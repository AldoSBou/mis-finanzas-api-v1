package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.dto.RecurringDtos.UpcomingItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class BudgetDtos {

    private BudgetDtos() {}

    public record MonthlyBudgetRequest(
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull @Min(1) @Max(12) Integer month,
            @NotNull @DecimalMin("0.00") BigDecimal expectedIncome,
            Long activeRuleId
    ) {}

    public record MonthlyBudgetResponse(
            Long id,
            Integer year,
            Integer month,
            BigDecimal expectedIncome,
            Long activeRuleId,
            String activeRuleName
    ) {}

    public record AllocationRuleRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 250) String description,
            @NotNull Map<String, Integer> percentages
    ) {}

    public record AllocationRuleResponse(
            Long id,
            String name,
            String description,
            Map<String, Integer> percentages,
            boolean template
    ) {}

    public record BucketSummary(
            AllocationBucket bucket,
            BigDecimal allocated,
            BigDecimal spent,
            BigDecimal percentageUsed
    ) {}

    public record CategoryTotal(
            Long categoryId,
            String categoryName,
            BigDecimal total
    ) {}

    /**
     * Montos en la moneda base del usuario.
     * {@code expenses} es consumo (sin ahorro ni inversión); {@code savings} es el ahorro
     * neto del mes; {@code balance = income - expenses - savings}.
     */
    public record DashboardResponse(
            int year,
            int month,
            String baseCurrency,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal balance,
            BigDecimal savingsYearToDate,
            BigDecimal expectedIncome,      // NUEVO: lo que se usó como base de cálculo
            boolean budgetConfigured,        // NUEVO: si el usuario configuró un budget
            AllocationRuleResponse activeRule,
            List<BucketSummary> bucketSummaries,
            List<CategoryTotal> topCategories,
            /** Solo en el mes actual: recurrentes pendientes y por venir hasta fin de mes */
            List<UpcomingItem> upcoming,
            /** Solo en el mes actual: balance + efecto de los recurrentes por venir */
            BigDecimal projectedBalance
    ) {}
}
