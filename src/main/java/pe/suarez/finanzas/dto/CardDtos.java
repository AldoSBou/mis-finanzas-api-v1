package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CardDtos {

    private CardDtos() {}

    public enum StatementStatus {
        /** Pagado el pago del mes */
        PAID,
        /** Falta pagar y aún no vence */
        PENDING,
        /** Venció y se pagó al menos el mínimo (genera intereses) */
        MINIMUM_PAID,
        /** Venció sin cubrir el mínimo */
        OVERDUE
    }

    public record StatementRequest(
            @NotNull LocalDate closingDate,
            @NotNull LocalDate dueDate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal totalDue,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal minimumDue
    ) {}

    /** {@code paid}: lo que entró a la tarjeta después del cierre (pagos y transferencias). */
    public record StatementResponse(
            Long id,
            LocalDate closingDate,
            LocalDate dueDate,
            BigDecimal totalDue,
            BigDecimal minimumDue,
            BigDecimal paid,
            BigDecimal remaining,
            StatementStatus status,
            /** Días hasta el vencimiento (negativo si ya venció) */
            long daysLeft
    ) {}

    public record InstallmentRequest(
            @NotNull @Min(value = 2, message = "Mínimo 2 cuotas") @Max(value = 60, message = "Máximo 60 cuotas") Integer installments,
            /** Por defecto: monto / cuotas. Otro valor si la cuota incluye intereses */
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal installmentAmount,
            /** Por defecto: el mes siguiente a la compra */
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "Usa el formato AAAA-MM") String firstPeriod
    ) {}

    public record InstallmentResponse(
            Long transactionId,
            String description,
            LocalDate purchaseDate,
            BigDecimal total,
            int installments,
            BigDecimal installmentAmount,
            String firstPeriod,
            String lastPeriod,
            /** Cuotas ya facturadas */
            int charged,
            BigDecimal remainingAmount,
            boolean finished
    ) {}

    public record CardSummary(
            Long accountId,
            String name,
            String currency,
            String color,
            /** Deuda actual (positivo = debes) */
            BigDecimal debt,
            BigDecimal creditLimit,
            BigDecimal available,
            /** % de la línea usado */
            BigDecimal utilization,
            Integer statementDay,
            Integer dueDay,
            LocalDate nextClosingDate,
            LocalDate nextDueDate,
            StatementResponse latestStatement,
            int activeInstallments,
            /** Lo que falta facturar de compras en cuotas */
            BigDecimal installmentsRemaining,
            /** Cuotas que se facturan este mes */
            BigDecimal installmentsThisMonth
    ) {}

    public record CardDetail(
            CardSummary summary,
            List<StatementResponse> statements,
            List<InstallmentResponse> installments
    ) {}
}
