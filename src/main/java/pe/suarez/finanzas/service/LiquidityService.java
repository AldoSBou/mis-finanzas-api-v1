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
 *
 * Cada moneda se proyecta por separado y nada se convierte: los soles responden si alcanzan
 * los soles y los dólares si alcanzan los dólares. Una tarjeta en dólares pesa en la
 * proyección en dólares aunque no haya cuenta en esa moneda (así se ve lo que habrá que
 * conseguir). El tipo de cambio solo interviene donde el dinero cruza de moneda: una
 * transferencia recurrente entre cuentas de distinta moneda usa sus propios montos.
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
    @Inject EntityManager em;
    @Inject UserContext userContext;

    public LiquidityResponse build(BigDecimal dailyOverride) {
        Long uid = userContext.userId();
        User user = User.findById(uid);
        String base = user.currencyDefault;
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = OverviewService.periodFor(today, user.periodStartDay)[1];
        LocalDate horizonEnd = OverviewService.periodFor(periodEnd.plusDays(1), user.periodStartDay)[1];

        // Saldo líquido de hoy, por moneda
        List<Account> all = accountRepo.listForUser(uid, false);
        Map<Long, Account> byId = all.stream().collect(Collectors.toMap(a -> a.id, Function.identity()));
        Map<Long, BigDecimal> movements = accountRepo.movementTotals(uid);
        Map<String, List<LiquidAccount>> accounts = new HashMap<>();
        for (Account a : all) {
            if (!LIQUID.contains(a.type)) continue;
            BigDecimal balance = a.initialBalance.add(movements.getOrDefault(a.id, BigDecimal.ZERO));
            accounts.computeIfAbsent(a.currency, k -> new ArrayList<>())
                    .add(new LiquidAccount(a.id, a.name, a.currency, balance));
        }

        // Entradas y salidas previstas, cada una en la moneda de la cuenta que toca
        Map<String, List<LiquidityEvent>> events = new HashMap<>();
        for (UpcomingItem u : recurringService.upcoming(uid, horizonEnd)) {
            Account from = u.accountId() != null ? byId.get(u.accountId()) : null;
            Account to = u.toAccountId() != null ? byId.get(u.toAccountId()) : null;
            boolean fromLiquid = from != null && LIQUID.contains(from.type);
            boolean toLiquid = to != null && LIQUID.contains(to.type);
            LocalDate date = u.date().isBefore(today) ? today : u.date();
            String name = u.description() != null ? u.description()
                    : u.categoryName() != null ? u.categoryName()
                    : u.type() == TransactionType.TRANSFER ? (to != null ? "→ " + to.name : "Transferencia") : "Recurrente";
            switch (u.type()) {
                case INCOME -> {
                    if (fromLiquid) add(events, from.currency, new LiquidityEvent(date, "RECURRING", u.recurringId(), name, u.amount()));
                }
                case EXPENSE -> {
                    if (fromLiquid) add(events, from.currency, new LiquidityEvent(date, "RECURRING", u.recurringId(), name, u.amount().negate()));
                }
                case TRANSFER -> {
                    // Entre dos cuentas líquidas de la misma moneda el efectivo no cambia
                    if (fromLiquid && toLiquid && from.currency.equals(to.currency)) continue;
                    if (fromLiquid) add(events, from.currency, new LiquidityEvent(date, "RECURRING", u.recurringId(), name, u.amount().negate()));
                    if (toLiquid && u.toAmount() != null) {
                        add(events, to.currency, new LiquidityEvent(date, "RECURRING", u.recurringId(),
                                from != null ? from.name + " →" : name, u.toAmount()));
                    }
                }
            }
        }
        Set<Long> commitmentCards = new HashSet<>();
        for (Commitment c : commitmentService.pending(uid)) {
            if (c.dueDate.isAfter(horizonEnd)) continue;
            if (c.toAccountId != null) commitmentCards.add(c.toAccountId);
            Account from = c.accountId != null ? byId.get(c.accountId) : null;
            if (from != null && !LIQUID.contains(from.type)) continue; // se paga desde ahorro: no toca el efectivo
            String currency = from != null ? from.currency : base;
            LocalDate date = c.dueDate.isBefore(today) ? today : c.dueDate;
            add(events, currency, new LiquidityEvent(date, "COMMITMENT", c.id, c.name, c.amount.negate()));
        }
        for (CardSummary card : cardService.summaries()) {
            var s = card.latestStatement();
            if (s == null || s.remaining() == null || s.remaining().signum() <= 0) continue;
            if (commitmentCards.contains(card.accountId())) continue; // ya está como compromiso
            LocalDate due = s.dueDate() != null ? s.dueDate() : card.nextDueDate();
            if (due == null || due.isAfter(horizonEnd)) continue;
            add(events, card.currency(), new LiquidityEvent(due.isBefore(today) ? today : due, "CARD",
                    card.accountId(), "Pago " + card.name(), s.remaining().negate()));
        }

        // Gasto variable diario por moneda: lo de los últimos 28 días que no fue recurrente, compromiso ni tarjeta
        Map<String, BigDecimal> estimated = variableDaily(uid, today);

        // Una proyección por moneda: la base primero, luego las demás en orden
        List<String> currencies = new ArrayList<>();
        currencies.add(base);
        new TreeSet<>(accounts.keySet()).forEach(c -> { if (!currencies.contains(c)) currencies.add(c); });
        new TreeSet<>(events.keySet()).forEach(c -> { if (!currencies.contains(c)) currencies.add(c); });

        List<CurrencyProjection> projections = new ArrayList<>();
        for (String currency : currencies) {
            List<LiquidAccount> accs = accounts.getOrDefault(currency, List.of());
            List<LiquidityEvent> evs = new ArrayList<>(events.getOrDefault(currency, List.of()));
            evs.sort(Comparator.comparing(LiquidityEvent::date));
            BigDecimal opening = accs.stream().map(LiquidAccount::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal est = estimated.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal daily = currency.equals(base) && dailyOverride != null && dailyOverride.signum() >= 0
                    ? dailyOverride : est;
            projections.add(project(currency, accs, opening, est, daily, evs, today, horizonEnd));
        }
        return new LiquidityResponse(today, periodEnd, horizonEnd, base, projections);
    }

    private static void add(Map<String, List<LiquidityEvent>> events, String currency, LiquidityEvent e) {
        if (e.amount().signum() == 0) return;
        events.computeIfAbsent(currency, k -> new ArrayList<>()).add(e);
    }

    /** Proyección día a día de una moneda. */
    private static CurrencyProjection project(String currency, List<LiquidAccount> accs, BigDecimal opening,
                                              BigDecimal estimated, BigDecimal daily, List<LiquidityEvent> events,
                                              LocalDate today, LocalDate horizonEnd) {
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
        return new CurrencyProjection(currency, accs, opening, estimated, daily, events, days, lowest,
                firstNegative, days.get(days.size() - 1).balance());
    }

    /**
     * Promedio diario de gastos de cuentas líquidas en los últimos 28 días, por moneda de la
     * cuenta (en esa moneda, sin convertir), sin recurrentes ni compromisos pagados.
     */
    private Map<String, BigDecimal> variableDaily(Long uid, LocalDate today) {
        LocalDate from = today.minusDays(VARIABLE_WINDOW_DAYS - 1);
        List<Object[]> rows = em.createQuery("""
                        SELECT a.currency, COALESCE(SUM(t.amount), 0)
                        FROM Transaction t, Account a
                        WHERE t.accountId = a.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND t.recurringId IS NULL
                          AND a.type IN (:liquid)
                          AND t.transactionDate BETWEEN :from AND :to
                          AND t.id NOT IN (SELECT c.paidTransactionId FROM Commitment c WHERE c.userId = :uid AND c.paidTransactionId IS NOT NULL)
                        GROUP BY a.currency
                        """, Object[].class)
                .setParameter("uid", uid)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("liquid", LIQUID)
                .setParameter("from", from)
                .setParameter("to", today)
                .getResultList();
        Map<String, BigDecimal> result = new HashMap<>();
        for (Object[] r : rows) {
            BigDecimal total = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
            result.put((String) r[0], total.divide(BigDecimal.valueOf(VARIABLE_WINDOW_DAYS), 2, RoundingMode.HALF_UP));
        }
        return result;
    }
}
