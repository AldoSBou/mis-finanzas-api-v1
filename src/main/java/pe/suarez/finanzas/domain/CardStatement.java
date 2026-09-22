package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Estado de cuenta de un ciclo de la tarjeta, con los montos que informa el banco. */
@Entity
@Table(name = "card_statements", uniqueConstraints =
        @UniqueConstraint(name = "uk_card_statement", columnNames = {"account_id", "closing_date"}))
public class CardStatement extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "account_id", nullable = false)
    public Long accountId;

    @Column(name = "closing_date", nullable = false)
    public LocalDate closingDate;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    /** Pago del mes (lo que hay que pagar para no generar intereses) */
    @Column(name = "total_due", nullable = false, precision = 14, scale = 2)
    public BigDecimal totalDue;

    @Column(name = "minimum_due", precision = 14, scale = 2)
    public BigDecimal minimumDue;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
