package pe.suarez.finanzas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ImportDtos {

    private ImportDtos() {}

    /** Máximo de filas por importación. */
    public static final int MAX_ROWS = 1000;

    // ---------- Reglas de categorización ----------

    public record RuleRequest(
            @NotBlank @Size(max = 80) String pattern,
            @NotNull Long categoryId
    ) {}

    public record RuleResponse(
            Long id,
            String pattern,
            Long categoryId,
            String categoryName
    ) {}

    // ---------- Vista previa ----------

    /** Fila ya interpretada por la web. {@code amount} con signo: negativo = sale dinero de la cuenta. */
    public record ImportRow(
            @NotNull LocalDate date,
            @Size(max = 200) String description,
            @NotNull BigDecimal amount
    ) {}

    public record PreviewRequest(
            @NotNull Long accountId,
            @NotNull @Size(min = 1, max = MAX_ROWS) List<@Valid ImportRow> rows
    ) {}

    public enum SuggestionSource { RULE, HISTORY, NONE }

    public record PreviewRow(
            int index,
            TransactionType type,
            Long suggestedCategoryId,
            SuggestionSource source,
            /** Ya existe un movimiento en la cuenta con la misma fecha, monto y tipo */
            boolean duplicate
    ) {}

    public record PreviewResponse(List<PreviewRow> rows) {}

    // ---------- Importación ----------

    /**
     * Fila a importar: con {@code categoryId} (ingreso o gasto según el signo) o con
     * {@code transferAccountId} (transferencia hacia/desde otra cuenta propia).
     */
    public record CommitRow(
            @NotNull LocalDate date,
            @Size(max = 200) String description,
            @NotNull BigDecimal amount,
            Long categoryId,
            Long transferAccountId
    ) {}

    public record CommitRequest(
            @NotNull Long accountId,
            @Size(max = 200) String fileName,
            /** Solo si la cuenta no está en la moneda base */
            @DecimalMin(value = "0.000001") BigDecimal exchangeRate,
            @NotNull @Size(min = 1, max = MAX_ROWS) List<@Valid CommitRow> rows
    ) {}

    public record ImportBatchResponse(
            Long id,
            Long accountId,
            String accountName,
            String fileName,
            int rowCount,
            /** Movimientos que siguen existiendo (el usuario pudo borrar algunos) */
            long remaining,
            Instant createdAt
    ) {}
}
