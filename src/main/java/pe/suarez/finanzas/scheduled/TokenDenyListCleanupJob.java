package pe.suarez.finanzas.scheduled;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.security.TokenDenyList;

/**
 * Job de mantenimiento que ejecuta cleanup explícito del denylist.
 * Caffeine ya hace expulsión automática por TTL, pero el cleanup explícito
 * libera memoria de forma proactiva y nos da puntos de logging.
 */
@ApplicationScoped
public class TokenDenyListCleanupJob {

    private static final Logger LOG = Logger.getLogger(TokenDenyListCleanupJob.class);

    @Inject
    TokenDenyList denyList;

    /**
     * Cada hora limpia entradas expiradas.
     * Usamos cron para evitar que coincida con peaks de tráfico.
     */
    @Scheduled(cron = "0 17 * * * ?", identity = "denylist-cleanup")
    void cleanup() {
        try {
            int evicted = denyList.cleanupExpired();
            if (evicted > 0) {
                LOG.infof("DenyList cleanup: %d entradas expiradas removidas", evicted);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error durante cleanup del denylist");
        }
    }
}
