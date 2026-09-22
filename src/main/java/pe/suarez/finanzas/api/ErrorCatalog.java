package pe.suarez.finanzas.api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ErrorCatalog {

    private ErrorCatalog() {}

    public record Entry(String message, String cause) {}

    private static final Map<ErrorCode, Entry> ENTRIES = new EnumMap<>(ErrorCode.class);

    static {
        // Genéricos
        register(ErrorCode.VALIDATION_ERROR,
                "Uno o más campos no pasaron la validación",
                "Los datos enviados no cumplen las restricciones definidas (campos requeridos, formato, longitud, etc.)");
        register(ErrorCode.BUSINESS_ERROR,
                "Operación no permitida por reglas de negocio",
                "Una regla de negocio impide completar la operación. Revisa el detalle del error.");
        register(ErrorCode.BAD_REQUEST,
                "Solicitud inválida",
                "La solicitud no pudo procesarse por estar mal formada o contener parámetros inválidos.");
        register(ErrorCode.UNAUTHORIZED,
                "No autorizado",
                "La solicitud no incluye credenciales válidas. Inicia sesión nuevamente.");
        register(ErrorCode.FORBIDDEN,
                "Acceso denegado",
                "Estás autenticado pero no tienes permisos para acceder a este recurso.");
        register(ErrorCode.NOT_FOUND,
                "Recurso no encontrado",
                "El recurso solicitado no existe o ya fue eliminado.");
        register(ErrorCode.METHOD_NOT_ALLOWED,
                "Método HTTP no permitido",
                "El método HTTP usado no está soportado para este recurso.");
        register(ErrorCode.CONFLICT,
                "Conflicto con el estado actual del recurso",
                "La operación no puede completarse por un conflicto, generalmente por un valor duplicado.");
        register(ErrorCode.PAYLOAD_TOO_LARGE,
                "El cuerpo de la solicitud excede el tamaño máximo permitido",
                "El servidor limita el tamaño del request body a 256 KB. Reduce el tamaño del payload.");
        register(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de contenido no soportado",
                "El tipo de contenido enviado no es procesable. Usa application/json.");
        register(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Demasiadas solicitudes. Intenta más tarde.",
                "Has excedido el número permitido de solicitudes en una ventana de tiempo. Espera unos minutos antes de reintentar.");
        register(ErrorCode.INTERNAL_ERROR,
                "Ocurrió un error inesperado",
                "Error interno del servidor. Si persiste, contacta al equipo con el requestId.");

        // Auth
        register(ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Email o contraseña incorrectos",
                "Las credenciales no coinciden con ningún usuario activo.");
        register(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS,
                "El email ya está registrado",
                "Ya existe un usuario con ese email. Intenta iniciar sesión o usa otro email.");
        register(ErrorCode.AUTH_TOKEN_EXPIRED,
                "Tu sesión expiró",
                "El token JWT ha expirado. Inicia sesión nuevamente.");
        register(ErrorCode.AUTH_TOKEN_INVALID,
                "Token inválido",
                "El token JWT no es válido o fue alterado.");
        register(ErrorCode.AUTH_TOKEN_REVOKED,
                "Tu sesión ha sido cerrada",
                "Este token fue invalidado mediante logout. Inicia sesión nuevamente.");
        register(ErrorCode.AUTH_ACCOUNT_TEMPORARILY_LOCKED,
                "Cuenta bloqueada temporalmente por múltiples intentos fallidos",
                "Tras superar el límite de intentos fallidos, la cuenta queda bloqueada 15 minutos. Espera o usa la opción de recuperar contraseña.");
        register(ErrorCode.AUTH_PASSWORD_TOO_WEAK,
                "La contraseña no cumple con los requisitos mínimos",
                "La contraseña debe tener al menos 12 caracteres y no estar entre las contraseñas más comunes filtradas en internet.");

        // Categorías
        register(ErrorCode.CATEGORY_NOT_FOUND,
                "Categoría no encontrada",
                "La categoría especificada no existe o no pertenece al usuario actual.");

        // Cuentas
        register(ErrorCode.ACCOUNT_NOT_FOUND,
                "Cuenta no encontrada",
                "La cuenta especificada no existe o no pertenece al usuario actual.");
        register(ErrorCode.ACCOUNT_ARCHIVED,
                "La cuenta está archivada",
                "No se pueden registrar movimientos nuevos en una cuenta archivada.");
        register(ErrorCode.ACCOUNT_CURRENCY_LOCKED,
                "No se puede cambiar la moneda de la cuenta",
                "La cuenta ya tiene movimientos registrados en su moneda actual. Crea una cuenta nueva para la otra moneda.");
        register(ErrorCode.CURRENCY_INVALID,
                "Moneda inválida",
                "La moneda debe ser un código ISO 4217 válido de tres letras (ej. PEN, USD, EUR).");

        // Movimientos
        register(ErrorCode.TRANSACTION_NOT_FOUND,
                "Movimiento no encontrado",
                "El movimiento especificado no existe o no pertenece al usuario actual.");
        register(ErrorCode.TRANSACTION_TYPE_MISMATCH,
                "El tipo del movimiento no coincide con la categoría",
                "Se intentó crear/actualizar un movimiento de tipo distinto al de su categoría (ej. INCOME en categoría EXPENSE).");
        register(ErrorCode.TRANSACTION_CATEGORY_REQUIRED,
                "La categoría es obligatoria",
                "Los ingresos y gastos requieren una categoría. Solo las transferencias no la llevan.");
        register(ErrorCode.TRANSFER_INVALID,
                "Transferencia inválida",
                "Una transferencia necesita una cuenta destino distinta a la de origen y, si las monedas difieren, el monto recibido.");
        register(ErrorCode.EXCHANGE_RATE_REQUIRED,
                "Falta el tipo de cambio",
                "El movimiento está en una moneda distinta a tu moneda base. Indica el tipo de cambio para convertirlo.");

        // Reglas
        register(ErrorCode.RULE_NOT_FOUND,
                "Regla de asignación no encontrada",
                "La regla especificada no existe o no pertenece al usuario actual.");
        register(ErrorCode.RULE_TEMPLATE_LOCKED,
                "Las reglas plantilla no se pueden modificar",
                "Se intentó editar o eliminar una regla marcada como plantilla. Crea una copia personalizada.");
        register(ErrorCode.RULE_PERCENTAGES_INVALID,
                "Los porcentajes de la regla son inválidos",
                "Los porcentajes deben sumar 100, cada uno entre 0 y 100, y los buckets deben ser válidos.");

        // Presupuesto
        register(ErrorCode.BUDGET_NOT_FOUND,
                "Presupuesto no encontrado",
                "No hay presupuesto configurado para el período solicitado.");

        // Período
        register(ErrorCode.PERIOD_INVALID,
                "Período inválido",
                "El período debe estar en formato YYYY-MM (ej. 2026-04).");
    }

    private static void register(ErrorCode code, String message, String cause) {
        ENTRIES.put(code, new Entry(message, cause));
    }

    public static Entry get(ErrorCode code) {
        return ENTRIES.getOrDefault(code,
                new Entry("Error no documentado", "Sin descripción registrada en el catálogo"));
    }

    public static String message(ErrorCode code) {
        return get(code).message();
    }

    public static String cause(ErrorCode code) {
        return get(code).cause();
    }

    public static Map<ErrorCode, Entry> all() {
        return Collections.unmodifiableMap(ENTRIES);
    }
}
