package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.AccountType;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.AllocationRule;
import pe.suarez.finanzas.domain.MonthlyBudget;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.BudgetDtos.*;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.AllocationRuleRepository;
import pe.suarez.finanzas.repository.MonthlyBudgetRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Todos los montos en moneda base. El "gasto" por bucket combina:
 * <ul>
 *   <li>Gastos por categoría (incluye categorías de ahorro heredadas, previas a las cuentas).</li>
 *   <li>Transferencias hacia/desde cuentas SAVINGS o INVESTMENT: aportan (o restan, si es un
 *       retiro) al bucket correspondiente. Pagar una tarjeta o mover entre cuentas corrientes
 *       no cuenta: los gastos ya se registraron al comprar.</li>
 * </ul>
 */
@ApplicationScoped
public class DashboardService {

    private static final Set<AllocationBucket> SAVINGS_BUCKETS =
            EnumSet.of(AllocationBucket.SAVINGS, AllocationBucket.INVESTMENT);

    @Inject TransactionRepository txRepo;
    @Inject MonthlyBudgetRepository budgetRepo;
    @Inject AllocationRuleRepository ruleRepo;
    @Inject UserContext userContext;

    public DashboardResponse build(YearMonth ym) {
        Long uid = userContext.userId();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal income = txRepo.sumIncome(uid, from, to);
        Map<AllocationBucket, BigDecimal> spentByBucket = bucketTotals(uid, from, to);
        BigDecimal savings = sum(spentByBucket, true);
        BigDecimal expenses = sum(spentByBucket, false);
        BigDecimal balance = income.subtract(expenses).subtract(savings);
        BigDecimal savingsYtd = sum(bucketTotals(uid, LocalDate.of(ym.getYear(), 1, 1), to), true);

        MonthlyBudget budget = budgetRepo.findForPeriod(uid, ym).orElse(null);
        AllocationRule activeRule = resolveActiveRule(uid, budget);

        // Si hay budget configurado se usa expectedIncome; si no, fallback al ingreso real
        boolean budgetConfigured = budget != null;
        BigDecimal expectedIncome = budget != null ? budget.expectedIncome : income;

        List<BucketSummary> bucketSummaries = buildBucketSummaries(activeRule, expectedIncome, spentByBucket);

        List<CategoryTotal> topCategories = txRepo.sumConsumptionByCategory(uid, from, to).stream()
                .limit(5)
                .map(row -> new CategoryTotal((Long) row[0], (String) row[1], (BigDecimal) row[2]))
                .toList();

        User user = User.findById(uid);
        return new DashboardResponse(
                ym.getYear(), ym.getMonthValue(),
                user != null ? user.currencyDefault : "PEN",
                income, expenses, savings, balance, savingsYtd,
                expectedIncome, budgetConfigured,
                activeRule != null ? Mappers.toAllocationRuleResponse(activeRule) : null,
                bucketSummaries,
                topCategories
        );
    }

    /** Gastos por bucket + aportes netos de transferencias a cuentas de ahorro/inversión. */
    private Map<AllocationBucket, BigDecimal> bucketTotals(Long uid, LocalDate from, LocalDate to) {
        Map<AllocationBucket, BigDecimal> totals = new EnumMap<>(AllocationBucket.class);
        for (Object[] row : txRepo.sumExpensesByBucket(uid, from, to)) {
            AllocationBucket b = row[0] != null ? (AllocationBucket) row[0] : AllocationBucket.UNCATEGORIZED;
            totals.merge(b, (BigDecimal) row[1], BigDecimal::add);
        }
        for (Object[] row : txRepo.sumTransfersByAccountTypes(uid, from, to)) {
            AllocationBucket fromBucket = ((AccountType) row[0]).savingsBucket();
            AllocationBucket toBucket = ((AccountType) row[1]).savingsBucket();
            if (fromBucket == toBucket) continue;
            BigDecimal amount = (BigDecimal) row[2];
            if (toBucket != null) totals.merge(toBucket, amount, BigDecimal::add);
            if (fromBucket != null) totals.merge(fromBucket, amount.negate(), BigDecimal::add);
        }
        return totals;
    }

    private static BigDecimal sum(Map<AllocationBucket, BigDecimal> totals, boolean savingsBuckets) {
        return totals.entrySet().stream()
                .filter(e -> SAVINGS_BUCKETS.contains(e.getKey()) == savingsBuckets)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AllocationRule resolveActiveRule(Long uid, MonthlyBudget budget) {
        if (budget != null && budget.activeRuleId != null) {
            return ruleRepo.findByIdForUser(budget.activeRuleId, uid).orElse(null);
        }
        return ruleRepo.find("userId = ?1 AND template = true", uid).firstResult();
    }

    private List<BucketSummary> buildBucketSummaries(AllocationRule rule, BigDecimal expectedIncome,
                                                     Map<AllocationBucket, BigDecimal> spentByBucket) {
        List<BucketSummary> result = new ArrayList<>();
        if (rule == null) return result;

        for (var entry : rule.percentages.entrySet()) {
            AllocationBucket bucket;
            try {
                bucket = AllocationBucket.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            BigDecimal pct = BigDecimal.valueOf(entry.getValue());
            BigDecimal allocated = expectedIncome.multiply(pct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal spent = spentByBucket.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal pctUsed = allocated.signum() == 0
                    ? BigDecimal.ZERO
                    : spent.multiply(BigDecimal.valueOf(100))
                        .divide(allocated, 1, RoundingMode.HALF_UP);
            result.add(new BucketSummary(bucket, allocated, spent, pctUsed));
        }
        return result;
    }
}
