package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import pe.suarez.finanzas.domain.AllocationBucket;

import java.math.BigDecimal;
import java.util.List;

public final class CategoryBudgetDtos {

    private CategoryBudgetDtos() {}

    public enum BudgetStatus {
        /** Sin límite definido */
        NONE,
        /** Menos del 80% */
        OK,
        /** Entre 80% y 100% */
        WARNING,
        /** Más del 100% */
        OVER
    }

    public record CategoryBudgetRequest(
            @NotNull @DecimalMin(value = "0.01", message = "El límite debe ser mayor a 0")
            @Digits(integer = 12, fraction = 2) BigDecimal amount
    ) {}

    /**
     * Una categoría de gasto con su límite y lo gastado en el período (moneda base).
     * {@code scheduled}: recurrentes de gasto que faltan en el mes (solo mes actual).
     * {@code willExceed}: lo gastado más lo programado supera el límite.
     */
    public record CategoryBudgetItem(
            Long categoryId,
            String categoryName,
            String categoryColor,
            AllocationBucket bucket,
            BigDecimal limit,
            BigDecimal spent,
            BigDecimal scheduled,
            BigDecimal percentage,
            BudgetStatus status,
            boolean willExceed
    ) {}

    public record CategoryBudgetSummary(
            int year,
            int month,
            String baseCurrency,
            /** Suma de los límites definidos */
            BigDecimal totalLimit,
            /** Gastado en las categorías con límite */
            BigDecimal totalSpent,
            List<CategoryBudgetItem> items
    ) {}
}
