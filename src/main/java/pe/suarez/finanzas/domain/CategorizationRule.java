package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/** Regla de categorización: si la descripción contiene {@link #pattern}, usar {@link #categoryId}. */
@Entity
@Table(name = "categorization_rules")
public class CategorizationRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 80)
    public String pattern;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
