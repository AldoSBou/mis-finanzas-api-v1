package pe.suarez.finanzas.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter dual basado en Bucket4j.
 *
 * <p>Aplica dos políticas independientes:
 * <ul>
 *   <li><b>Por IP</b>: límite global de intentos contra endpoints de auth (login + register).
 *       Mitiga ataques distribuidos desde una sola fuente.</li>
 *   <li><b>Por email</b>: cuenta intentos fallidos consecutivos contra una cuenta específica.
 *       Mitiga targeting a una víctima concreta. Se resetea con un login exitoso.</li>
 * </ul>
 *
 * <p>Implementación in-memory adecuada para single-instance. Para multi-instance migrar
 * a Bucket4j con backend distribuido (Redis, Hazelcast).
 *
 * <p><b>MITIGACIÓN MF-01</b>
 */
@ApplicationScoped
public class RateLimitService {

    private static final Logger LOG = Logger.getLogger(RateLimitService.class);

    @ConfigProperty(name = "app.security.rate-limit.ip.capacity", defaultValue = "10")
    int ipCapacity;

    @ConfigProperty(name = "app.security.rate-limit.ip.window-minutes", defaultValue = "15")
    int ipWindowMinutes;

    @ConfigProperty(name = "app.security.rate-limit.email.capacity", defaultValue = "5")
    int emailCapacity;

    @ConfigProperty(name = "app.security.rate-limit.email.window-minutes", defaultValue = "15")
    int emailWindowMinutes;

    private final ConcurrentMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> emailBuckets = new ConcurrentHashMap<>();

    private Bandwidth ipLimit;
    private Bandwidth emailLimit;

    @PostConstruct
    void init() {
        ipLimit = Bandwidth.builder()
                .capacity(ipCapacity)
                .refillIntervally(ipCapacity, Duration.ofMinutes(ipWindowMinutes))
                .build();
        emailLimit = Bandwidth.builder()
                .capacity(emailCapacity)
                .refillIntervally(emailCapacity, Duration.ofMinutes(emailWindowMinutes))
                .build();
        LOG.infof("Rate limits inicializados: IP=%d/%dmin, Email=%d/%dmin",
                ipCapacity, ipWindowMinutes, emailCapacity, emailWindowMinutes);
    }

    /**
     * Intenta consumir 1 token del bucket de la IP.
     * @return true si la request puede proceder, false si se excedió el rate limit.
     */
    public boolean tryAcquireIp(String ip) {
        if (ip == null || ip.isBlank()) ip = "unknown";
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> Bucket.builder().addLimit(ipLimit).build());
        return bucket.tryConsume(1);
    }

    /**
     * Verifica si el email puede recibir un nuevo intento.
     * IMPORTANTE: solo se consume cuando el intento FALLA (ver consumeEmailFailure).
     * Esto evita que logins exitosos cuenten contra el límite.
     */
    public boolean isEmailLocked(String email) {
        if (email == null || email.isBlank()) return false;
        Bucket bucket = emailBuckets.get(email.toLowerCase());
        // Si nunca falló, no está bloqueado
        return bucket != null && bucket.getAvailableTokens() <= 0;
    }

    /**
     * Registra un intento fallido contra un email. Después de N fallos,
     * isEmailLocked() devolverá true durante la ventana configurada.
     */
    public void registerEmailFailure(String email) {
        if (email == null || email.isBlank()) return;
        Bucket bucket = emailBuckets.computeIfAbsent(email.toLowerCase(),
                k -> Bucket.builder().addLimit(emailLimit).build());
        bucket.tryConsume(1);
    }

    /**
     * Resetea el contador de fallos para un email tras un login exitoso.
     */
    public void resetEmailFailures(String email) {
        if (email == null || email.isBlank()) return;
        emailBuckets.remove(email.toLowerCase());
    }

    /**
     * Tokens disponibles para una IP (útil para debugging y headers Retry-After).
     */
    public long availableIpTokens(String ip) {
        Bucket bucket = ipBuckets.get(ip);
        return bucket != null ? bucket.getAvailableTokens() : ipCapacity;
    }
}
