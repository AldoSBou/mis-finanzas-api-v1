package pe.suarez.finanzas.mapper;

import pe.suarez.finanzas.domain.AllocationRule;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.AuthDtos;
import pe.suarez.finanzas.dto.BudgetDtos;
import pe.suarez.finanzas.dto.CategoryDtos;
import pe.suarez.finanzas.dto.TransactionDtos;

public final class Mappers {

    private Mappers() {}

    public static AuthDtos.UserResponse toUserResponse(User u) {
        return new AuthDtos.UserResponse(u.id, u.email, u.displayName, u.currencyDefault);
    }

    public static CategoryDtos.CategoryResponse toCategoryResponse(Category c) {
        return new CategoryDtos.CategoryResponse(
                c.id, c.name, c.type, c.defaultBucket, c.color, c.icon, c.archived);
    }

    public static TransactionDtos.TransactionResponse toTransactionResponse(Transaction t, Category c) {
        return new TransactionDtos.TransactionResponse(
                t.id, t.categoryId,
                c != null ? c.name : null,
                c != null ? c.color : null,
                t.amount, t.type, t.transactionDate, t.description,
                t.paymentMethod, t.currency, t.createdAt);
    }

    public static BudgetDtos.AllocationRuleResponse toAllocationRuleResponse(AllocationRule r) {
        return new BudgetDtos.AllocationRuleResponse(
                r.id, r.name, r.description, r.percentages, r.template);
    }
}
