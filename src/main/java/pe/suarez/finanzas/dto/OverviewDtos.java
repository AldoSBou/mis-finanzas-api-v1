package pe.suarez.finanzas.dto;

import pe.suarez.finanzas.dto.CategoryBudgetDtos.CategoryBudgetItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OverviewDtos {

    private OverviewDtos() {}

    /** Algo pendiente que se resta de "te quedan": un compromiso o un recurrente por venir. */
    public record PendingItem(
            /** COMMITMENT o RECURRING */
            String kind,
            Long id,
            String name,
            String categoryName,
            BigDecimal amount,
            LocalDate date,
            boolean overdue,
            /** Recurrentes: se registra solo en la fecha. Compromisos: siempre false. */
            boolean automatic,
            /** Transferencia hacia ahorro o hacia una cuenta propia (no es gasto) */
            boolean transfer
    ) {}

    /**
     * "¿Cuánto me queda?" para el periodo en curso. Montos en moneda base.
     * remaining = expectedIncome − spent − savingsDone − savingsPending − pendingTotal − commitmentsPaid.
     */
    public record OverviewResponse(
            LocalDate today,
            LocalDate periodStart,
            LocalDate periodEnd,
            /** Días que faltan, contando hoy */
            int daysLeft,
            /** Día de inicio configurado (null = mes calendario) */
            Integer periodStartDay,
            String baseCurrency,
            /** Ingresos registrados en el periodo */
            BigDecimal income,
            /** Lo que se espera recibir: registrado + recurrentes de ingreso por venir, o el ingreso esperado configurado si es mayor */
            BigDecimal expectedIncome,
            /** Gastos registrados (consumo, sin ahorro) */
            BigDecimal spent,
            /** Transferido a ahorro/inversión en el periodo */
            BigDecimal savingsDone,
            /** Ahorro que falta: recurrentes hacia ahorro por venir y lo que falta para la meta de la regla activa */
            BigDecimal savingsPending,
            /** Compromisos pendientes + gastos recurrentes por venir */
            BigDecimal pendingTotal,
            /** Compromisos pagados en el periodo con "mover dinero" (los pagados como gasto ya están en spent) */
            BigDecimal commitmentsPaid,
            BigDecimal remaining,
            /** remaining / daysLeft si es positivo; null si no */
            BigDecimal perDay,
            List<PendingItem> pending,
            /** Categorías de gasto: con límite primero (más consumidas arriba) */
            List<CategoryBudgetItem> categories
    ) {}
}
