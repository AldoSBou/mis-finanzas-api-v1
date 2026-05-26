package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TransactionDtos {

    private TransactionDtos() {}

    public record TransactionRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0") BigDecimal amount,
            @NotNull TransactionType type,
            @NotNull @PastOrPresent LocalDate transactionDate,
            @Size(max = 200) String description,
            @Size(max = 40) String paymentMethod,
            @Size(min = 3, max = 3) String currency
    ) {}

    public record TransactionResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColor,
            BigDecimal amount,
            TransactionType type,
            LocalDate transactionDate,
            String description,
            String paymentMethod,
            String currency,
            Instant createdAt
    ) {}

    public record TransactionPage(
            List<TransactionResponse> items,
            long total,
            int page,
            int size
    ) {}
}
