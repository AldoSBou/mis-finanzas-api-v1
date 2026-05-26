package pe.suarez.finanzas.security;

import java.time.Instant;

/**
 * Abstracción de la lista de tokens revocados.
 *
 * <p>Permite cambiar la implementación (Caffeine in-memory ↔ Redis distribuido)
 * sin tocar los puntos de uso (filtro JWT, AuthService).
 *
 * <p><b>Decisión arquitectural</b>: la implementación inicial es in-memory (Caffeine)
 * porque la app corre como instancia única. Migrar a Redis es trivial:
 * implementar esta interfaz contra {@code quarkus-redis-cache}.
 *
 * <p><b>MITIGACIÓN MF-03</b>
 */
public interface TokenDenyList {

    /**
     * Marca un token (identificado por su jti) como revocado.
     *
     * @param jti       claim "jti" del JWT — identificador único del token
     * @param expiresAt instante en que expira el token (post esa fecha la entrada
     *                  puede limpiarse; el token será inválido por exp anyway)
     */
    void revoke(String jti, Instant expiresAt);

    /**
     * Verifica si un token está revocado.
     *
     * @param jti claim "jti" del JWT
     * @return true si fue revocado
     */
    boolean isRevoked(String jti);

    /**
     * Limpieza explícita de entradas expiradas. Caffeine lo hace automáticamente
     * por TTL pero exponemos el método para testing y logging.
     *
     * @return número de entradas expulsadas
     */
    int cleanupExpired();
}
