package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Meta de ahorro. Su moneda es la de {@link #accountId}; lo ahorrado es la suma de sus aportes. */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "account_id", nullable = false)
    public Long accountId;

    @Column(nullable = false, length = 60)
    public String name;

    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2)
    public BigDecimal targetAmount;

    @Column(name = "target_date")
    public LocalDate targetDate;

    @Column(length = 7)
    public String color;

    @Column(nullable = false)
    public boolean archived = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
