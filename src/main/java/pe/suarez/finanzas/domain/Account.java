package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts", indexes = @Index(name = "idx_account_user", columnList = "user_id"))
public class Account extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 60)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AccountType type;

    /** Moneda ISO 4217. Todos los movimientos de la cuenta están en esta moneda. */
    @Column(nullable = false, length = 3)
    public String currency = "PEN";

    /** Saldo al empezar a registrar. En tarjetas de crédito, negativo = deuda. */
    @Column(name = "initial_balance", nullable = false, precision = 14, scale = 2)
    public BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(length = 7)
    public String color;

    @Column(length = 40)
    public String icon;

    @Column(nullable = false)
    public boolean archived = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
