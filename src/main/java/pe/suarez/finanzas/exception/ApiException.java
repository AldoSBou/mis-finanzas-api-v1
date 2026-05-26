package pe.suarez.finanzas.exception;

import pe.suarez.finanzas.api.ErrorCatalog;
import pe.suarez.finanzas.api.ErrorCode;

/**
 * Excepción base que lleva un {@link ErrorCode}.
 * El mapper la traduce a una respuesta API estandarizada.
 *
 * <p>Si {@link #getMessage()} es null, se usará el mensaje del catálogo.
 * Los servicios pueden lanzar con un mensaje custom cuando quieran agregar contexto:
 * <pre>{@code
 *   throw new ApiException(ErrorCode.TRANSACTION_TYPE_MISMATCH,
 *       "El movimiento es INCOME pero la categoría 'Alimentación' es EXPENSE");
 * }</pre>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(ErrorCatalog.message(code));
        this.code = code;
    }

    public ApiException(ErrorCode code, String overrideMessage) {
        super(overrideMessage);
        this.code = code;
    }

    public ApiException(ErrorCode code, String overrideMessage, Throwable cause) {
        super(overrideMessage, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
