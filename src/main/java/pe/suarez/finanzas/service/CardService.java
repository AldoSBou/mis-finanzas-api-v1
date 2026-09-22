package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.*;
import pe.suarez.finanzas.dto.CardDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Tarjetas de crédito: disponible, estados de cuenta con seguimiento del pago y compras en
 * cuotas. El "pago del mes" no se calcula (depende de cuotas y del sistema revolvente de cada
 * banco): se registra el que informa el banco y se descuentan los pagos hechos tras el cierre.
 */
@ApplicationScoped
public class CardService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Inject AccountRepository accountRepo;
    @Inject EntityManager em;
    @Inject UserContext userContext;

    public List<CardSummary> summaries() {
        Long uid = userContext.userId();
        Map<Long, BigDecimal> movements = accountRepo.movementTotals(uid);
        return accountRepo.listForUser(uid, false).stream()
                .filter(a -> a.type == AccountType.CREDIT_CARD)
                .map(a -> summary(a, movements, uid))
                .toList();
    }

    public CardDetail detail(Long accountId) {
        Long uid = userContext.userId();
        Account card = card(accountId, uid);
        List<StatementResponse> statements = CardStatement.<CardStatement>list(
                        "accountId = ?1 ORDER BY closingDate DESC", card.id).stream()
                .map(s -> statementResponse(s, card.id))
                .toList();
        return new CardDetail(summary(card, accountRepo.movementTotals(uid), uid), statements,
                installments(card, uid));
    }

    /** Registra o actualiza (por fecha de cierre) el estado de cuenta de un ciclo. */
    @Transactional
    public StatementResponse saveStatement(Long accountId, StatementRequest req) {
        Long uid = userContext.userId();
        Account card = card(accountId, uid);
        if (req.dueDate().isBefore(req.closingDate())) {
            throw new ApiException(ErrorCode.CARD_INVALID, "El vencimiento no puede ser antes del cierre");
        }
        CardStatement s = CardStatement.<CardStatement>find("accountId = ?1 AND closingDate = ?2", card.id, req.closingDate())
                .firstResultOptional()
                .orElseGet(() -> {
                    CardStatement ns = new CardStatement();
                    ns.userId = uid;
                    ns.accountId = card.id;
                    ns.closingDate = req.closingDate();
                    return ns;
                });
        s.dueDate = req.dueDate();
        s.totalDue = req.totalDue();
        s.minimumDue = req.minimumDue();
        if (s.id == null) s.persist();
        return statementResponse(s, card.id);
    }

    @Transactional
    public void deleteStatement(Long accountId, Long statementId) {
        Long uid = userContext.userId();
        Account card = card(accountId, uid);
        CardStatement.delete("id = ?1 AND accountId = ?2", statementId, card.id);
    }

    /** Marca una compra de tarjeta como compra en cuotas (o cambia su plan). */
    @Transactional
    public InstallmentResponse setInstallments(Long transactionId, InstallmentRequest req) {
        Long uid = userContext.userId();
        Transaction t = Transaction.<Transaction>find("id = ?1 AND userId = ?2", transactionId, uid)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        Account card = accountRepo.findByIdForUser(t.accountId, uid).orElseThrow();
        if (t.type != TransactionType.EXPENSE || card.type != AccountType.CREDIT_CARD) {
            throw new ApiException(ErrorCode.CARD_INVALID, "Solo las compras con tarjeta de crédito se pagan en cuotas");
        }
        InstallmentPlan p = InstallmentPlan.<InstallmentPlan>find("transactionId", t.id)
                .firstResultOptional()
                .orElseGet(() -> {
                    InstallmentPlan np = new InstallmentPlan();
                    np.userId = uid;
                    np.transactionId = t.id;
                    return np;
                });
        if (req.installmentAmount() != null && req.installmentAmount()
                .multiply(BigDecimal.valueOf(req.installments()))
                .compareTo(t.amount.subtract(BigDecimal.valueOf(req.installments(), 2))) < 0) {
            throw new ApiException(ErrorCode.CARD_INVALID, "Las cuotas no alcanzan a cubrir el monto de la compra");
        }
        p.installments = req.installments();
        p.installmentAmount = req.installmentAmount() != null
                ? req.installmentAmount()
                : t.amount.divide(BigDecimal.valueOf(req.installments()), 2, RoundingMode.HALF_UP);
        p.firstPeriod = req.firstPeriod() != null
                ? req.firstPeriod()
                : YearMonth.from(t.transactionDate).plusMonths(1).toString();
        if (p.id == null) p.persist();
        return installmentResponse(p, t, card);
    }

    @Transactional
    public void removeInstallments(Long transactionId) {
        Long uid = userContext.userId();
        InstallmentPlan.delete("transactionId = ?1 AND userId = ?2", transactionId, uid);
    }

    /** Plan de cuotas del movimiento, o vacío si se pagó en una sola. */
    public Optional<InstallmentResponse> getInstallments(Long transactionId) {
        Long uid = userContext.userId();
        return InstallmentPlan.<InstallmentPlan>find("transactionId = ?1 AND userId = ?2", transactionId, uid)
                .firstResultOptional()
                .map(p -> {
                    Transaction t = Transaction.findById(p.transactionId);
                    Account card = accountRepo.findByIdForUser(t.accountId, uid).orElseThrow();
                    return installmentResponse(p, t, card);
                });
    }

    // ---------------------------------------------------------------

    private CardSummary summary(Account card, Map<Long, BigDecimal> movements, Long uid) {
        BigDecimal balance = card.initialBalance.add(movements.getOrDefault(card.id, BigDecimal.ZERO));
        BigDecimal debt = balance.negate();
        BigDecimal available = null;
        BigDecimal utilization = null;
        if (card.creditLimit != null) {
            available = card.creditLimit.subtract(debt.max(BigDecimal.ZERO));
            utilization = debt.max(BigDecimal.ZERO).multiply(HUNDRED)
                    .divide(card.creditLimit, 1, RoundingMode.HALF_UP);
        }
        LocalDate today = LocalDate.now();
        StatementResponse latest = CardStatement.<CardStatement>find("accountId = ?1 ORDER BY closingDate DESC", card.id)
                .firstResultOptional()
                .map(s -> statementResponse(s, card.id))
                .orElse(null);

        // Próximo vencimiento: el del último estado de cuenta si aún no vence; si no, según el día de pago
        LocalDate nextDue = latest != null && !latest.dueDate().isBefore(today) ? latest.dueDate()
                : card.dueDay != null ? nextDayOfMonth(card.dueDay, today) : null;
        LocalDate nextClosing = card.statementDay != null ? nextDayOfMonth(card.statementDay, today) : null;

        List<InstallmentResponse> plans = installments(card, uid);
        List<InstallmentResponse> active = plans.stream().filter(p -> !p.finished()).toList();
        String now = YearMonth.now().toString();
        BigDecimal thisMonth = active.stream()
                .filter(p -> p.firstPeriod().compareTo(now) <= 0 && p.lastPeriod().compareTo(now) >= 0)
                .map(InstallmentResponse::installmentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CardSummary(card.id, card.name, card.currency, card.color,
                debt, card.creditLimit, available, utilization,
                card.statementDay, card.dueDay, nextClosing, nextDue, latest,
                active.size(),
                active.stream().map(InstallmentResponse::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                thisMonth);
    }

    private StatementResponse statementResponse(CardStatement s, Long cardId) {
        BigDecimal paid = paymentsAfter(cardId, s.closingDate);
        BigDecimal remaining = s.totalDue.subtract(paid).max(BigDecimal.ZERO);
        LocalDate today = LocalDate.now();
        boolean minimumCovered = s.minimumDue != null && paid.compareTo(s.minimumDue) >= 0;
        StatementStatus status = remaining.signum() == 0 ? StatementStatus.PAID
                : !today.isAfter(s.dueDate) ? StatementStatus.PENDING
                : minimumCovered ? StatementStatus.MINIMUM_PAID
                : StatementStatus.OVERDUE;
        return new StatementResponse(s.id, s.closingDate, s.dueDate, s.totalDue, s.minimumDue,
                paid, remaining, status, ChronoUnit.DAYS.between(today, s.dueDate));
    }

    /** Lo que entró a la tarjeta después del cierre: ingresos (abonos) y transferencias (pagos). */
    private BigDecimal paymentsAfter(Long cardId, LocalDate closing) {
        BigDecimal result = em.createQuery("""
                        SELECT COALESCE(SUM(CASE WHEN t.type = :transfer THEN COALESCE(t.toAmount, t.amount) ELSE t.amount END), 0)
                        FROM Transaction t
                        WHERE t.transactionDate > :closing
                          AND ((t.type = :income AND t.accountId = :card)
                               OR (t.type = :transfer AND t.toAccountId = :card))
                        """, BigDecimal.class)
                .setParameter("transfer", TransactionType.TRANSFER)
                .setParameter("income", TransactionType.INCOME)
                .setParameter("closing", closing)
                .setParameter("card", cardId)
                .getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }

    private List<InstallmentResponse> installments(Account card, Long uid) {
        List<Object[]> rows = em.createQuery("""
                        SELECT p, t FROM InstallmentPlan p, Transaction t
                        WHERE p.transactionId = t.id AND t.accountId = :card AND p.userId = :uid
                        ORDER BY t.transactionDate DESC
                        """, Object[].class)
                .setParameter("card", card.id)
                .setParameter("uid", uid)
                .getResultList();
        return rows.stream()
                .map(r -> installmentResponse((InstallmentPlan) r[0], (Transaction) r[1], card))
                .toList();
    }

    /**
     * Cuotas facturadas: las de meses anteriores y la de este mes si ya pasó el cierre
     * (sin día de cierre configurado, la del mes cuenta como facturada).
     */
    static InstallmentResponse installmentResponse(InstallmentPlan p, Transaction t, Account card) {
        YearMonth first = YearMonth.parse(p.firstPeriod);
        YearMonth last = first.plusMonths(p.installments - 1L);
        YearMonth now = YearMonth.now();
        LocalDate today = LocalDate.now();
        boolean thisMonthClosed = card.statementDay == null
                || today.getDayOfMonth() >= Math.min(card.statementDay, now.lengthOfMonth());
        long charged = ChronoUnit.MONTHS.between(first, now) + (thisMonthClosed ? 1 : 0);
        int chargedCount = (int) Math.max(0, Math.min(p.installments, charged));
        BigDecimal remaining = p.installmentAmount.multiply(BigDecimal.valueOf(p.installments - chargedCount));
        return new InstallmentResponse(t.id, t.description, t.transactionDate, t.amount,
                p.installments, p.installmentAmount, p.firstPeriod, last.toString(),
                chargedCount, remaining, chargedCount >= p.installments);
    }

    /** Próxima fecha (hoy incluido) con ese día del mes; el 31 cae el último día en meses cortos. */
    static LocalDate nextDayOfMonth(int day, LocalDate from) {
        YearMonth ym = YearMonth.from(from);
        LocalDate candidate = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        if (candidate.isBefore(from)) {
            YearMonth next = ym.plusMonths(1);
            candidate = next.atDay(Math.min(day, next.lengthOfMonth()));
        }
        return candidate;
    }

    private Account card(Long accountId, Long uid) {
        Account a = accountRepo.findByIdForUser(accountId, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (a.type != AccountType.CREDIT_CARD) {
            throw new ApiException(ErrorCode.CARD_INVALID, "La cuenta no es una tarjeta de crédito");
        }
        return a;
    }
}
