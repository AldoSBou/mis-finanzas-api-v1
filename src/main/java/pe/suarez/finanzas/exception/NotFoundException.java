package pe.suarez.finanzas.exception;

import pe.suarez.finanzas.api.ErrorCode;

/**
 * Mantenida por compatibilidad con el código existente.
 * Internamente usa {@link ApiException} con el código apropiado.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode specificCode, String message) {
        super(specificCode, message);
    }
}
