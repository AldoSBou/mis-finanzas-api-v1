package pe.suarez.finanzas.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CashflowDtos {

    private CashflowDtos() {}

    /** Una fila de la grilla: categoría (ingresos o gastos) o cuenta de ahorro, con un monto por mes. */
    public record CashflowRow(
            Long id,
            String name,
            String color,
            /** Alineado con {@code periods} */
            List<BigDecimal> monthly,
            BigDecimal total
    ) {}

    /**
     * Flujo de caja de 1 a 6 meses, en moneda base. Solo lo registrado (no proyecciones).
     * {@code net = income − expenses − savings}; {@code cumulative} acumula el neto desde el primer mes.
     */
    public record CashflowResponse(
            String baseCurrency,
            /** Meses YYYY-MM, del más antiguo al más reciente */
            List<String> periods,
            List<CashflowRow> income,
            List<CashflowRow> expenses,
            /** Aportes netos a cuentas de ahorro e inversión, por cuenta */
            List<CashflowRow> savings,
            List<BigDecimal> totalIncome,
            List<BigDecimal> totalExpenses,
            List<BigDecimal> totalSavings,
            List<BigDecimal> net,
            List<BigDecimal> cumulative
    ) {}
}
