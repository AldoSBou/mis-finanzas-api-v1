package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.RecurringTransaction;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.TransactionDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class TransactionService {

    @Inject TransactionRepository txRepo;
    @Inject CategoryRepository catRepo;
    @Inject AccountRepository accountRepo;
    @Inject UserContext userContext;

    public TransactionPage listForMonth(YearMonth ym, Long accountId, int page, int size) {
        Long uid = userContext.userId();
        var transactions = txRepo.listForMonth(uid, ym, accountId, page, size);

        var catIds = transactions.stream().map(t -> t.categoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Category> catsById = catIds.isEmpty()
                ? Map.of()
                : catRepo.list("id IN ?1 AND userId = ?2", catIds, uid).stream()
                    .collect(Collectors.toMap(c -> c.id, c -> c));

        var accountIds = transactions.stream()
                .flatMap(t -> Stream.of(t.accountId, t.toAccountId))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Account> accountsById = accountRepo.mapByIds(accountIds, uid);

        var items = transactions.stream()
                .map(t -> Mappers.toTransactionResponse(t,
                        t.categoryId != null ? catsById.get(t.categoryId) : null,
                        accountsById.get(t.accountId),
                        t.toAccountId != null ? accountsById.get(t.toAccountId) : null))
                .toList();

        long total = txRepo.countForMonth(uid, ym, accountId);
        return new TransactionPage(items, total, page, size);
    }

    public TransactionResponse get(Long id) {
        Long uid = userContext.userId();
        Transaction t = findTx(id, uid);
        Category c = t.categoryId != null ? catRepo.findByIdForUser(t.categoryId, uid).orElse(null) : null;
        Account from = accountRepo.findByIdForUser(t.accountId, uid).orElse(null);
        Account to = t.toAccountId != null ? accountRepo.findByIdForUser(t.toAccountId, uid).orElse(null) : null;
        return Mappers.toTransactionResponse(t, c, from, to);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        Transaction t = new Transaction();
        t.userId = userContext.userId();
        TransactionResponse resp = apply(t, req);
        t.persist();
        return resp;
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest req) {
        Transaction t = findTx(id, userContext.userId());
        return apply(t, req);
    }

    @Transactional
    public void delete(Long id) {
        findTx(id, userContext.userId()).delete();
    }

    /**
     * Tipo de cambio más reciente que el usuario usó para {@code currency}: el de su último
     * movimiento en esa moneda o el implícito en su última compra de esa moneda
     * (transferencia desde una cuenta en moneda base), lo que sea más reciente.
     */
    public Optional<ExchangeRateResponse> latestRate(String currency) {
        Long uid = userContext.userId();
        String base = baseCurrency(uid);
        if (base.equals(currency)) {
            return Optional.of(new ExchangeRateResponse(currency, base, BigDecimal.ONE, null));
        }
        Optional<Transaction> movement = txRepo.latestInCurrency(uid, currency);
        Optional<Transaction> purchase = txRepo.latestTransferInto(uid, currency, base);
        return Stream.of(movement, purchase)
                .flatMap(Optional::stream)
                .max(Comparator.comparing((Transaction t) -> t.transactionDate).thenComparing(t -> t.id))
                .map(t -> new ExchangeRateResponse(currency, base,
                        t.currency.equals(currency)
                                ? t.exchangeRate
                                : t.amount.divide(t.toAmount, 6, RoundingMode.HALF_UP),
                        t.transactionDate));
    }

    /** Valida una solicitud sin guardar nada (lanza ApiException si no es válida). */
    public void validate(TransactionRequest req) {
        Transaction probe = new Transaction();
        probe.userId = userContext.userId();
        apply(probe, req);
    }

    /**
     * Crea el movimiento de una ocurrencia de un recurrente. Sin {@code @Transactional}
     * a propósito: corre dentro de la transacción del llamador, y si la validación falla
     * la ApiException no marca esa transacción para rollback.
     */
    public Transaction createFromRecurring(RecurringTransaction r, LocalDate date,
                                           BigDecimal amount, BigDecimal exchangeRate) {
        Transaction t = new Transaction();
        t.userId = r.userId;
        apply(t, requestFor(r, date, amount, exchangeRate));
        t.recurringId = r.id;
        t.persist();
        return t;
    }

    public static TransactionRequest requestFor(RecurringTransaction r, LocalDate date,
                                                BigDecimal amount, BigDecimal exchangeRate) {
        return new TransactionRequest(
                r.type, r.accountId, r.categoryId, r.toAccountId,
                amount != null ? amount : r.amount,
                r.toAmount,
                exchangeRate != null ? exchangeRate : r.exchangeRate,
                date, r.description, null);
    }

    // ---------------------------------------------------------------

    /** Valida la solicitud y llena {@code t}. Sirve tanto para crear como para editar. */
    private TransactionResponse apply(Transaction t, TransactionRequest req) {
        Long uid = t.userId;
        Account from = usableAccount(req.accountId(), uid, t.accountId);

        Category category = null;
        Account to = null;
        BigDecimal toAmount = null;

        if (req.type() == TransactionType.TRANSFER) {
            if (req.toAccountId() == null) {
                throw new ApiException(ErrorCode.TRANSFER_INVALID, "Elige la cuenta destino");
            }
            if (req.toAccountId().equals(from.id)) {
                throw new ApiException(ErrorCode.TRANSFER_INVALID, "La cuenta destino debe ser distinta a la de origen");
            }
            to = usableAccount(req.toAccountId(), uid, t.toAccountId);
            if (from.currency.equals(to.currency)) {
                toAmount = req.amount();
            } else if (req.toAmount() == null) {
                throw new ApiException(ErrorCode.TRANSFER_INVALID,
                        "Indica cuánto se recibió en " + to.currency + " en la cuenta '" + to.name + "'");
            } else {
                toAmount = req.toAmount();
            }
        } else {
            if (req.categoryId() == null) {
                throw new ApiException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);
            }
            category = catRepo.findByIdForUser(req.categoryId(), uid)
                    .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
            if (category.type != req.type()) {
                throw new ApiException(ErrorCode.TRANSACTION_TYPE_MISMATCH,
                        "El movimiento es " + req.type() + " pero la categoría '" + category.name + "' es " + category.type);
            }
        }

        BigDecimal rate = resolveRate(from, to, req.amount(), toAmount, req.exchangeRate(), baseCurrency(uid));

        t.type = req.type();
        t.accountId = from.id;
        t.categoryId = category != null ? category.id : null;
        t.toAccountId = to != null ? to.id : null;
        t.amount = req.amount();
        t.toAmount = toAmount;
        t.currency = from.currency;
        t.exchangeRate = rate;
        t.amountBase = req.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        t.transactionDate = req.transactionDate();
        t.description = req.description();
        t.paymentMethod = req.paymentMethod();

        return Mappers.toTransactionResponse(t, category, from, to);
    }

    /**
     * Tipo de cambio de la moneda de la cuenta origen a la moneda base:
     * 1 si ya es la base; implícito si es una transferencia hacia la base
     * (ej. cambio 100 USD → 372 PEN = 3.72); si no, el que envió el cliente.
     */
    private static BigDecimal resolveRate(Account from, Account to, BigDecimal amount, BigDecimal toAmount,
                                          BigDecimal requested, String base) {
        if (from.currency.equals(base)) {
            return BigDecimal.ONE;
        }
        if (to != null && to.currency.equals(base)) {
            return toAmount.divide(amount, 6, RoundingMode.HALF_UP);
        }
        if (requested == null) {
            throw new ApiException(ErrorCode.EXCHANGE_RATE_REQUIRED,
                    "Indica el tipo de cambio de " + from.currency + " a " + base);
        }
        return requested;
    }

    /**
     * Cuenta del usuario que admite movimientos. Una cuenta archivada solo se acepta
     * si el movimiento ya estaba en ella (para poder editar el historial).
     */
    private Account usableAccount(Long accountId, Long uid, Long currentAccountId) {
        Account a = accountRepo.findByIdForUser(accountId, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (a.archived && !a.id.equals(currentAccountId)) {
            throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED, "La cuenta '" + a.name + "' está archivada");
        }
        return a;
    }

    private Transaction findTx(Long id, Long uid) {
        return txRepo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    static String baseCurrency(Long uid) {
        User user = User.findById(uid);
        return user != null ? user.currencyDefault : "PEN";
    }
}
