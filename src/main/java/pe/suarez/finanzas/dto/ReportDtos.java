package pe.suarez.finanzas.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    /** Resumen de un mes, en moneda base. {@code net = income - expenses - savings}. */
    public record MonthSummary(
            String period,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal net,
            /** Suma de saldos de todas las cuentas al cierre del mes */
            BigDecimal netWorth
    ) {}

    /** Gasto mensual de una categoría; {@code categoryId} null = "Otros". */
    public record CategorySeries(
            Long categoryId,
            String name,
            BigDecimal total,
            List<BigDecimal> monthly
    ) {}

    public record ReportResponse(
            String baseCurrency,
            List<MonthSummary> months,
            /** Top categorías de consumo del período (más "Otros"), alineadas con {@code months} */
            List<CategorySeries> categories,
            /**
             * true si el patrimonio incluye cuentas en otra moneda convertidas con el último
             * tipo de cambio conocido (no el de cada mes)
             */
            boolean netWorthApproximate
    ) {}
}
