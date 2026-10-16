package pe.suarez.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class VoiceDtos {

    private VoiceDtos() {}

    /** Lo que la persona dijo, ya transcrito por el teléfono. */
    public record VoiceParseRequest(
            @NotBlank @Size(max = 300) String text
    ) {}

    /** Foto o captura (JPEG/PNG/WEBP) en base64. El teléfono la reduce antes de mandarla (máx. ~1 MB). */
    public record ImageParseRequest(
            @NotBlank @Size(max = 4_000_000) String imageBase64,
            @Size(max = 30) String mediaType
    ) {}

    /** Respuesta al teléfono. {@code amount}, {@code categoryId} y {@code date} pueden ser null si no se entendieron. */
    public record VoiceParseResponse(
            BigDecimal amount,
            /** Moneda del comprobante (PEN, USD...). Si no se distingue, la moneda base del usuario. */
            String currency,
            String type,
            Long categoryId,
            String categoryName,
            String description,
            /** Fecha del comprobante (fotos); null si no se ve o es la de hoy */
            LocalDate date
    ) {}

    /** Esquema que debe devolver el modelo (salida estructurada). */
    public record Parsed(
            @JsonPropertyDescription("Monto tal como aparece, con decimales, sin convertir de moneda. null si no se menciona un monto.")
            Double amount,
            @JsonPropertyDescription("Moneda del monto en código ISO: PEN si dice S/ o soles; USD si dice $, US$ o dólares; EUR si dice €. null si no se distingue.")
            String currency,
            @JsonPropertyDescription("EXPENSE si la persona gastó o pagó; INCOME si recibió dinero.")
            String type,
            @JsonPropertyDescription("id de la categoría más probable de la lista dada, o null si ninguna encaja.")
            Long categoryId,
            @JsonPropertyDescription("Descripción corta (máx. 60 caracteres) de en qué fue o a quién se pagó, sin el monto ni el nombre de la categoría. Vacía si no aporta nada.")
            String description,
            @JsonPropertyDescription("Fecha del comprobante en formato YYYY-MM-DD si se ve en la imagen; null si no se ve o si es una frase de voz.")
            String date
    ) {}
}
