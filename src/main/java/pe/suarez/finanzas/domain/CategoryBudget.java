package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Límite mensual de gasto de una categoría, en moneda base. */
@Entity
@Table(name = "category_budgets", uniqueConstraints =
        @UniqueConstraint(name = "uk_category_budget", columnNames = {"user_id", "category_id"}))
public class CategoryBudget extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
