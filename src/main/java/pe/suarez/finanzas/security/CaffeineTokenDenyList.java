package pe.suarez.finanzas.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * Implementación in-memory de {@link TokenDenyList} usando Caffeine.
 *
 * <p>Características:
 * <ul>
 *   <li>TTL automático: cada entrada expira al mismo tiempo que el JWT.</li>
 *   <li>Capacidad bounded: máx 10,000 entradas (suficiente para single-tenant
 *       con uso normal). Caffeine evicta LRU si se excede.</li>
 *   <li>Lookup O(1) por hash.</li>
 * </ul>
 *
 * <p><b>Limitación conocida</b>: la lista se pierde al reiniciar el backend.
 * Esto es aceptable porque:
 * <ol>
 *   <li>Los tokens revocados igual expiran solos en ≤8h.</li>
 *   <li>Single instance: no hay múltiples nodos para sincronizar.</li>
 *   <li>Reinicios son poco frecuentes en producción.</li>
 * </ol>
 *
 * <p>Para multi-instance, reemplazar por implementación con Redis manteniendo
 * la misma interfaz {@link TokenDenyList}.
 */
@ApplicationScoped
public class CaffeineTokenDenyList implements TokenDenyList {

    private static final Logger LOG = Logger.getLogger(CaffeineTokenDenyList.class);

    private Cache<String, Instant> revoked;

    @PostConstruct
    void init() {
        revoked = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<String, Instant>() {
                    @Override
                    public long expireAfterCreate(String key, Instant expiresAt, long currentTime) {
                        long ttlNanos = Duration.between(Instant.now(), expiresAt).toNanos();
                        return Math.max(ttlNanos, 0);
                    }
                    @Override
                    public long expireAfterUpdate(String key, Instant value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                    @Override
                    public long expireAfterRead(String key, Instant value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        LOG.info("CaffeineTokenDenyList inicializado (max 10,000 entries, TTL = JWT exp)");
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            // No tiene sentido revocar un token ya expirado
            return;
        }
        revoked.put(jti, expiresAt);
        LOG.debugf("Token revocado: jti=%s exp=%s", jti, expiresAt);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return revoked.getIfPresent(jti) != null;
    }

    @Override
    public int cleanupExpired() {
        long before = revoked.estimatedSize();
        revoked.cleanUp();
        long after = revoked.estimatedSize();
        int evicted = (int) (before - after);
        if (evicted > 0) {
            LOG.debugf("Cleanup: %d entradas expiradas", evicted);
        }
        return evicted;
    }
}
