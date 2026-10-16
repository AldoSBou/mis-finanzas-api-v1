package pe.suarez.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LiquidityDtos {

    private LiquidityDtos() {}

    /** Una cuenta líquida con su saldo en su propia moneda. */
    public record LiquidAccount(
            Long id,
            String name,
            String currency,
            BigDecimal balance
    ) {}

    /** Entrada o salida de efectivo prevista en una fecha. {@code amount} con signo, en la moneda de la proyección. */
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
     * Proyección del efectivo en una moneda. Nada se convierte: solo entra lo que está en esa
     * moneda (cuentas, recurrentes, compromisos y pagos de tarjeta). Lo que cruza de moneda
     * (una transferencia de soles a dólares) sale de una proyección y entra en la otra con los
     * montos propios de esa transferencia.
     *
     * saldo(d) = saldo de hoy + eventos con fecha <= d − gasto variable diario × días transcurridos.
     */
    public record CurrencyProjection(
            String currency,
            List<LiquidAccount> accounts,
            /** Saldo líquido de hoy (suma de accounts.balance) */
            BigDecimal opening,
            /** Gasto variable diario estimado (promedio de 28 días sin recurrentes ni compromisos, sin tarjetas) */
            BigDecimal dailyVariableEstimated,
            /** El que se usó: el estimado o el que mandó el cliente (solo para la moneda base) */
            BigDecimal dailyVariable,
            List<LiquidityEvent> events,
            List<LiquidityDay> days,
            LiquidityDay lowest,
            /** Primer día en que el saldo proyectado baja de cero, o null */
            LocalDate firstNegative,
            BigDecimal endBalance
    ) {}

    /**
     * Liquidez: el efectivo disponible día a día desde hoy hasta el fin del periodo siguiente,
     * una proyección por moneda. La base va primero; una moneda aparece si hay cuentas líquidas
     * o salidas previstas en ella (p. ej. el pago de una tarjeta en dólares).
     */
    public record LiquidityResponse(
            LocalDate today,
            LocalDate periodEnd,
            LocalDate horizonEnd,
            String baseCurrency,
            List<CurrencyProjection> currencies
    ) {}
}
