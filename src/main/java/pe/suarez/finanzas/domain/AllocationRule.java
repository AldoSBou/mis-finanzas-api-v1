package pe.suarez.finanzas.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "allocation_rules", indexes = @Index(name = "idx_rule_user", columnList = "user_id"))
public class AllocationRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 60)
    public String name;

    @Column(length = 250)
    public String description;

    /**
     * Mapa bucket -> porcentaje (0..100). Debe sumar 100.
     * Ejemplo 50/30/20: {"NEEDS": 50, "WANTS": 30, "SAVINGS": 20}
     * Ejemplo 70/20/10: {"NEEDS": 70, "SAVINGS": 20, "INVESTMENT": 10}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    public Map<String, Integer> percentages;

    @Column(name = "is_template", nullable = false)
    public boolean template = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
