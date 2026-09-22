package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.CategoryBudget;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.CategoryBudgetDtos.*;
import pe.suarez.finanzas.dto.RecurringDtos.UpcomingItem;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CategoryBudgetService {

    private static final BigDecimal WARNING_PCT = BigDecimal.valueOf(80);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Inject CategoryRepository catRepo;
    @Inject TransactionRepository txRepo;
    @Inject RecurringService recurringService;
    @Inject UserContext userContext;

    /** Todas las categorías de gasto activas, con límite (si tiene) y lo gastado en el mes. */
    public CategoryBudgetSummary summary(YearMonth ym) {
        Long uid = userContext.userId();
        List<Category> categories = catRepo.listByType(uid, TransactionType.EXPENSE);
        Map<Long, BigDecimal> limits = CategoryBudget.<CategoryBudget>list("userId", uid).stream()
                .collect(Collectors.toMap(b -> b.categoryId, b -> b.amount));
        Map<Long, BigDecimal> spent = txRepo.sumExpensesByCategory(uid, ym.atDay(1), ym.atEndOfMonth());
        Map<Long, BigDecimal> scheduled = scheduledByCategory(uid, ym);

        List<CategoryBudgetItem> items = new ArrayList<>();
        BigDecimal totalLimit = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (Category c : categories) {
            BigDecimal limit = limits.get(c.id);
            BigDecimal s = spent.getOrDefault(c.id, BigDecimal.ZERO);
            BigDecimal sch = scheduled.getOrDefault(c.id, BigDecimal.ZERO);
            items.add(item(c, limit, s, sch));
            if (limit != null) {
                totalLimit = totalLimit.add(limit);
                totalSpent = totalSpent.add(s);
            }
        }
        // Con límite primero (los más consumidos arriba), luego el resto por nombre
        items.sort(Comparator
                .comparing((CategoryBudgetItem i) -> i.limit() == null)
                .thenComparing(i -> i.percentage() != null ? i.percentage().negate() : BigDecimal.ZERO)
                .thenComparing(CategoryBudgetItem::categoryName));

        return new CategoryBudgetSummary(ym.getYear(), ym.getMonthValue(),
                TransactionService.baseCurrency(uid), totalLimit, totalSpent, items);
    }

    /** Categorías con límite que ya pasaron el 80% o que se pasarán con lo programado. */
    public List<CategoryBudgetItem> alerts(YearMonth ym) {
        return summary(ym).items().stream()
                .filter(i -> i.status() == BudgetStatus.WARNING || i.status() == BudgetStatus.OVER || i.willExceed())
                .toList();
    }

    @Transactional
    public void upsert(Long categoryId, CategoryBudgetRequest req) {
        Long uid = userContext.userId();
        Category c = catRepo.findByIdForUser(categoryId, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        if (c.type != TransactionType.EXPENSE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Solo las categorías de gasto tienen presupuesto");
        }
        CategoryBudget b = CategoryBudget.<CategoryBudget>find("userId = ?1 AND categoryId = ?2", uid, categoryId)
                .firstResultOptional()
                .orElseGet(() -> {
                    CategoryBudget nb = new CategoryBudget();
                    nb.userId = uid;
                    nb.categoryId = categoryId;
                    return nb;
                });
        b.amount = req.amount();
        if (b.id == null) b.persist();
    }

    @Transactional
    public void delete(Long categoryId) {
        CategoryBudget.delete("userId = ?1 AND categoryId = ?2", userContext.userId(), categoryId);
    }

    // ---------------------------------------------------------------

    private static CategoryBudgetItem item(Category c, BigDecimal limit, BigDecimal spent, BigDecimal scheduled) {
        if (limit == null) {
            return new CategoryBudgetItem(c.id, c.name, c.color, c.defaultBucket,
                    null, spent, scheduled, null, BudgetStatus.NONE, false);
        }
        BigDecimal pct = spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);
        BudgetStatus status = pct.compareTo(HUNDRED) > 0 ? BudgetStatus.OVER
                : pct.compareTo(WARNING_PCT) >= 0 ? BudgetStatus.WARNING
                : BudgetStatus.OK;
        boolean willExceed = spent.add(scheduled).compareTo(limit) > 0;
        return new CategoryBudgetItem(c.id, c.name, c.color, c.defaultBucket,
                limit, spent, scheduled, pct, status, willExceed);
    }

    /** Gastos recurrentes que faltan en el mes actual, por categoría (vacío en otros meses). */
    private Map<Long, BigDecimal> scheduledByCategory(Long uid, YearMonth ym) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (!ym.equals(YearMonth.now())) return result;
        for (UpcomingItem u : recurringService.upcoming(uid, ym.atEndOfMonth())) {
            if (u.type() == TransactionType.EXPENSE && u.categoryId() != null) {
                result.merge(u.categoryId(), u.amountBase(), BigDecimal::add);
            }
        }
        return result;
    }
}
