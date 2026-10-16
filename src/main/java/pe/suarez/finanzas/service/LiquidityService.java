package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.suarez.finanzas.domain.*;
import pe.suarez.finanzas.dto.CardDtos.CardSummary;
import pe.suarez.finanzas.dto.LiquidityDtos.*;
import pe.suarez.finanzas.dto.RecurringDtos.UpcomingItem;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "¿Me alcanza la plata de las cuentas hasta que caiga el sueldo?": la vista de liquidez.
 *
 * A diferencia de "te quedan" (vista de presupuesto, donde lo comprado con tarjeta ya se
 * descontó), aquí cuenta el efectivo: las cuentas líquidas (general, efectivo, banco,
 * billetera) y lo que entra y sale de ellas en cada fecha. El pago de la tarjeta sale el
 * día de vencimiento, no al comprar. Las compras del ciclo que aún no tiene estado de
 * cuenta no se proyectan.
 */
@ApplicationScoped
public class LiquidityService {

    private static final Set<AccountType> LIQUID = EnumSet.of(
            AccountType.GENERAL, AccountType.CASH, AccountType.BANK, AccountType.EWALLET);
    private static final int VARIABLE_WINDOW_DAYS = 28;

    @Inject AccountRepository accountRepo;
    @Inject RecurringService recurringService;
    @Inject CommitmentService commitmentService;
    @Inject CardService cardService;
    @Inject TransactionService txService;
    @Inject EntityManager em;
    @Inject UserContext userContext;

    public LiquidityResponse build(BigDecimal dailyOverride) {
        Long uid = userContext.userId();
        User user = User.findById(uid);
        String base = user.currencyDefault;
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = OverviewService.periodFor(today, user.periodStartDay)[1];
        LocalDate horizonEnd = OverviewService.periodFor(periodEnd.plusDays(1), user.periodStartDay)[1];

        // Saldo líquido de hoy
        List<Account> all = accountRepo.listForUser(uid, false);
        Map<Long, Account> byId = all.stream().collect(Collectors.toMap(a -> a.id, Function.identity()));
        Map<Long, BigDecimal> movements = accountRepo.movementTotals(uid);
        List<LiquidAccount> liquid = new ArrayList<>();
        BigDecimal opening = BigDecimal.ZERO;
        for (Account a : all) {
            if (!LIQUID.contains(a.type)) continue;
            BigDecimal balance = a.initialBalance.add(movements.getOrDefault(a.id, BigDecimal.ZERO));
            BigDecimal rate = a.currency.equals(base) ? BigDecimal.ONE
                    : txService.latestRate(a.currency).map(r -> r.rate()).orElse(null);
            BigDecimal inBase = rate != null ? balance.multiply(rate).setScale(2, RoundingMode.HALF_UP) : null;
            if (inBase != null) opening = opening.add(inBase);
            liquid.add(new LiquidAccount(a.id, a.name, a.currency, balance, inBase, rate));
        }

        // Entradas y salidas previstas
        List<LiquidityEvent> events = new ArrayList<>();
        for (UpcomingItem u : recurringService.upcoming(uid, horizonEnd)) {
            Account from = u.accountId() != null ? byId.get(u.accountId()) : null;
            Account to = u.toAccountId() != null ? byId.get(u.toAccountId()) : null;
            boolean fromLiquid = from != null && LIQUID.contains(from.type);
            boolean toLiquid = to != null && LIQUID.contains(to.type);
            BigDecimal effect = switch (u.type()) {
                case INCOME -> fromLiquid ? u.amountBase() : BigDecimal.ZERO;
                case EXPENSE -> fromLiquid ? u.amountBase().negate() : BigDecimal.ZERO;
                case TRANSFER -> fromLiquid == toLiquid ? BigDecimal.ZERO
                        : fromLiquid ? u.amountBase().negate() : u.amountBase();
            };
            if (effect.signum() == 0) continue;
            LocalDate date = u.date().isBefore(today) ? today : u.date();
            String name = u.description() != null ? u.description()
                    : u.categoryName() != null ? u.categoryName()
                    : u.type() == TransactionType.TRANSFER ? (to != null ? "→ " + to.name : "Transferencia") : "Recurrente";
            events.add(new LiquidityEvent(date, "RECURRING", u.recurringId(), name, effect));
        }
        List<Commitment> pending = commitmentService.pending(uid);
        Set<Long> commitmentCards = new HashSet<>();
        for (Commitment c : pending) {
            if (c.dueDate.isAfter(horizonEnd)) continue;
            LocalDate date = c.dueDate.isBefore(today) ? today : c.dueDate;
            events.add(new LiquidityEvent(date, "COMMITMENT", c.id, c.name, c.amount.negate()));
            if (c.toAccountId != null) commitmentCards.add(c.toAccountId);
        }
        for (CardSummary card : cardService.summaries()) {
            var s = card.latestStatement();
            if (s == null || s.remaining() == null || s.remaining().signum() <= 0) continue;
            if (commitmentCards.contains(card.accountId())) continue; // ya está como compromiso
            LocalDate due = s.dueDate() != null ? s.dueDate() : card.nextDueDate();
            if (due == null || due.isAfter(horizonEnd)) continue;
            BigDecimal rate = card.currency().equals(base) ? BigDecimal.ONE
                    : txService.latestRate(card.currency()).map(r -> r.rate()).orElse(BigDecimal.ONE);
            BigDecimal amount = s.remaining().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            events.add(new LiquidityEvent(due.isBefore(today) ? today : due, "CARD", card.accountId(),
                    "Pago " + card.name(), amount.negate()));
        }
        events.sort(Comparator.comparing(LiquidityEvent::date));

        // Gasto variable diario: lo de los últimos 28 días que no fue recurrente, compromiso ni tarjeta
        BigDecimal estimated = variableDaily(uid, today);
        BigDecimal daily = dailyOverride != null && dailyOverride.signum() >= 0 ? dailyOverride : estimated;

        // Proyección día a día
        List<LiquidityDay> days = new ArrayList<>();
        LiquidityDay lowest = null;
        LocalDate firstNegative = null;
        BigDecimal running = opening;
        int i = 0;
        int ev = 0;
        for (LocalDate d = today; !d.isAfter(horizonEnd); d = d.plusDays(1), i++) {
            while (ev < events.size() && !events.get(ev).date().isAfter(d)) {
                running = running.add(events.get(ev).amount());
                ev++;
            }
            BigDecimal balance = running.subtract(daily.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP);
            LiquidityDay day = new LiquidityDay(d, balance);
            days.add(day);
            if (lowest == null || balance.compareTo(lowest.balance()) < 0) lowest = day;
            if (firstNegative == null && balance.signum() < 0) firstNegative = d;
        }

        return new LiquidityResponse(today, periodEnd, horizonEnd, base, liquid, opening,
                estimated, daily, events, days, lowest, firstNegative, days.get(days.size() - 1).balance());
    }

    /** Promedio diario de gastos de cuentas líquidas en los últimos 28 días, sin recurrentes ni compromisos pagados. */
    private BigDecimal variableDaily(Long uid, LocalDate today) {
        LocalDate from = today.minusDays(VARIABLE_WINDOW_DAYS - 1);
        BigDecimal total = em.createQuery("""
                        SELECT COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t, Account a
                        WHERE t.accountId = a.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND t.recurringId IS NULL
                          AND a.type IN (:liquid)
                          AND t.transactionDate BETWEEN :from AND :to
                          AND t.id NOT IN (SELECT c.paidTransactionId FROM Commitment c WHERE c.userId = :uid AND c.paidTransactionId IS NOT NULL)
                        """, BigDecimal.class)
                .setParameter("uid", uid)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("liquid", LIQUID)
                .setParameter("from", from)
                .setParameter("to", today)
                .getSingleResult();
        return (total != null ? total : BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(VARIABLE_WINDOW_DAYS), 2, RoundingMode.HALF_UP);
    }
}
