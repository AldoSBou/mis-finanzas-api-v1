package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.ImportBatch;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.ImportDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionRequest;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Importación de estados de cuenta. La web lee el archivo (CSV/Excel) y manda filas ya
 * interpretadas: fecha, descripción y monto con signo (negativo = sale dinero de la cuenta).
 */
@ApplicationScoped
public class ImportService {

    @Inject AccountRepository accountRepo;
    @Inject TransactionService txService;
    @Inject CategorizationService categorization;
    @Inject UserContext userContext;

    /** Tipo sugerido, categoría sugerida y si parece duplicado de algo ya registrado. */
    public PreviewResponse preview(PreviewRequest req) {
        Long uid = userContext.userId();
        Account account = findAccount(req.accountId(), uid);
        DuplicateCounter duplicates = new DuplicateCounter(account.id, req.rows().stream().map(ImportRow::date).toList());
        CategorizationService.Suggester suggester = categorization.suggester();

        List<PreviewRow> rows = new ArrayList<>();
        for (int i = 0; i < req.rows().size(); i++) {
            ImportRow row = req.rows().get(i);
            TransactionType type = typeOf(row.amount());
            var s = suggester.suggest(row.description(), type);
            rows.add(new PreviewRow(i, type, s.categoryId(), s.source(),
                    duplicates.consume(row.date(), row.amount().abs(), type)));
        }
        return new PreviewResponse(rows);
    }

    /** Crea todos los movimientos en una sola transacción: si una fila falla, no se importa nada. */
    @Transactional
    public ImportBatchResponse commit(CommitRequest req) {
        Long uid = userContext.userId();
        Account account = findAccount(req.accountId(), uid);

        ImportBatch batch = new ImportBatch();
        batch.userId = uid;
        batch.accountId = account.id;
        batch.fileName = req.fileName();
        batch.rowCount = req.rows().size();
        batch.persist();

        for (int i = 0; i < req.rows().size(); i++) {
            CommitRow row = req.rows().get(i);
            try {
                Transaction t = txService.createFromImport(requestFor(account.id, row, req.exchangeRate()), uid);
                t.importBatchId = batch.id;
            } catch (ApiException e) {
                throw new ApiException(e.code(), "Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        return toResponse(batch, account, batch.rowCount);
    }

    public List<ImportBatchResponse> recent() {
        Long uid = userContext.userId();
        List<ImportBatch> batches = ImportBatch.<ImportBatch>find("userId = ?1 ORDER BY createdAt DESC", uid)
                .page(0, 20).list();
        Map<Long, Account> accounts = accountRepo.mapByIds(
                batches.stream().map(b -> b.accountId).distinct().toList(), uid);
        return batches.stream()
                .map(b -> toResponse(b, accounts.get(b.accountId),
                        Transaction.count("importBatchId", b.id)))
                .toList();
    }

    /** Deshace la importación: borra sus movimientos y el registro de la importación. */
    @Transactional
    public void undo(Long batchId) {
        Long uid = userContext.userId();
        ImportBatch batch = ImportBatch.<ImportBatch>find("id = ?1 AND userId = ?2", batchId, uid)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Importación no encontrada"));
        Transaction.delete("importBatchId = ?1 AND userId = ?2", batch.id, uid);
        batch.delete();
    }

    // ---------------------------------------------------------------

    private static TransactionType typeOf(BigDecimal signedAmount) {
        return signedAmount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
    }

    /** Fila → movimiento: con cuenta de transferencia es TRANSFER (el signo da la dirección). */
    private static TransactionRequest requestFor(Long accountId, CommitRow row, BigDecimal exchangeRate) {
        if (row.amount().signum() == 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "El monto no puede ser cero");
        }
        BigDecimal amount = row.amount().abs();
        if (row.transferAccountId() != null) {
            boolean outgoing = row.amount().signum() < 0;
            return new TransactionRequest(TransactionType.TRANSFER,
                    outgoing ? accountId : row.transferAccountId(),
                    null,
                    outgoing ? row.transferAccountId() : accountId,
                    amount, null, exchangeRate, row.date(), row.description(), null);
        }
        return new TransactionRequest(typeOf(row.amount()), accountId, row.categoryId(), null,
                amount, null, exchangeRate, row.date(), row.description(), null);
    }

    private Account findAccount(Long accountId, Long uid) {
        return accountRepo.findByIdForUser(accountId, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private static ImportBatchResponse toResponse(ImportBatch b, Account a, long remaining) {
        return new ImportBatchResponse(b.id, b.accountId, a != null ? a.name : null,
                b.fileName, b.rowCount, remaining, b.createdAt);
    }

    /**
     * Cuenta los movimientos ya registrados en la cuenta por (fecha, monto, tipo). Cada fila
     * importada "consume" uno: si hay dos cafés iguales el mismo día y solo uno registrado,
     * solo la primera fila se marca como duplicado.
     */
    private static final class DuplicateCounter {
        private final Map<String, Integer> counts = new HashMap<>();

        DuplicateCounter(Long accountId, List<LocalDate> dates) {
            if (dates.isEmpty()) return;
            LocalDate from = Collections.min(dates);
            LocalDate to = Collections.max(dates);
            List<Transaction> existing = Transaction.list(
                    "(accountId = ?1 OR toAccountId = ?1) AND transactionDate BETWEEN ?2 AND ?3",
                    accountId, from, to);
            for (Transaction t : existing) {
                counts.merge(key(t.transactionDate, amountSeenBy(t, accountId), typeSeenBy(t, accountId)), 1, Integer::sum);
            }
        }

        boolean consume(LocalDate date, BigDecimal amount, TransactionType type) {
            String k = key(date, amount, type);
            Integer n = counts.get(k);
            if (n == null || n == 0) return false;
            counts.put(k, n - 1);
            return true;
        }

        /** Cómo aparece el movimiento en el estado de cuenta de esta cuenta. */
        private static TransactionType typeSeenBy(Transaction t, Long accountId) {
            if (t.type != TransactionType.TRANSFER) return t.type;
            return accountId.equals(t.toAccountId) ? TransactionType.INCOME : TransactionType.EXPENSE;
        }

        private static BigDecimal amountSeenBy(Transaction t, Long accountId) {
            return t.type == TransactionType.TRANSFER && accountId.equals(t.toAccountId) ? t.toAmount : t.amount;
        }

        private static String key(LocalDate date, BigDecimal amount, TransactionType type) {
            return date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + type;
        }
    }
}
