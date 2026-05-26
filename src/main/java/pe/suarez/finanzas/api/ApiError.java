package pe.suarez.finanzas.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Representa un error en la respuesta API.
 *
 * @param code     código semántico del error (ver {@link ErrorCode})
 * @param message  mensaje user-friendly listo para mostrar al usuario
 * @param cause    explicación técnica de la causa (útil para debugging y documentación)
 * @param details  lista opcional de errores de campo (típicamente para VALIDATION_ERROR)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        String cause,
        List<FieldError> details
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(ErrorCode code) {
        var entry = ErrorCatalog.get(code);
        return new ApiError(code.name(), entry.message(), entry.cause(), null);
    }

    public static ApiError of(ErrorCode code, String overrideMessage) {
        var entry = ErrorCatalog.get(code);
        return new ApiError(code.name(),
                overrideMessage != null ? overrideMessage : entry.message(),
                entry.cause(), null);
    }

    public static ApiError of(ErrorCode code, String overrideMessage, List<FieldError> details) {
        var entry = ErrorCatalog.get(code);
        return new ApiError(code.name(),
                overrideMessage != null ? overrideMessage : entry.message(),
                entry.cause(), details);
    }
}
