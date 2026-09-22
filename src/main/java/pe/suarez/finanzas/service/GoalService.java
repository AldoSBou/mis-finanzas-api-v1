package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.GoalContribution;
import pe.suarez.finanzas.domain.SavingsGoal;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.GoalDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionRequest;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Metas de ahorro. Lo ahorrado en una meta es la suma de sus aportes (no el saldo de la cuenta):
 * así varias metas pueden compartir una cuenta, y lo que no está en ninguna queda "sin asignar".
 */
@ApplicationScoped
public class GoalService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Meses para calcular el ritmo de aporte */
    private static final int PACE_MONTHS = 3;

    @Inject AccountRepository accountRepo;
    @Inject TransactionService txService;
    @Inject UserContext userContext;

    public GoalsOverview overview(boolean includeArchived) {
        Long uid = userContext.userId();
        List<SavingsGoal> goals = SavingsGoal.<SavingsGoal>list(
                includeArchived ? "userId = ?1 ORDER BY archived, createdAt" : "userId = ?1 AND archived = false ORDER BY createdAt",
                uid);
        Map<Long, BigDecimal> saved = sums(uid, null);
        Map<Long, BigDecimal> recent = sums(uid, LocalDate.now().minusMonths(PACE_MONTHS));
        Map<Long, Account> accounts = accountRepo.mapByIds(goals.stream().map(g -> g.accountId).distinct().toList(), uid);

        List<GoalResponse> responses = goals.stream()
                .map(g -> toResponse(g, accounts.get(g.accountId),
                        saved.getOrDefault(g.id, BigDecimal.ZERO),
                        recent.getOrDefault(g.id, BigDecimal.ZERO)))
                .toList();

        // Por cuenta: saldo, asignado a metas activas y lo que queda libre
        Map<Long, BigDecimal> movements = accountRepo.movementTotals(uid);
        Map<Long, BigDecimal> assigned = new LinkedHashMap<>();
        goals.stream().filter(g -> !g.archived)
                .forEach(g -> assigned.merge(g.accountId, saved.getOrDefault(g.id, BigDecimal.ZERO), BigDecimal::add));
        List<AccountAllocation> allocations = assigned.entrySet().stream().map(e -> {
            Account a = accounts.get(e.getKey());
            BigDecimal balance = a.initialBalance.add(movements.getOrDefault(a.id, BigDecimal.ZERO));
            return new AccountAllocation(a.id, a.name, a.currency, balance, e.getValue(), balance.subtract(e.getValue()));
        }).toList();

        return new GoalsOverview(responses, allocations);
    }

    @Transactional
    public GoalResponse create(GoalRequest req) {
        Long uid = userContext.userId();
        Account account = account(req.accountId(), uid);
        if (account.archived) throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED);
        SavingsGoal g = new SavingsGoal();
        g.userId = uid;
        g.accountId = account.id;
        apply(g, req);
        g.persist();
        return toResponse(g, account, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public GoalResponse update(Long id, GoalRequest req) {
        Long uid = userContext.userId();
        SavingsGoal g = goal(id, uid);
        if (!g.accountId.equals(req.accountId())) {
            if (GoalContribution.count("goalId", g.id) > 0) {
                throw new ApiException(ErrorCode.GOAL_INVALID,
                        "La meta ya tiene aportes en su cuenta: no se puede cambiar de cuenta");
            }
            g.accountId = account(req.accountId(), uid).id;
        }
        apply(g, req);
        Map<Long, BigDecimal> saved = sums(uid, null);
        Map<Long, BigDecimal> recent = sums(uid, LocalDate.now().minusMonths(PACE_MONTHS));
        return toResponse(g, account(g.accountId, uid),
                saved.getOrDefault(g.id, BigDecimal.ZERO), recent.getOrDefault(g.id, BigDecimal.ZERO));
    }

    /** Borra la meta y sus aportes. Las transferencias ya hechas se conservan: el dinero sigue en la cuenta. */
    @Transactional
    public void delete(Long id) {
        Long uid = userContext.userId();
        SavingsGoal g = goal(id, uid);
        // Se borran los aportes; sus transferencias quedan en Movimientos
        GoalContribution.delete("goalId", g.id);
        g.delete();
    }

    public List<ContributionResponse> contributions(Long goalId) {
        Long uid = userContext.userId();
        goal(goalId, uid);
        return GoalContribution.<GoalContribution>list(
                        "goalId = ?1 ORDER BY contributionDate DESC, id DESC", goalId).stream()
                .map(c -> new ContributionResponse(c.id, c.amount, c.contributionDate, c.note, c.transactionId))
                .toList();
    }

    @Transactional
    public ContributionResponse contribute(Long goalId, ContributionRequest req) {
        Long uid = userContext.userId();
        SavingsGoal g = goal(goalId, uid);
        Account goalAccount = account(g.accountId, uid);
        boolean in = req.direction() == Direction.IN;
        BigDecimal saved = sums(uid, null).getOrDefault(g.id, BigDecimal.ZERO);

        if (!in && req.amount().compareTo(saved) > 0) {
            throw new ApiException(ErrorCode.GOAL_INVALID,
                    "No puedes retirar más de lo ahorrado en la meta (" + saved.toPlainString() + ")");
        }

        Long transactionId = null;
        if (req.otherAccountId() != null) {
            Account other = account(req.otherAccountId(), uid);
            if (other.id.equals(goalAccount.id)) {
                throw new ApiException(ErrorCode.GOAL_INVALID, "Elige una cuenta distinta a la de la meta");
            }
            if (!other.currency.equals(goalAccount.currency)) {
                throw new ApiException(ErrorCode.GOAL_INVALID,
                        "La cuenta debe estar en " + goalAccount.currency + ", como la de la meta");
            }
            String description = req.note() != null && !req.note().isBlank()
                    ? req.note()
                    : (in ? "Aporte a meta: " : "Retiro de meta: ") + g.name;
            TransactionRequest tx = new TransactionRequest(TransactionType.TRANSFER,
                    in ? other.id : goalAccount.id, null, in ? goalAccount.id : other.id,
                    req.amount(), null, req.exchangeRate(), req.date(), description, null);
            Transaction t = txService.createWithinTransaction(tx, uid);
            transactionId = t.id;
        } else if (in) {
            // Solo asignar: el dinero ya está en la cuenta, pero no puede asignarse más del saldo libre
            BigDecimal unassigned = unassigned(goalAccount, uid);
            if (req.amount().compareTo(unassigned) > 0) {
                throw new ApiException(ErrorCode.GOAL_INVALID,
                        "En " + goalAccount.name + " solo hay " + unassigned.max(BigDecimal.ZERO).toPlainString()
                                + " sin asignar. Aporta desde otra cuenta para transferir el dinero.");
            }
        }

        GoalContribution c = new GoalContribution();
        c.goalId = g.id;
        c.userId = uid;
        c.amount = in ? req.amount() : req.amount().negate();
        c.contributionDate = req.date();
        c.note = req.note();
        c.transactionId = transactionId;
        c.persist();
        return new ContributionResponse(c.id, c.amount, c.contributionDate, c.note, c.transactionId);
    }

    /** Borra un aporte; si movió dinero, también su transferencia. */
    @Transactional
    public void deleteContribution(Long goalId, Long contributionId) {
        Long uid = userContext.userId();
        goal(goalId, uid);
        GoalContribution c = GoalContribution.<GoalContribution>find("id = ?1 AND goalId = ?2", contributionId, goalId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Aporte no encontrado"));
        if (c.transactionId != null) {
            // La FK borra el aporte junto con la transferencia
            Transaction.delete("id = ?1 AND userId = ?2", c.transactionId, uid);
        } else {
            c.delete();
        }
    }

    // ---------------------------------------------------------------

    private void apply(SavingsGoal g, GoalRequest req) {
        g.name = req.name().trim();
        g.targetAmount = req.targetAmount();
        g.targetDate = req.targetDate();
        g.color = req.color();
        if (req.archived() != null) g.archived = req.archived();
    }

    /** Suma de aportes por meta del usuario (desde {@code from}, o todos si es null). */
    private Map<Long, BigDecimal> sums(Long uid, LocalDate from) {
        String q = "SELECT c.goalId, SUM(c.amount) FROM GoalContribution c WHERE c.userId = ?1"
                + (from != null ? " AND c.contributionDate >= ?2" : "") + " GROUP BY c.goalId";
        var query = GoalContribution.getEntityManager().createQuery(q, Object[].class).setParameter(1, uid);
        if (from != null) query.setParameter(2, from);
        return query.getResultList().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));
    }

    private BigDecimal unassigned(Account account, Long uid) {
        BigDecimal balance = account.initialBalance.add(accountRepo.movementTotals(uid).getOrDefault(account.id, BigDecimal.ZERO));
        Map<Long, BigDecimal> saved = sums(uid, null);
        BigDecimal assigned = SavingsGoal.<SavingsGoal>list("userId = ?1 AND accountId = ?2 AND archived = false", uid, account.id)
                .stream().map(g -> saved.getOrDefault(g.id, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return balance.subtract(assigned);
    }

    static GoalResponse toResponse(SavingsGoal g, Account account, BigDecimal saved, BigDecimal recent) {
        BigDecimal remaining = g.targetAmount.subtract(saved).max(BigDecimal.ZERO);
        boolean completed = remaining.signum() == 0;
        BigDecimal percentage = saved.multiply(HUNDRED).divide(g.targetAmount, 1, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
        BigDecimal pace = recent.divide(BigDecimal.valueOf(PACE_MONTHS), 2, RoundingMode.HALF_UP);

        Integer monthsLeft = null;
        BigDecimal monthlyNeeded = null;
        Boolean onTrack = null;
        if (g.targetDate != null && !completed) {
            // Meses de aporte que quedan, contando el actual (0 si la fecha ya pasó)
            long months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(g.targetDate)) + 1;
            monthsLeft = (int) Math.max(0, months);
            monthlyNeeded = remaining.divide(BigDecimal.valueOf(Math.max(1, monthsLeft)), 2, RoundingMode.UP);
            onTrack = monthsLeft > 0 && pace.compareTo(monthlyNeeded) >= 0;
        }
        String projected = null;
        if (!completed && pace.signum() > 0) {
            long monthsToGo = remaining.divide(pace, 0, RoundingMode.CEILING).longValue();
            projected = YearMonth.now().plusMonths(Math.max(0, monthsToGo - 1)).toString();
        }
        return new GoalResponse(g.id, g.name, g.accountId,
                account != null ? account.name : null, account != null ? account.currency : null,
                g.targetAmount, g.targetDate, g.color, g.archived,
                saved, remaining, percentage, completed,
                monthsLeft, monthlyNeeded, pace, projected, onTrack);
    }

    private SavingsGoal goal(Long id, Long uid) {
        return SavingsGoal.<SavingsGoal>find("id = ?1 AND userId = ?2", id, uid)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.GOAL_NOT_FOUND));
    }

    private Account account(Long id, Long uid) {
        return accountRepo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
