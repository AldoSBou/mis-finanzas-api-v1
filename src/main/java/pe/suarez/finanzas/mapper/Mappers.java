package pe.suarez.finanzas.mapper;

import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.AllocationRule;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.AccountDtos;
import pe.suarez.finanzas.dto.AuthDtos;
import pe.suarez.finanzas.dto.BudgetDtos;
import pe.suarez.finanzas.dto.CategoryDtos;
import pe.suarez.finanzas.dto.TransactionDtos;

import java.math.BigDecimal;

public final class Mappers {

    private Mappers() {}

    public static AuthDtos.UserResponse toUserResponse(User u) {
        return new AuthDtos.UserResponse(u.id, u.email, u.displayName, u.currencyDefault);
    }

    public static CategoryDtos.CategoryResponse toCategoryResponse(Category c) {
        return new CategoryDtos.CategoryResponse(
                c.id, c.name, c.type, c.defaultBucket, c.color, c.icon, c.archived);
    }

    public static AccountDtos.AccountResponse toAccountResponse(Account a, BigDecimal balance) {
        return new AccountDtos.AccountResponse(
                a.id, a.name, a.type, a.currency, a.initialBalance, balance,
                a.color, a.icon, a.archived);
    }

    /** {@code c} es null en transferencias; {@code to} es null en ingresos/gastos. */
    public static TransactionDtos.TransactionResponse toTransactionResponse(
            Transaction t, Category c, Account from, Account to) {
        return new TransactionDtos.TransactionResponse(
                t.id, t.type,
                t.accountId, from != null ? from.name : null,
                t.toAccountId, to != null ? to.name : null,
                t.categoryId,
                c != null ? c.name : null,
                c != null ? c.color : null,
                t.amount, t.currency,
                t.toAmount, to != null ? to.currency : null,
                t.exchangeRate, t.amountBase,
                t.transactionDate, t.description,
                t.paymentMethod, t.recurringId, t.createdAt);
    }

    public static BudgetDtos.AllocationRuleResponse toAllocationRuleResponse(AllocationRule r) {
        return new BudgetDtos.AllocationRuleResponse(
                r.id, r.name, r.description, r.percentages, r.template);
    }
}
