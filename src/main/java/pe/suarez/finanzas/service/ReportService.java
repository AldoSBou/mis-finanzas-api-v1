package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.dto.ReportDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.ExchangeRateResponse;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Reportes históricos. Todo en moneda base; se usa la misma lógica del panel
 * (gastos = consumo, ahorro = aportes netos a cuentas de ahorro/inversión).
 */
@ApplicationScoped
public class ReportService {

    /** Categorías con serie propia; el resto se agrupa en "Otros". */
    private static final int TOP_CATEGORIES = 5;

    @Inject TransactionRepository txRepo;
    @Inject AccountRepository accountRepo;
    @Inject DashboardService dashboardService;
    @Inject TransactionService txService;
    @Inject UserContext userContext;

    public ReportResponse build(YearMonth until, int monthCount) {
        Long uid = userContext.userId();
        String base = TransactionService.baseCurrency(uid);
        List<YearMonth> periods = new ArrayList<>();
        for (int i = monthCount - 1; i >= 0; i--) periods.add(until.minusMonths(i));

        // Tipo de cambio por moneda para el patrimonio (el último conocido)
        List<Account> accounts = accountRepo.listForUser(uid, true);
        Map<String, BigDecimal> rates = new HashMap<>();
        boolean approximate = false;
        for (Account a : accounts) {
            if (a.currency.equals(base) || rates.containsKey(a.currency)) continue;
            BigDecimal rate = txService.latestRate(a.currency)
                    .map(ExchangeRateResponse::rate).orElse(null);
            rates.put(a.currency, rate);
            approximate = true;
        }

        List<MonthSummary> months = new ArrayList<>();
        List<Map<Long, BigDecimal>> categoryByMonth = new ArrayList<>();
        Map<Long, String> categoryNames = new HashMap<>();
        for (YearMonth ym : periods) {
            var from = ym.atDay(1);
            var to = ym.atEndOfMonth();
            BigDecimal income = txRepo.sumIncome(uid, from, to);
            Map<AllocationBucket, BigDecimal> buckets = dashboardService.bucketTotals(uid, from, to);
            BigDecimal savings = DashboardService.sum(buckets, true);
            BigDecimal expenses = DashboardService.sum(buckets, false);
            months.add(new MonthSummary(ym.toString(), income, expenses, savings,
                    income.subtract(expenses).subtract(savings),
                    netWorth(uid, accounts, to, base, rates)));

            Map<Long, BigDecimal> byCategory = new HashMap<>();
            for (Object[] row : txRepo.sumConsumptionByCategory(uid, from, to)) {
                byCategory.put((Long) row[0], (BigDecimal) row[2]);
                categoryNames.put((Long) row[0], (String) row[1]);
            }
            categoryByMonth.add(byCategory);
        }

        return new ReportResponse(base, months, categorySeries(categoryByMonth, categoryNames), approximate);
    }

    /** Top N categorías por total del período y una serie "Otros" con el resto. */
    private static List<CategorySeries> categorySeries(List<Map<Long, BigDecimal>> byMonth,
                                                       Map<Long, String> names) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        byMonth.forEach(m -> m.forEach((id, v) -> totals.merge(id, v, BigDecimal::add)));
        List<Long> top = totals.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_CATEGORIES)
                .map(Map.Entry::getKey)
                .toList();

        List<CategorySeries> series = new ArrayList<>();
        for (Long id : top) {
            List<BigDecimal> monthly = byMonth.stream()
                    .map(m -> m.getOrDefault(id, BigDecimal.ZERO)).toList();
            series.add(new CategorySeries(id, names.get(id), totals.get(id), monthly));
        }
        List<BigDecimal> others = byMonth.stream()
                .map(m -> m.entrySet().stream()
                        .filter(e -> !top.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .toList();
        BigDecimal othersTotal = others.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (othersTotal.signum() > 0) {
            series.add(new CategorySeries(null, "Otros", othersTotal, others));
        }
        return series;
    }

    /** Suma de saldos al cierre de {@code date}, convertida a moneda base. */
    private BigDecimal netWorth(Long uid, List<Account> accounts, LocalDate date,
                                String base, Map<String, BigDecimal> rates) {
        Map<Long, BigDecimal> movements = accountRepo.movementTotals(uid, date);
        BigDecimal total = BigDecimal.ZERO;
        for (Account a : accounts) {
            // Una cuenta creada después de esa fecha aún no existía
            if (a.createdAt.isAfter(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                    && !movements.containsKey(a.id)) {
                continue;
            }
            BigDecimal balance = a.initialBalance.add(movements.getOrDefault(a.id, BigDecimal.ZERO));
            if (a.currency.equals(base)) {
                total = total.add(balance);
            } else {
                BigDecimal rate = rates.get(a.currency);
                if (rate != null) total = total.add(balance.multiply(rate));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
