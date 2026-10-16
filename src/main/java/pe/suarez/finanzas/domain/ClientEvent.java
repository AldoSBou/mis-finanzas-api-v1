package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Evento que manda la app móvil para medir la marcha blanca:
 * cuánto tarda cada registro y qué errores ve la persona.
 */
@Entity
@Table(name = "client_events")
public class ClientEvent extends PanacheEntityBase {

    public enum Kind {
        /** Un movimiento guardado; {@link #durationMs} es el tiempo de la persona hasta tocar Guardar. */
        ENTRY,
        /** Algo falló en el teléfono. */
        ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Kind kind;

    @Column(name = "duration_ms")
    public Integer durationMs;

    @Column(length = 30)
    public String source;

    @Column(length = 500)
    public String message;

    @Column(length = 20)
    public String platform;

    @Column(name = "app_version", length = 20)
    public String appVersion;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
