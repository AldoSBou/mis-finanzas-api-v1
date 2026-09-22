package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_user_date", columnList = "user_id,transaction_date DESC"),
        @Index(name = "idx_tx_user_category", columnList = "user_id,category_id"),
        @Index(name = "idx_tx_account", columnList = "account_id")
})
public class Transaction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    /** Cuenta origen (de donde sale o a donde entra el dinero). */
    @Column(name = "account_id", nullable = false)
    public Long accountId;

    /** Null en transferencias. */
    @Column(name = "category_id")
    public Long categoryId;

    /** Solo en transferencias: cuenta destino. */
    @Column(name = "to_account_id")
    public Long toAccountId;

    /**
     * Monto siempre positivo, en la moneda de la cuenta origen. El signo lo determina {@link #type}.
     * Precision 14, scale 2 soporta hasta 999,999,999,999.99 con dos decimales.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    /** Solo en transferencias: monto recibido en la moneda de la cuenta destino. */
    @Column(name = "to_amount", precision = 14, scale = 2)
    public BigDecimal toAmount;

    /** Tipo de cambio de {@link #currency} a la moneda base del usuario (1 si son iguales). */
    @Column(name = "exchange_rate", nullable = false, precision = 14, scale = 6)
    public BigDecimal exchangeRate = BigDecimal.ONE;

    /** {@link #amount} convertido a la moneda base. Es lo que suman los reportes. */
    @Column(name = "amount_base", nullable = false, precision = 14, scale = 2)
    public BigDecimal amountBase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TransactionType type;

    @Column(name = "transaction_date", nullable = false)
    public LocalDate transactionDate;

    @Column(length = 200)
    public String description;

    /** Recurrente que generó este movimiento, si aplica. */
    @Column(name = "recurring_id")
    public Long recurringId;

    @Column(name = "payment_method", length = 40)
    public String paymentMethod;

    /** Moneda de la cuenta origen (denormalizada para consultas). */
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
