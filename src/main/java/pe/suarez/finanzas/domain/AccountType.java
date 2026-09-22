package pe.suarez.finanzas.domain;

/**
 * Tipo de cuenta donde vive el dinero.
 * SAVINGS e INVESTMENT son cuentas de "ahorro": transferir dinero hacia ellas
 * cuenta como ahorro del mes en el dashboard, no como gasto.
 */
public enum AccountType {
    CASH,           // Efectivo
    BANK,           // Cuenta bancaria / débito
    CREDIT_CARD,    // Tarjeta de crédito (saldo negativo = deuda)
    EWALLET,        // Billetera digital (Yape, Plin)
    SAVINGS,        // Cuenta de ahorro / fondo de emergencia
    INVESTMENT;     // Inversiones (fondos mutuos, acciones, depósitos a plazo)

    /** Bucket de ahorro al que aporta esta cuenta, o null si es de gasto corriente. */
    public AllocationBucket savingsBucket() {
        return switch (this) {
            case SAVINGS -> AllocationBucket.SAVINGS;
            case INVESTMENT -> AllocationBucket.INVESTMENT;
            default -> null;
        };
    }
}
