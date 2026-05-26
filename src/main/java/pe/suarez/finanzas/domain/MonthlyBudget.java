package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "monthly_budgets", uniqueConstraints =
        @UniqueConstraint(name = "uk_budget_user_period", columnNames = {"user_id", "year", "month"}))
public class MonthlyBudget extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Integer year;

    /** Mes 1..12 */
    @Column(nullable = false)
    public Integer month;

    @Column(name = "expected_income", nullable = false, precision = 14, scale = 2)
    public BigDecimal expectedIncome;

    @Column(name = "active_rule_id")
    public Long activeRuleId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
