package pe.suarez.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LiquidityDtos {

    private LiquidityDtos() {}

    /** Una cuenta líquida con su saldo, convertido a moneda base si hace falta. */
    public record LiquidAccount(
            Long id,
            String name,
            String currency,
            BigDecimal balance,
            /** En moneda base; null si no hay tipo de cambio conocido (queda fuera del cálculo) */
            BigDecimal balanceBase,
            BigDecimal rate
    ) {}

    /** Entrada o salida de efectivo prevista en una fecha. {@code amount} con signo. */
    public record LiquidityEvent(
            LocalDate date,
            /** RECURRING, COMMITMENT o CARD */
            String kind,
            Long id,
            String name,
            BigDecimal amount
    ) {}

    public record LiquidityDay(
            LocalDate date,
            BigDecimal balance
    ) {}

    /**
     * Proyección de liquidez: el efectivo disponible día a día desde hoy hasta el fin del
     * periodo siguiente. Montos en moneda base.
     *
     * saldo(d) = saldo líquido de hoy + eventos con fecha <= d − gasto variable diario × días transcurridos.
     */
    public record LiquidityResponse(
            LocalDate today,
            LocalDate periodEnd,
            LocalDate horizonEnd,
            String baseCurrency,
            List<LiquidAccount> accounts,
            /** Saldo líquido de hoy (suma de accounts.balanceBase) */
            BigDecimal opening,
            /** Gasto variable diario estimado (promedio de 28 días sin recurrentes ni compromisos, sin tarjetas) */
            BigDecimal dailyVariableEstimated,
            /** El que se usó: el estimado o el que mandó el cliente */
            BigDecimal dailyVariable,
            List<LiquidityEvent> events,
            List<LiquidityDay> days,
            LiquidityDay lowest,
            /** Primer día en que el saldo proyectado baja de cero, o null */
            LocalDate firstNegative,
            BigDecimal endBalance
    ) {}
}
