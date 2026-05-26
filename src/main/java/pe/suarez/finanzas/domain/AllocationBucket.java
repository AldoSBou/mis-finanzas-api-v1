package pe.suarez.finanzas.domain;

/**
 * Etiqueta de asignación para agrupar categorías bajo una regla.
 * Permite mapear categorías a los "cubos" de una metodología (50/30/20, 70/20/10, etc.)
 * sin acoplar las categorías del usuario a una regla específica.
 */
public enum AllocationBucket {
    NEEDS,          // Necesidades (alquiler, alimentación, transporte, servicios)
    WANTS,          // Deseos (ocio, suscripciones, restaurantes)
    SAVINGS,        // Ahorro
    INVESTMENT,     // Inversión (separada de ahorro para quienes lo distinguen)
    DEBT,           // Pago de deudas
    UNCATEGORIZED   // Sin asignar a ningún cubo
}
