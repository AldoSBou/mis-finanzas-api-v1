package pe.suarez.finanzas.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Metadatos de la respuesta API.
 *
 * @param timestamp instante en que se generó la respuesta
 * @param requestId identificador único de la request (para correlacionar logs y soporte)
 * @param path      ruta del endpoint (incluida solo en errores)
 * @param pagination metadatos de paginación (solo en colecciones paginadas)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Meta(
        Instant timestamp,
        String requestId,
        String path,
        Pagination pagination
) {
    public record Pagination(int page, int size, long total, int totalPages) {
        public static Pagination of(int page, int size, long total) {
            int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
            return new Pagination(page, size, total, totalPages);
        }
    }

    public static Meta now(String requestId) {
        return new Meta(Instant.now(), requestId, null, null);
    }

    public static Meta now(String requestId, String path) {
        return new Meta(Instant.now(), requestId, path, null);
    }

    public static Meta paginated(String requestId, Pagination pagination) {
        return new Meta(Instant.now(), requestId, null, pagination);
    }
}
