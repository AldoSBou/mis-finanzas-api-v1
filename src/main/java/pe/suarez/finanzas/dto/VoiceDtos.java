package pe.suarez.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class VoiceDtos {

    private VoiceDtos() {}

    /** Lo que la persona dijo, ya transcrito por el teléfono. */
    public record VoiceParseRequest(
            @NotBlank @Size(max = 300) String text
    ) {}

    /** Respuesta al teléfono. {@code amount} y {@code categoryId} pueden ser null si no se entendieron. */
    public record VoiceParseResponse(
            BigDecimal amount,
            String type,
            Long categoryId,
            String categoryName,
            String description
    ) {}

    /** Esquema que debe devolver el modelo (salida estructurada). */
    public record Parsed(
            @JsonPropertyDescription("Monto en la moneda del usuario, con decimales. null si no se menciona un monto.")
            Double amount,
            @JsonPropertyDescription("EXPENSE si la persona gastó o pagó; INCOME si recibió dinero.")
            String type,
            @JsonPropertyDescription("id de la categoría más probable de la lista dada, o null si ninguna encaja.")
            Long categoryId,
            @JsonPropertyDescription("Descripción corta (máx. 60 caracteres) de en qué fue, sin el monto ni el nombre de la categoría. Vacía si no aporta nada.")
            String description
    ) {}
}
