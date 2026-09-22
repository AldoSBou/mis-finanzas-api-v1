package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Aporte (+) o retiro (−) de una meta; con {@link #transactionId} si movió dinero entre cuentas. */
@Entity
@Table(name = "goal_contributions")
public class GoalContribution extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "goal_id", nullable = false)
    public Long goalId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "contribution_date", nullable = false)
    public LocalDate contributionDate;

    @Column(length = 200)
    public String note;

    @Column(name = "transaction_id")
    public Long transactionId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
