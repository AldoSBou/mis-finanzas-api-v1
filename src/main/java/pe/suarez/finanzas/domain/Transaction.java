package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_user_date", columnList = "user_id,transaction_date DESC"),
        @Index(name = "idx_tx_user_category", columnList = "user_id,category_id")
})
public class Transaction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    /**
     * Monto siempre positivo. El signo lo determina {@link #type}.
     * Precision 14, scale 2 soporta hasta 999,999,999,999.99 con dos decimales.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TransactionType type;

    @Column(name = "transaction_date", nullable = false)
    public LocalDate transactionDate;

    @Column(length = 200)
    public String description;

    @Column(name = "payment_method", length = 40)
    public String paymentMethod;

    @Column(nullable = false, length = 3)
    public String currency = "PEN";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
