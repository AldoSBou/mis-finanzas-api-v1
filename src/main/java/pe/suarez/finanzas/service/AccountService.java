package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.AccountType;
import pe.suarez.finanzas.dto.AccountDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AccountService {

    @Inject AccountRepository repository;
    @Inject UserContext userContext;

    public List<AccountResponse> listAll(boolean includeArchived) {
        Long uid = userContext.userId();
        Map<Long, BigDecimal> movements = repository.movementTotals(uid);
        return repository.listForUser(uid, includeArchived).stream()
                .map(a -> Mappers.toAccountResponse(a, balanceOf(a, movements)))
                .toList();
    }

    public AccountResponse get(Long id) {
        Long uid = userContext.userId();
        Account a = find(id, uid);
        return Mappers.toAccountResponse(a, balanceOf(a, repository.movementTotals(uid)));
    }

    @Transactional
    public AccountResponse create(AccountRequest req) {
        Long uid = userContext.userId();
        Account a = new Account();
        a.userId = uid;
        apply(a, req);
        a.persist();
        return Mappers.toAccountResponse(a, a.initialBalance);
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest req) {
        Long uid = userContext.userId();
        Account a = find(id, uid);
        if (!a.currency.equals(req.currency()) && repository.hasTransactions(a.id)) {
            throw new ApiException(ErrorCode.ACCOUNT_CURRENCY_LOCKED);
        }
        apply(a, req);
        return Mappers.toAccountResponse(a, balanceOf(a, repository.movementTotals(uid)));
    }

    /** Archiva la cuenta: deja de aparecer en listas y formularios, pero conserva su historial. */
    @Transactional
    public void archive(Long id) {
        find(id, userContext.userId()).archived = true;
    }

    private Account find(Long id, Long uid) {
        return repository.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void apply(Account a, AccountRequest req) {
        a.name = req.name().trim();
        a.type = req.type();
        a.currency = validCurrency(req.currency());
        a.initialBalance = req.initialBalance() != null ? req.initialBalance() : BigDecimal.ZERO;
        a.color = req.color();
        a.icon = req.icon();
        boolean card = a.type == AccountType.CREDIT_CARD;
        a.creditLimit = card ? req.creditLimit() : null;
        a.statementDay = card ? req.statementDay() : null;
        a.dueDay = card ? req.dueDay() : null;
    }

    private static String validCurrency(String code) {
        try {
            return Currency.getInstance(code).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.CURRENCY_INVALID);
        }
    }

    private static BigDecimal balanceOf(Account a, Map<Long, BigDecimal> movements) {
        return a.initialBalance.add(movements.getOrDefault(a.id, BigDecimal.ZERO));
    }
}
