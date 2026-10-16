package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.*;
import pe.suarez.finanzas.dto.CommitmentDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionRequest;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class CommitmentService {

    @Inject AccountRepository accountRepo;
    @Inject CategoryRepository catRepo;
    @Inject TransactionService txService;
    @Inject UserContext userContext;

    /** Todos los compromisos del usuario, pendientes primero y por fecha. */
    public List<CommitmentResponse> list(Commitment.Status status) {
        Long uid = userContext.userId();
        List<Commitment> list = status != null
                ? Commitment.list("userId = ?1 AND status = ?2 ORDER BY dueDate, id", uid, status)
                : Commitment.list("userId = ?1 ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, dueDate DESC, id", uid);
        return toResponses(list, uid);
    }

    public List<Commitment> pending(Long uid) {
        return Commitment.list("userId = ?1 AND status = ?2 ORDER BY dueDate", uid, Commitment.Status.PENDING);
    }

    public List<Commitment> paidBetween(Long uid, LocalDate from, LocalDate to) {
        return Commitment.list("userId = ?1 AND status = ?2 AND paidAt BETWEEN ?3 AND ?4",
                uid, Commitment.Status.PAID, from, to);
    }

    @Transactional
    public CommitmentResponse create(CommitmentRequest req) {
        Long uid = userContext.userId();
        Commitment c = new Commitment();
        c.userId = uid;
        apply(c, req, uid);
        c.persist();
        return toResponses(List.of(c), uid).get(0);
    }

    @Transactional
    public CommitmentResponse update(Long id, CommitmentRequest req) {
        Long uid = userContext.userId();
        Commitment c = find(id, uid);
        if (c.status != Commitment.Status.PENDING) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "Solo se puede editar un compromiso pendiente");
        }
        apply(c, req, uid);
        return toResponses(List.of(c), uid).get(0);
    }

    @Transactional
    public void delete(Long id) {
        find(id, userContext.userId()).delete();
    }

    /**
     * Registra el pago y cierra el compromiso. Si tiene cuenta destino, el pago es
     * "mover dinero" (p. ej. a la tarjeta); si no, un gasto en su categoría.
     */
    @Transactional
    public CommitmentResponse pay(Long id, PayRequest req) {
        Long uid = userContext.userId();
        Commitment c = find(id, uid);
        if (c.status != Commitment.Status.PENDING) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "Este compromiso ya no está pendiente");
        }
        Long accountId = req != null && req.accountId() != null ? req.accountId() : c.accountId;
        if (accountId == null) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "Indica desde qué cuenta se pagó");
        }
        if (accountId.equals(c.toAccountId)) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "La cuenta de pago no puede ser la misma cuenta destino");
        }
        BigDecimal amount = req != null && req.amount() != null ? req.amount() : c.amount;
        LocalDate date = req != null && req.date() != null ? req.date() : LocalDate.now();
        BigDecimal rate = req != null ? req.exchangeRate() : null;

        TransactionRequest tx = c.toAccountId != null
                ? new TransactionRequest(TransactionType.TRANSFER, accountId, null, c.toAccountId,
                        amount, null, rate, date, c.name, null)
                : new TransactionRequest(TransactionType.EXPENSE, accountId, c.categoryId, null,
                        amount, null, rate, date, c.name, null);
        Transaction t = txService.createWithinTransaction(tx, uid);

        c.status = Commitment.Status.PAID;
        c.paidTransactionId = t.id;
        c.paidAt = date;
        return toResponses(List.of(c), uid).get(0);
    }

    /** Lo da por resuelto sin registrar nada (ya no aplica o se pagó de otra forma). */
    @Transactional
    public CommitmentResponse skip(Long id) {
        Long uid = userContext.userId();
        Commitment c = find(id, uid);
        if (c.status != Commitment.Status.PENDING) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "Este compromiso ya no está pendiente");
        }
        c.status = Commitment.Status.SKIPPED;
        return toResponses(List.of(c), uid).get(0);
    }

    /** Vuelve a dejarlo pendiente (el movimiento registrado, si lo hubo, se conserva). */
    @Transactional
    public CommitmentResponse reopen(Long id) {
        Long uid = userContext.userId();
        Commitment c = find(id, uid);
        c.status = Commitment.Status.PENDING;
        c.paidTransactionId = null;
        c.paidAt = null;
        return toResponses(List.of(c), uid).get(0);
    }

    // ---------------------------------------------------------------

    private void apply(Commitment c, CommitmentRequest req, Long uid) {
        if (req.toAccountId() == null && req.categoryId() == null) {
            throw new ApiException(ErrorCode.COMMITMENT_INVALID, "Indica la categoría del gasto o la cuenta a la que se paga");
        }
        if (req.toAccountId() != null) {
            accountRepo.findByIdForUser(req.toAccountId(), uid)
                    .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
            if (req.toAccountId().equals(req.accountId())) {
                throw new ApiException(ErrorCode.COMMITMENT_INVALID, "La cuenta de pago no puede ser la misma cuenta destino");
            }
        }
        if (req.accountId() != null) {
            accountRepo.findByIdForUser(req.accountId(), uid)
                    .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        }
        if (req.categoryId() != null) {
            Category cat = catRepo.findByIdForUser(req.categoryId(), uid)
                    .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
            if (cat.type != TransactionType.EXPENSE) {
                throw new ApiException(ErrorCode.COMMITMENT_INVALID, "La categoría debe ser de gasto");
            }
        }
        c.name = req.name().trim();
        c.amount = req.amount();
        c.dueDate = req.dueDate();
        c.toAccountId = req.toAccountId();
        c.categoryId = req.toAccountId() != null ? null : req.categoryId();
        c.accountId = req.accountId();
        c.note = req.note();
    }

    private Commitment find(Long id, Long uid) {
        return Commitment.<Commitment>find("id = ?1 AND userId = ?2", id, uid).firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.COMMITMENT_NOT_FOUND));
    }

    List<CommitmentResponse> toResponses(List<Commitment> list, Long uid) {
        Map<Long, Account> accounts = accountRepo.mapByIds(list.stream()
                .flatMap(c -> Stream.of(c.accountId, c.toAccountId))
                .filter(Objects::nonNull).distinct().toList(), uid);
        Map<Long, Category> categories = categoriesFor(list, uid);
        String base = TransactionService.baseCurrency(uid);
        LocalDate today = LocalDate.now();
        return list.stream().map(c -> {
            Account from = c.accountId != null ? accounts.get(c.accountId) : null;
            Account to = c.toAccountId != null ? accounts.get(c.toAccountId) : null;
            Category cat = c.categoryId != null ? categories.get(c.categoryId) : null;
            return new CommitmentResponse(c.id, c.name, c.amount, base, c.dueDate,
                    c.status == Commitment.Status.PENDING && c.dueDate.isBefore(today),
                    c.categoryId, cat != null ? cat.name : null,
                    c.accountId, from != null ? from.name : null,
                    c.toAccountId, to != null ? to.name : null,
                    c.note, c.status, c.paidTransactionId, c.paidAt);
        }).toList();
    }

    Map<Long, Category> categoriesFor(List<Commitment> list, Long uid) {
        var ids = list.stream().map(c -> c.categoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Category> result = new HashMap<>();
        for (Category c : catRepo.<Category>list("id IN ?1 AND userId = ?2", ids, uid)) result.put(c.id, c);
        return result;
    }
}
