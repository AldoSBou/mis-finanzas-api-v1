package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TransactionDtos {

    private TransactionDtos() {}

    /**
     * La moneda no se envía: es la de la cuenta.
     * <ul>
     *   <li>INCOME / EXPENSE: requieren {@code categoryId}.</li>
     *   <li>TRANSFER: requiere {@code toAccountId}; {@code toAmount} solo si las monedas difieren.</li>
     *   <li>{@code exchangeRate}: requerido si la cuenta no está en la moneda base
     *       (salvo transferencias hacia la moneda base, donde se deduce de los montos).</li>
     * </ul>
     */
    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull Long accountId,
            Long categoryId,
            Long toAccountId,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0") BigDecimal amount,
            @DecimalMin(value = "0.01", message = "El monto recibido debe ser mayor a 0") BigDecimal toAmount,
            @DecimalMin(value = "0.000001", message = "El tipo de cambio debe ser mayor a 0") BigDecimal exchangeRate,
            @NotNull @PastOrPresent LocalDate transactionDate,
            @Size(max = 200) String description,
            @Size(max = 40) String paymentMethod
    ) {}

    public record TransactionResponse(
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
            String toCurrency,
            BigDecimal exchangeRate,
            BigDecimal amountBase,
            LocalDate transactionDate,
            String description,
            String paymentMethod,
            Long recurringId,
            Instant createdAt
    ) {}

    public record TransactionPage(
            List<TransactionResponse> items,
            long total,
            int page,
            int size
    ) {}

    /** Último tipo de cambio que el usuario usó para una moneda (para prellenar formularios). */
    public record ExchangeRateResponse(
            String currency,
            String baseCurrency,
            BigDecimal rate,
            LocalDate date
    ) {}
}
