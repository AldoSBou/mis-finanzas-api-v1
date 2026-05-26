package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_category_user", columnList = "user_id"),
        @Index(name = "idx_category_user_type", columnList = "user_id,type")
})
public class Category extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 60)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_bucket", length = 20)
    public AllocationBucket defaultBucket = AllocationBucket.UNCATEGORIZED;

    @Column(length = 7)
    public String color;

    @Column(length = 40)
    public String icon;

    @Column(nullable = false)
    public boolean archived = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
