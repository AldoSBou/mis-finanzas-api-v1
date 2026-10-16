package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.Commitment;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CommitmentDtos {

    private CommitmentDtos() {}

    /**
     * {@code categoryId} es obligatoria salvo que el pago vaya a una cuenta propia
     * ({@code toAccountId}, p. ej. la deuda con la que llega una tarjeta).
     */
    public record CommitmentRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
            @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate dueDate,
            Long categoryId,
            Long accountId,
            Long toAccountId,
            @Size(max = 200) String note
    ) {}

    public record CommitmentResponse(
            Long id,
            String name,
            BigDecimal amount,
            String currency,
            LocalDate dueDate,
            boolean overdue,
            Long categoryId,
            String categoryName,
            Long accountId,
            String accountName,
            Long toAccountId,
            String toAccountName,
            String note,
            Commitment.Status status,
            Long paidTransactionId,
            LocalDate paidAt
    ) {}

    /** Pagar: todo opcional. Por defecto, la cuenta sugerida, el monto del compromiso y hoy. */
    public record PayRequest(
            Long accountId,
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0") BigDecimal amount,
            @DecimalMin(value = "0.000001", message = "El tipo de cambio debe ser mayor a 0") BigDecimal exchangeRate,
            @PastOrPresent LocalDate date
    ) {}
}
