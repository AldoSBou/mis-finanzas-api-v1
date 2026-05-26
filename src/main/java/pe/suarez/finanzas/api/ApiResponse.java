package pe.suarez.finanzas.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sobre estandarizado para todas las respuestas API.
 *
 * <p>Estructura de éxito:
 * <pre>{@code
 * { "success": true, "data": {...}, "message": "...", "meta": {...} }
 * }</pre>
 *
 * <p>Estructura de error:
 * <pre>{@code
 * { "success": false, "data": null, "error": {...}, "meta": {...} }
 * }</pre>
 *
 * @param success true si la operación fue exitosa
 * @param data    el payload (DTO, lista, etc.) — null en errores
 * @param message mensaje informativo para casos de éxito (ej. "Movimiento creado")
 * @param error   detalle del error — null en éxito
 * @param meta    metadatos (timestamp, requestId, paginación)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        ApiError error,
        Meta meta
) {
    // -------- Factory methods de éxito --------

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message, Meta meta) {
        return new ApiResponse<>(true, data, message, null, meta);
    }

    public static <T> ApiResponse<T> paginated(T data, Meta meta) {
        return new ApiResponse<>(true, data, null, null, meta);
    }

    // -------- Factory methods de error --------

    public static <T> ApiResponse<T> error(ApiError error, Meta meta) {
        return new ApiResponse<>(false, null, null, error, meta);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, Meta meta) {
        return new ApiResponse<>(false, null, null, ApiError.of(code), meta);
    }
}
