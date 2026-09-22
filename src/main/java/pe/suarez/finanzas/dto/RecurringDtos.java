package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.Frequency;
import pe.suarez.finanzas.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecurringDtos {

    private RecurringDtos() {}

    /**
     * Mismas reglas que un movimiento (ver {@link TransactionDtos.TransactionRequest}).
     * {@code startDate} es la primera ocurrencia: si ya pasó y {@code autoCreate} es true,
     * se registran las ocurrencias vencidas al guardar.
     */
    public record RecurringRequest(
            @NotNull TransactionType type,
            @NotNull Long accountId,
            Long categoryId,
            Long toAccountId,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0") BigDecimal amount,
            @DecimalMin(value = "0.01", message = "El monto recibido debe ser mayor a 0") BigDecimal toAmount,
            @DecimalMin(value = "0.000001", message = "El tipo de cambio debe ser mayor a 0") BigDecimal exchangeRate,
            @Size(max = 200) String description,
            @NotNull Frequency frequency,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            boolean autoCreate
    ) {}

    public record RecurringResponse(
            Long id,
            TransactionType type,
            Long accountId,
            String accountName,
            Long toAccountId,
            String toAccountName,
            Long categoryId,
            String categoryName,
            String categoryColor,
            BigDecimal amount,
            String currency,
            BigDecimal toAmount,
            BigDecimal exchangeRate,
            String description,
            Frequency frequency,
            LocalDate nextDate,
            LocalDate endDate,
            boolean autoCreate,
            boolean active
    ) {}

    /** Registrar la ocurrencia pendiente. Todo es opcional: por defecto, monto del recurrente y fecha de hoy. */
    public record RegisterOccurrenceRequest(
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0") BigDecimal amount,
            @DecimalMin(value = "0.000001", message = "El tipo de cambio debe ser mayor a 0") BigDecimal exchangeRate,
            @PastOrPresent LocalDate date
    ) {}

    /** Ocurrencia futura o pendiente, para el panel. */
    public record UpcomingItem(
            Long recurringId,
            TransactionType type,
            String description,
            String categoryName,
            String accountName,
            String toAccountName,
            BigDecimal amount,
            String currency,
            BigDecimal amountBase,
            LocalDate date,
            boolean overdue,
            boolean autoCreate
    ) {}
}
