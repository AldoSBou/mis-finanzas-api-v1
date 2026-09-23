package pe.suarez.finanzas.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.RefreshToken;
import pe.suarez.finanzas.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens para que la app móvil no pida la contraseña cada 8 horas.
 *
 * <ul>
 *   <li>Opacos (32 bytes aleatorios); en la base solo va su SHA-256.</li>
 *   <li>Rotación: cada uso revoca el token y emite otro.</li>
 *   <li>Reuso de un token revocado = posible robo: se revocan todas las sesiones del usuario.</li>
 * </ul>
 *
 * <p>Los métodos se llaman dentro de la transacción del servicio de autenticación.
 */
@ApplicationScoped
public class RefreshTokenService {

    private static final Logger LOG = Logger.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "app.refresh.duration-days", defaultValue = "90")
    long durationDays;

    /** Emite un token nuevo para el usuario y devuelve su valor en claro (solo esta vez). */
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken rt = new RefreshToken();
        rt.userId = userId;
        rt.tokenHash = hash(token);
        rt.expiresAt = Instant.now().plus(Duration.ofDays(durationDays));
        rt.persist();
        return token;
    }

    /** Valida y revoca el token (rotación); devuelve el usuario dueño. */
    public Long consume(String token) {
        RefreshToken rt = RefreshToken.<RefreshToken>find("tokenHash", hash(token))
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
        if (rt.revokedAt != null) {
            LOG.warnf("Reuso de refresh token revocado: se cierran las sesiones del usuario %d", rt.userId);
            revokeAll(rt.userId);
            throw new ApiException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        if (rt.expiresAt.isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        rt.revokedAt = Instant.now();
        return rt.userId;
    }

    /** Cierra la sesión de este dispositivo (si el token no existe, no pasa nada). */
    public void revoke(String token) {
        RefreshToken.<RefreshToken>find("tokenHash = ?1 AND revokedAt IS NULL", hash(token))
                .firstResultOptional()
                .ifPresent(rt -> rt.revokedAt = Instant.now());
    }

    public void revokeAll(Long userId) {
        RefreshToken.update("revokedAt = ?1 WHERE userId = ?2 AND revokedAt IS NULL", Instant.now(), userId);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
