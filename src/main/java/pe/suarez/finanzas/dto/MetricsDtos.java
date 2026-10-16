package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.suarez.finanzas.domain.ClientEvent;

import java.time.Instant;

public final class MetricsDtos {

    private MetricsDtos() {}

    /** Un evento del teléfono. La app los acumula y los manda en lote cuando hay conexión. */
    public record ClientEventRequest(
            @NotNull ClientEvent.Kind kind,
            @Min(0) Integer durationMs,
            @Size(max = 30) String source,
            @Size(max = 500) String message,
            @Size(max = 20) String platform,
            @Size(max = 20) String appVersion,
            @NotNull Instant occurredAt
    ) {}

    public record ClientEventsAccepted(int accepted) {}

    /**
     * Velocidad de registro de la persona en los últimos días.
     * Los registros que superan el tiempo de inactividad no entran: la app quedó abierta sin usarse.
     */
    public record MetricsSummary(
            int days,
            int entries,
            Integer medianMs,
            Integer under10Pct,
            int fromNotification,
            /** Registros hechos por voz (aceptados tal cual o corregidos) */
            int voice,
            /** De esos, los que se guardaron sin corregir nada */
            int voiceFirstTry,
            int errors
    ) {}
}
