package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.*;
import pe.suarez.finanzas.dto.OverviewDtos.*;
import pe.suarez.finanzas.dto.RecurringDtos.UpcomingItem;
import pe.suarez.finanzas.repository.AllocationRuleRepository;
import pe.suarez.finanzas.repository.MonthlyBudgetRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * "¿Cuánto me queda?": la recompensa por registrar. Un solo número para el periodo en curso.
 *
 * <pre>
 * te quedan = ingreso esperado
 *           − gastado
 *           − ahorro hecho − ahorro pendiente
 *           − compromisos pendientes − gastos recurrentes por venir
 *           − compromisos ya pagados con "mover dinero" (los pagados como gasto ya están en gastado)
 * </pre>
 *
 * Lo comprado con tarjeta ya cuenta como gasto al registrarlo; el pago mensual de la tarjeta
 * no se resta otra vez. La deuda con la que llega una tarjeta sí se declara como compromiso.
 */
@ApplicationScoped
public class OverviewService {

    @Inject TransactionRepository txRepo;
    @Inject MonthlyBudgetRepository budgetRepo;
    @Inject AllocationRuleRepository ruleRepo;
    @Inject RecurringService recurringService;
    @Inject CommitmentService commitmentService;
    @Inject CategoryBudgetService categoryBudgetService;
    @Inject DashboardService dashboardService;
    @Inject UserContext userContext;

    public OverviewResponse build() {
        Long uid = userContext.userId();
        User user = User.findById(uid);
        LocalDate today = LocalDate.now();
        LocalDate[] period = periodFor(today, user.periodStartDay);
        LocalDate from = period[0];
        LocalDate to = period[1];
        int daysLeft = (int) ChronoUnit.DAYS.between(today, to) + 1;

        // Lo que ya pasó en el periodo
        BigDecimal income = txRepo.sumIncome(uid, from, to);
        Map<AllocationBucket, BigDecimal> byBucket = dashboardService.bucketTotals(uid, from, to);
        BigDecimal spent = DashboardService.sum(byBucket, false);
        BigDecimal savingsDone = DashboardService.sum(byBucket, true);

        // Lo que falta: recurrentes por venir
        BigDecimal incomePending = BigDecimal.ZERO;
        BigDecimal expensesPending = BigDecimal.ZERO;
        BigDecimal savingsRecurring = BigDecimal.ZERO;
        List<PendingItem> pending = new ArrayList<>();
        var transferBuckets = recurringService.transferBuckets(uid);
        for (UpcomingItem u : recurringService.upcoming(uid, to)) {
            switch (u.type()) {
                case INCOME -> incomePending = incomePending.add(u.amountBase());
                case EXPENSE -> {
                    expensesPending = expensesPending.add(u.amountBase());
                    pending.add(new PendingItem("RECURRING", u.recurringId(),
                            u.description() != null ? u.description() : u.categoryName(),
                            u.categoryName(), u.amountBase(), u.date(), u.overdue(), u.autoCreate(), false));
                }
                case TRANSFER -> {
                    BigDecimal effect = recurringService.effectOnAvailable(u, transferBuckets);
                    if (effect.signum() < 0) {
                        savingsRecurring = savingsRecurring.add(effect.negate());
                        pending.add(new PendingItem("RECURRING", u.recurringId(),
                                u.description() != null ? u.description() : "Ahorro → " + u.toAccountName(),
                                null, u.amountBase(), u.date(), u.overdue(), u.autoCreate(), true));
                    }
                }
            }
        }

        // Compromisos
        BigDecimal commitmentsPending = BigDecimal.ZERO;
        List<Commitment> pendingCommitments = commitmentService.pending(uid);
        Map<Long, Category> categories = commitmentService.categoriesFor(pendingCommitments, uid);
        for (Commitment c : pendingCommitments) {
            if (c.dueDate.isAfter(to)) continue; // lo de otros periodos no entra todavía
            commitmentsPending = commitmentsPending.add(c.amount);
            Category cat = c.categoryId != null ? categories.get(c.categoryId) : null;
            pending.add(new PendingItem("COMMITMENT", c.id, c.name, cat != null ? cat.name : null,
                    c.amount, c.dueDate, c.dueDate.isBefore(today), false, c.toAccountId != null));
        }
        pending.sort(Comparator.comparing(PendingItem::date));

        BigDecimal commitmentsPaid = BigDecimal.ZERO;
        for (Commitment c : commitmentService.paidBetween(uid, from, to)) {
            if (c.toAccountId != null || c.paidTransactionId == null) commitmentsPaid = commitmentsPaid.add(c.amount);
        }

        // Ingreso esperado y meta de ahorro de la regla activa (presupuesto del mes en que termina el periodo)
        YearMonth ym = YearMonth.from(to);
        MonthlyBudget budget = budgetRepo.findForPeriod(uid, ym).orElse(null);
        BigDecimal expectedIncome = income.add(incomePending);
        if (budget != null && budget.expectedIncome.compareTo(expectedIncome) > 0) expectedIncome = budget.expectedIncome;

        BigDecimal savingsTarget = savingsTarget(uid, budget, expectedIncome);
        BigDecimal savingsPending = savingsRecurring.max(savingsTarget.subtract(savingsDone).max(BigDecimal.ZERO));

        BigDecimal pendingTotal = expensesPending.add(commitmentsPending);
        BigDecimal remaining = expectedIncome.subtract(spent).subtract(savingsDone).subtract(savingsPending)
                .subtract(pendingTotal).subtract(commitmentsPaid);
        BigDecimal perDay = remaining.signum() > 0
                ? remaining.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.DOWN) : null;

        return new OverviewResponse(today, from, to, daysLeft, user.periodStartDay, user.currencyDefault,
                income, expectedIncome, spent, savingsDone, savingsPending, pendingTotal, commitmentsPaid,
                remaining, perDay, pending, categoryBudgetService.summary(from, to).items());
    }

    /** Mes calendario, o del día de pago al día anterior del mes siguiente. */
    public static LocalDate[] periodFor(LocalDate today, Integer startDay) {
        if (startDay == null) {
            YearMonth ym = YearMonth.from(today);
            return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
        }
        LocalDate start = today.getDayOfMonth() >= startDay
                ? today.withDayOfMonth(startDay)
                : today.minusMonths(1).withDayOfMonth(startDay);
        return new LocalDate[]{start, start.plusMonths(1).minusDays(1)};
    }

    /** Ahorro + inversión que pide la regla activa sobre el ingreso esperado (0 si no hay regla). */
    private BigDecimal savingsTarget(Long uid, MonthlyBudget budget, BigDecimal expectedIncome) {
        AllocationRule rule = null;
        if (budget != null && budget.activeRuleId != null) {
            rule = ruleRepo.findByIdForUser(budget.activeRuleId, uid).orElse(null);
        }
        if (rule == null) return BigDecimal.ZERO;
        int pct = rule.percentages.getOrDefault("SAVINGS", 0) + rule.percentages.getOrDefault("INVESTMENT", 0);
        return expectedIncome.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
