package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Dinero que la persona va a tener que soltar en una fecha (alquiler, colegio, una deuda,
 * la deuda con la que llega una tarjeta). Se resta de "te quedan" hasta que se paga.
 */
@Entity
@Table(name = "commitments")
public class Commitment extends PanacheEntityBase {

    public enum Status { PENDING, PAID, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 80)
    public String name;

    /** En la moneda base del usuario */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    /** Gasto que se registra al pagar; nula si el pago es hacia una cuenta propia */
    @Column(name = "category_id")
    public Long categoryId;

    /** Cuenta sugerida desde donde se paga */
    @Column(name = "account_id")
    public Long accountId;

    /** Deuda de tarjeta: el pago es "mover dinero" hacia esta cuenta, no un gasto */
    @Column(name = "to_account_id")
    public Long toAccountId;

    @Column(length = 200)
    public String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Status status = Status.PENDING;

    @Column(name = "paid_transaction_id")
    public Long paidTransactionId;

    @Column(name = "paid_at")
    public LocalDate paidAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
