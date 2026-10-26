package pe.suarez.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Boletas leídas desde una foto: lectura (preview), confirmación y consulta. */
public final class ReceiptDtos {

    private ReceiptDtos() {}

    /** Una o dos fotos de la misma boleta (si es larga), en base64 y ya reducidas por el teléfono. */
    public record ReceiptParseRequest(
            @NotNull @Size(min = 1, max = 2) List<@NotBlank @Size(max = 4_000_000) String> images,
            @Size(max = 30) String mediaType
    ) {}

    /** Una línea leída. {@code categoryId} null = no encajó en ninguna categoría; {@code suggestedCategory} dice cuál crear. */
    public record ReceiptItem(
            String description,
            BigDecimal amount,
            Long categoryId,
            String suggestedCategory
    ) {}

    /** Líneas de una misma categoría: será un movimiento. */
    public record ReceiptGroup(
            Long categoryId,
            String categoryName,
            /** Nombre de categoría propuesto cuando categoryId es null */
            String suggestedCategory,
            BigDecimal amount,
            List<ReceiptItem> items
    ) {}

    /** Lo leído de la foto, listo para que la persona confirme. */
    public record ReceiptPreview(
            String merchant,
            /** Fecha de la boleta; null si no se ve */
            LocalDate date,
            BigDecimal total,
            /** Moneda de la boleta; la base del usuario si no se distingue */
            String currency,
            List<ReceiptGroup> groups
    ) {}

    public record ReceiptItemRequest(
            @Size(max = 120) String description,
            BigDecimal amount
    ) {}

    public record ReceiptGroupRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01", message = "Cada grupo debe ser mayor a 0") BigDecimal amount,
            @Size(max = 200) List<@Valid ReceiptItemRequest> items
    ) {}

    /** Confirmación: la cuenta con la que se pagó y los grupos, que deben sumar el total. */
    public record ReceiptCreateRequest(
            @NotNull Long accountId,
            @Size(max = 120) String merchant,
            @NotNull @PastOrPresent LocalDate date,
            @NotNull @DecimalMin(value = "0.01") BigDecimal total,
            /** Requerido si la cuenta no está en la moneda base */
            @DecimalMin(value = "0.000001") BigDecimal exchangeRate,
            @NotNull @Size(min = 1, max = 30) List<@Valid ReceiptGroupRequest> groups
    ) {}

    public record ReceiptResponse(
            Long id,
            String merchant,
            LocalDate date,
            BigDecimal total,
            String currency,
            Long accountId,
            List<ReceiptItem> items,
            List<TransactionResponse> transactions
    ) {}

    // ----- Esquema que devuelve el modelo (salida estructurada) -----

    public record ParsedItem(
            @JsonPropertyDescription("Nombre del producto tal como aparece, acortado si es muy largo (máx. 60 caracteres).")
            String description,
            @JsonPropertyDescription("Importe de la línea con decimales. Negativo si es un descuento. null si no se lee.")
            Double amount,
            @JsonPropertyDescription("id de la categoría de gasto de la persona que mejor encaja con este producto, o null si ninguna encaja de verdad.")
            Long categoryId,
            @JsonPropertyDescription("Solo si categoryId es null: nombre corto de la categoría que haría falta (p. ej. 'Limpieza'). Si no, null.")
            String suggestedCategory
    ) {}

    public record ParsedReceipt(
            @JsonPropertyDescription("Nombre del comercio (p. ej. 'Tottus', 'Tambo', 'Plaza Vea'), sin razón social ni RUC. null si no se ve.")
            String merchant,
            @JsonPropertyDescription("Fecha de la boleta en formato YYYY-MM-DD, o null si no se ve.")
            String date,
            @JsonPropertyDescription("Importe TOTAL pagado, el último de la boleta (no subtotal, no vuelto, no efectivo entregado). null si no se lee.")
            Double total,
            @JsonPropertyDescription("Moneda en código ISO: PEN si es S/ o soles, USD si es $ o US$. null si no se distingue.")
            String currency,
            @JsonPropertyDescription("Todas las líneas de producto de la boleta en orden. Si la imagen es un voucher o captura sin detalle de productos, una sola línea con el total.")
            List<ParsedItem> items
    ) {}
}
