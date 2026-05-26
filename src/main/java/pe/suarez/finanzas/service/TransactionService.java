package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.dto.TransactionDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class TransactionService {

    @Inject TransactionRepository txRepo;
    @Inject CategoryRepository catRepo;
    @Inject UserContext userContext;

    public TransactionPage listForMonth(YearMonth ym, int page, int size) {
        Long uid = userContext.userId();
        var transactions = txRepo.listForMonth(uid, ym, page, size);

        var catIds = transactions.stream().map(t -> t.categoryId).distinct().toList();
        Map<Long, Category> catsById = catIds.isEmpty()
                ? Map.of()
                : catRepo.list("id IN ?1 AND userId = ?2", catIds, uid).stream()
                    .collect(Collectors.toMap(c -> c.id, c -> c));

        var items = transactions.stream()
                .map(t -> Mappers.toTransactionResponse(t, catsById.get(t.categoryId)))
                .toList();

        long total = txRepo.countForMonth(uid, ym);
        return new TransactionPage(items, total, page, size);
    }

    public TransactionResponse get(Long id) {
        Long uid = userContext.userId();
        Transaction t = txRepo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        Category c = catRepo.findByIdForUser(t.categoryId, uid).orElse(null);
        return Mappers.toTransactionResponse(t, c);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        Long uid = userContext.userId();
        Category c = catRepo.findByIdForUser(req.categoryId(), uid)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));

        if (c.type != req.type()) {
            throw new ApiException(ErrorCode.TRANSACTION_TYPE_MISMATCH,
                    "El movimiento es " + req.type() + " pero la categoría '" + c.name + "' es " + c.type);
        }

        Transaction t = new Transaction();
        t.userId = uid;
        t.categoryId = c.id;
        t.amount = req.amount();
        t.type = req.type();
        t.transactionDate = req.transactionDate();
        t.description = req.description();
        t.paymentMethod = req.paymentMethod();
        t.currency = req.currency() != null ? req.currency() : "PEN";
        t.persist();

        return Mappers.toTransactionResponse(t, c);
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest req) {
        Long uid = userContext.userId();
        Transaction t = txRepo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        Category c = catRepo.findByIdForUser(req.categoryId(), uid)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));

        if (c.type != req.type()) {
            throw new ApiException(ErrorCode.TRANSACTION_TYPE_MISMATCH,
                    "El movimiento es " + req.type() + " pero la categoría '" + c.name + "' es " + c.type);
        }

        t.categoryId = c.id;
        t.amount = req.amount();
        t.type = req.type();
        t.transactionDate = req.transactionDate();
        t.description = req.description();
        t.paymentMethod = req.paymentMethod();
        if (req.currency() != null) t.currency = req.currency();

        return Mappers.toTransactionResponse(t, c);
    }

    @Transactional
    public void delete(Long id) {
        Long uid = userContext.userId();
        Transaction t = txRepo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        t.delete();
    }
}
