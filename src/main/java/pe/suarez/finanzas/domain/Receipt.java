package pe.suarez.finanzas.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Una boleta leída desde una foto. Sus líneas se agrupan por categoría y cada grupo es un
 * {@link Transaction} con {@code receiptId}; aquí queda el detalle de productos para
 * análisis futuros ("cuánto gasto en leche").
 */
@Entity
@Table(name = "receipts")
public class Receipt extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(length = 120)
    public String merchant;

    @Column(name = "receipt_date", nullable = false)
    public LocalDate receiptDate;

    /** Total pagado, en la moneda de la cuenta. */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal total;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "account_id")
    public Long accountId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    public List<Item> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** Una línea de la boleta tal como se leyó. */
    public static class Item {
        public String description;
        public BigDecimal amount;
        public Long categoryId;

        public Item() {}

        public Item(String description, BigDecimal amount, Long categoryId) {
            this.description = description;
            this.amount = amount;
            this.categoryId = categoryId;
        }

        // Hibernate compara el JSON por valor para detectar cambios
        @Override
        public boolean equals(Object o) {
            return o instanceof Item i && Objects.equals(description, i.description)
                    && (amount == null ? i.amount == null : i.amount != null && amount.compareTo(i.amount) == 0)
                    && Objects.equals(categoryId, i.categoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, amount == null ? null : amount.stripTrailingZeros(), categoryId);
        }
    }
}
