package pe.suarez.finanzas.api;

public enum ErrorCode {
    // Genéricos
    VALIDATION_ERROR(400),
    BUSINESS_ERROR(400),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    CONFLICT(409),
    PAYLOAD_TOO_LARGE(413),
    UNSUPPORTED_MEDIA_TYPE(415),
    RATE_LIMIT_EXCEEDED(429),
    INTERNAL_ERROR(500),

    // Auth específicos
    AUTH_INVALID_CREDENTIALS(401),
    AUTH_EMAIL_ALREADY_EXISTS(409),
    AUTH_TOKEN_EXPIRED(401),
    AUTH_TOKEN_INVALID(401),
    AUTH_TOKEN_REVOKED(401),               // NUEVO: token en denylist
    AUTH_ACCOUNT_TEMPORARILY_LOCKED(429),  // NUEVO: lockout por intentos fallidos
    AUTH_PASSWORD_TOO_WEAK(400),           // NUEVO: para política de password

    // Categorías
    CATEGORY_NOT_FOUND(404),

    // Cuentas
    ACCOUNT_NOT_FOUND(404),
    ACCOUNT_ARCHIVED(400),
    ACCOUNT_CURRENCY_LOCKED(400),
    CURRENCY_INVALID(400),

    // Movimientos
    TRANSACTION_NOT_FOUND(404),
    TRANSACTION_TYPE_MISMATCH(400),
    TRANSACTION_CATEGORY_REQUIRED(400),
    TRANSFER_INVALID(400),
    EXCHANGE_RATE_REQUIRED(400),

    // Reglas
    RULE_NOT_FOUND(404),
    RULE_TEMPLATE_LOCKED(400),
    RULE_PERCENTAGES_INVALID(400),

    // Presupuesto
    BUDGET_NOT_FOUND(404),

    // Período
    PERIOD_INVALID(400);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
