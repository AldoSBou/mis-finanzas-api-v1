package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Plantilla de un movimiento que se repite. Los campos del movimiento siguen las
 * mismas reglas que {@link Transaction}; {@link #nextDate} es la próxima ocurrencia
 * aún no registrada.
 */
@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TransactionType type;

    @Column(name = "account_id", nullable = false)
    public Long accountId;

    @Column(name = "to_account_id")
    public Long toAccountId;

    @Column(name = "category_id")
    public Long categoryId;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "to_amount", precision = 14, scale = 2)
    public BigDecimal toAmount;

    /** Tipo de cambio a usar si la cuenta no está en la moneda base. */
    @Column(name = "exchange_rate", precision = 14, scale = 6)
    public BigDecimal exchangeRate;

    @Column(length = 200)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Frequency frequency;

    @Column(name = "anchor_day", nullable = false)
    public int anchorDay;

    @Column(name = "next_date", nullable = false)
    public LocalDate nextDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(name = "auto_create", nullable = false)
    public boolean autoCreate = true;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** Avanza a la siguiente ocurrencia; se desactiva si pasa la fecha de fin. */
    public void advance() {
        nextDate = frequency.next(nextDate, anchorDay);
        if (endDate != null && nextDate.isAfter(endDate)) {
            active = false;
        }
    }
}
