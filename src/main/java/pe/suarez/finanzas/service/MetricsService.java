package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.domain.ClientEvent;
import pe.suarez.finanzas.dto.MetricsDtos.*;
import pe.suarez.finanzas.security.UserContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class MetricsService {

    /** Más de esto no es "registrar": la app quedó abierta sin usarse. */
    static final int IDLE_MS = 120_000;
    public static final int MAX_BATCH = 200;

    @Inject EntityManager em;
    @Inject UserContext userContext;

    @Transactional
    public ClientEventsAccepted record(List<ClientEventRequest> events) {
        Long uid = userContext.userId();
        int accepted = 0;
        for (ClientEventRequest r : events) {
            ClientEvent e = new ClientEvent();
            e.userId = uid;
            e.kind = r.kind();
            e.durationMs = r.durationMs();
            e.source = r.source();
            e.message = r.message();
            e.platform = r.platform();
            e.appVersion = r.appVersion();
            e.occurredAt = r.occurredAt();
            e.persist();
            accepted++;
        }
        return new ClientEventsAccepted(accepted);
    }

    public MetricsSummary summary(int days) {
        Long uid = userContext.userId();
        Instant since = Instant.now().minus(Duration.ofDays(days));

        List<Object[]> entries = em.createQuery("""
                SELECT e.durationMs, e.source FROM ClientEvent e
                WHERE e.userId = :uid AND e.kind = :kind AND e.occurredAt >= :since
                  AND e.durationMs IS NOT NULL AND e.durationMs <= :idle
                ORDER BY e.durationMs
                """, Object[].class)
                .setParameter("uid", uid)
                .setParameter("kind", ClientEvent.Kind.ENTRY)
                .setParameter("since", since)
                .setParameter("idle", IDLE_MS)
                .getResultList();

        long errors = em.createQuery("""
                SELECT COUNT(e) FROM ClientEvent e
                WHERE e.userId = :uid AND e.kind = :kind AND e.occurredAt >= :since
                """, Long.class)
                .setParameter("uid", uid)
                .setParameter("kind", ClientEvent.Kind.ERROR)
                .setParameter("since", since)
                .getSingleResult();

        int n = entries.size();
        if (n == 0) return new MetricsSummary(days, 0, null, null, 0, 0, 0, 0, 0, (int) errors);

        int[] ms = entries.stream().mapToInt(e -> (Integer) e[0]).toArray();
        int median = n % 2 == 1 ? ms[n / 2] : (ms[n / 2 - 1] + ms[n / 2]) / 2;
        long under10 = entries.stream().filter(e -> (Integer) e[0] < 10_000).count();
        long fromNotification = entries.stream().filter(e -> "notification".equals(e[1])).count();
        long voiceFirstTry = entries.stream().filter(e -> "voice".equals(e[1])).count();
        long voiceEdited = entries.stream().filter(e -> "voice-edited".equals(e[1])).count();
        long photoFirstTry = entries.stream().filter(e -> "photo".equals(e[1])).count();
        long photoEdited = entries.stream().filter(e -> "photo-edited".equals(e[1])).count();
        return new MetricsSummary(days, n, median, (int) Math.round(under10 * 100.0 / n),
                (int) fromNotification, (int) (voiceFirstTry + voiceEdited), (int) voiceFirstTry,
                (int) (photoFirstTry + photoEdited), (int) photoFirstTry, (int) errors);
    }
}
