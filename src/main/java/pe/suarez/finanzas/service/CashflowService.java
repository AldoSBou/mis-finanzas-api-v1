package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.CashflowDtos.*;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Flujo de caja mensual: la grilla tipo hoja de cálculo con ingresos, gastos y ahorro por
 * categoría y por mes. Solo lo registrado; las proyecciones viven en "te quedan".
 */
@ApplicationScoped
public class CashflowService {

    public static final int MAX_MONTHS = 6;

    @Inject TransactionRepository txRepo;
    @Inject AccountRepository accountRepo;
    @Inject UserContext userContext;

    public CashflowResponse build(YearMonth until, int months) {
        Long uid = userContext.userId();
        int n = Math.max(1, Math.min(months, MAX_MONTHS));
        List<YearMonth> yms = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) yms.add(until.minusMonths(i));

        // Por mes: ingresos y gastos por categoría, ahorro neto por cuenta
        List<Map<Long, BigDecimal>> incomeByMonth = new ArrayList<>();
        List<Map<Long, BigDecimal>> expensesByMonth = new ArrayList<>();
        List<Map<Long, BigDecimal>> savingsByMonth = new ArrayList<>();
        for (YearMonth ym : yms) {
            incomeByMonth.add(txRepo.sumByCategory(uid, TransactionType.INCOME, ym.atDay(1), ym.atEndOfMonth()));
            expensesByMonth.add(txRepo.sumByCategory(uid, TransactionType.EXPENSE, ym.atDay(1), ym.atEndOfMonth()));
            savingsByMonth.add(txRepo.sumSavingsByAccount(uid, ym.atDay(1), ym.atEndOfMonth()));
        }

        Map<Long, Category> categories = Category.<Category>list("userId", uid).stream()
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        Map<Long, Account> accounts = accountRepo.list("userId", uid).stream()
                .collect(Collectors.toMap(a -> a.id, Function.identity()));

        List<CashflowRow> income = rows(incomeByMonth, id -> name(categories.get(id), "Sin categoría"),
                id -> color(categories.get(id)));
        List<CashflowRow> expenses = rows(expensesByMonth, id -> name(categories.get(id), "Sin categoría"),
                id -> color(categories.get(id)));
        List<CashflowRow> savings = rows(savingsByMonth,
                id -> accounts.get(id) != null ? accounts.get(id).name : "Cuenta",
                id -> accounts.get(id) != null ? accounts.get(id).color : null);

        List<BigDecimal> totalIncome = columnTotals(income, n);
        List<BigDecimal> totalExpenses = columnTotals(expenses, n);
        List<BigDecimal> totalSavings = columnTotals(savings, n);
        List<BigDecimal> net = new ArrayList<>();
        List<BigDecimal> cumulative = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal m = totalIncome.get(i).subtract(totalExpenses.get(i)).subtract(totalSavings.get(i));
            net.add(m);
            running = running.add(m);
            cumulative.add(running);
        }

        return new CashflowResponse(TransactionService.baseCurrency(uid),
                yms.stream().map(YearMonth::toString).toList(),
                income, expenses, savings, totalIncome, totalExpenses, totalSavings, net, cumulative);
    }

    /** Filas por id presente en cualquier mes, con ceros donde no hubo nada; de mayor a menor total. */
    private static List<CashflowRow> rows(List<Map<Long, BigDecimal>> byMonth,
                                          Function<Long, String> nameOf, Function<Long, String> colorOf) {
        Set<Long> ids = new LinkedHashSet<>();
        byMonth.forEach(m -> ids.addAll(m.keySet()));
        List<CashflowRow> result = new ArrayList<>();
        for (Long id : ids) {
            List<BigDecimal> monthly = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map<Long, BigDecimal> m : byMonth) {
                BigDecimal v = m.getOrDefault(id, BigDecimal.ZERO);
                monthly.add(v);
                total = total.add(v);
            }
            result.add(new CashflowRow(id, nameOf.apply(id), colorOf.apply(id), monthly, total));
        }
        result.sort(Comparator.comparing((CashflowRow r) -> r.total().abs()).reversed()
                .thenComparing(CashflowRow::name));
        return result;
    }

    private static List<BigDecimal> columnTotals(List<CashflowRow> rows, int n) {
        List<BigDecimal> totals = new ArrayList<>(Collections.nCopies(n, BigDecimal.ZERO));
        for (CashflowRow r : rows) {
            for (int i = 0; i < n; i++) totals.set(i, totals.get(i).add(r.monthly().get(i)));
        }
        return totals;
    }

    private static String name(Category c, String fallback) {
        return c != null ? c.name : fallback;
    }

    private static String color(Category c) {
        return c != null ? c.color : null;
    }
}
