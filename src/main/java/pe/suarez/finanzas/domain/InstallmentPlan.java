package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compra en cuotas. El gasto ya está registrado completo en su movimiento; el plan indica
 * cuánto se factura cada mes a partir de {@link #firstPeriod}.
 */
@Entity
@Table(name = "installment_plans")
public class InstallmentPlan extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "transaction_id", nullable = false, unique = true)
    public Long transactionId;

    @Column(nullable = false)
    public int installments;

    @Column(name = "installment_amount", nullable = false, precision = 14, scale = 2)
    public BigDecimal installmentAmount;

    /** Mes (YYYY-MM) de la primera cuota */
    @Column(name = "first_period", nullable = false, length = 7)
    public String firstPeriod;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
