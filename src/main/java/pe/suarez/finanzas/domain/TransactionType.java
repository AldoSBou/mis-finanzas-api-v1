package pe.suarez.finanzas.domain;

/**
 * Tipo de movimiento financiero.
 * INCOME: dinero que ingresa (sueldo, freelance, intereses, etc.)
 * EXPENSE: dinero que sale (gastos en general)
 * TRANSFER: dinero que se mueve entre dos cuentas propias (no es ingreso ni gasto)
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
