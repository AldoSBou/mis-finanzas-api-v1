package pe.suarez.finanzas.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.AuthDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.security.JwtIssuer;
import pe.suarez.finanzas.security.RateLimitService;
import pe.suarez.finanzas.security.RefreshTokenService;
import pe.suarez.finanzas.security.TokenDenyList;

import java.time.Instant;

/**
 * Servicio de autenticación con todas las mitigaciones de seguridad aplicadas:
 *
 * <ul>
 *   <li><b>MF-01 (rate limit por email)</b>: lockout tras N fallos consecutivos.</li>
 *   <li><b>MF-01 (timing attack)</b>: BCrypt dummy cuando el email no existe,
 *       para igualar latencias entre casos.</li>
 *   <li><b>MF-03 (logout server-side)</b>: revocación de jti en denylist.</li>
 *   <li><b>MF-05 (password policy)</b>: validación contra política mínima.</li>
 * </ul>
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    /**
     * Hash dummy precomputado de un password arbitrario. Sirve para que la rama
     * "email no existe" tarde lo mismo que la rama "password incorrecto", mitigando
     * timing attacks de enumeración (MF-01 derivado).
     */
    private String DUMMY_HASH;

    @Inject JwtIssuer jwtIssuer;
    @Inject EntityManager em;
    @Inject RateLimitService rateLimitService;
    @Inject TokenDenyList tokenDenyList;
    @Inject PasswordPolicy passwordPolicy;
    @Inject RefreshTokenService refreshTokens;

    @PostConstruct
    void init() {
        // Generamos el dummy hash una sola vez al arrancar.
        // Su valor exacto no importa — solo que sea un BCrypt válido.
        DUMMY_HASH = BcryptUtil.bcryptHash("dummy-password-for-timing-mitigation");
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        // Política de password (MF-05)
        passwordPolicy.validate(req.password());

        if (User.findByEmail(req.email()).isPresent()) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.email = req.email().toLowerCase().trim();
        user.passwordHash = BcryptUtil.bcryptHash(req.password());
        user.displayName = req.displayName();
        user.persist();

        em.createNativeQuery("SELECT seed_default_data_for_user(:uid)")
                .setParameter("uid", user.id)
                .getSingleResult();

        return tokensFor(user, Boolean.TRUE.equals(req.rememberDevice()));
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String emailNorm = req.email() != null ? req.email().toLowerCase().trim() : "";

        // Pre-check: si la cuenta está temporalmente bloqueada, rechazar inmediatamente
        // sin pasar por BCrypt (ahorra CPU y revela poco al atacante: el lockout no
        // confirma existencia del email porque solo se activa tras fallos previos).
        if (rateLimitService.isEmailLocked(emailNorm)) {
            LOG.warnf("Login bloqueado por lockout: email=%s", emailNorm);
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_TEMPORARILY_LOCKED);
        }

        var userOpt = User.<User>findByEmail(emailNorm);

        // === MITIGACIÓN TIMING ATTACK (MF-01) ===
        // Ejecutamos BCrypt SIEMPRE, exista o no el usuario, contra el mismo tipo
        // de hash. Esto iguala latencias entre los dos casos.
        String hashToCheck = userOpt.map(u -> u.passwordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = BcryptUtil.matches(req.password(), hashToCheck);

        // Verdadero solo si: usuario existe Y password coincide
        boolean success = userOpt.isPresent() && passwordMatches;

        if (!success) {
            // Registrar intento fallido (alimenta lockout por email)
            rateLimitService.registerEmailFailure(emailNorm);
            LOG.debugf("Login fallido: email=%s exists=%s", emailNorm, userOpt.isPresent());
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Login exitoso: limpiar contador de fallos
        rateLimitService.resetEmailFailures(emailNorm);

        User user = userOpt.get();
        return tokensFor(user, Boolean.TRUE.equals(req.rememberDevice()));
    }

    /**
     * Nueva sesión a partir de un refresh token (que queda revocado: rotación).
     * No revierte ante {@link ApiException}: si se detecta el reuso de un token, la
     * revocación de todas las sesiones del usuario debe quedar guardada.
     */
    @Transactional(dontRollbackOn = ApiException.class)
    public TokenResponse refresh(RefreshRequest req) {
        Long userId = refreshTokens.consume(req.refreshToken());
        User user = User.findById(userId);
        if (user == null) throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        return tokensFor(user, true);
    }

    private TokenResponse tokensFor(User user, boolean rememberDevice) {
        String token = jwtIssuer.issueFor(user);
        String refresh = rememberDevice ? refreshTokens.issue(user.id) : null;
        return new TokenResponse(token, jwtIssuer.durationSeconds(), Mappers.toUserResponse(user), refresh);
    }

    /**
     * Cierra la sesión del token actual agregándolo al denylist.
     * Tras esta operación, ese mismo token no podrá usarse aunque no haya expirado.
     *
     * <p><b>MITIGACIÓN MF-03</b>
     */
    @Transactional
    public void logout(JsonWebToken jwt, String refreshToken) {
        // La app móvil manda su refresh token para cerrar también la sesión del dispositivo
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
        }
        if (jwt == null || jwt.getTokenID() == null) {
            // Sin jti no hay nada que revocar específicamente; igual respondemos OK
            // para no filtrar info al cliente.
            return;
        }
        // Calcular el instante de expiración del token
        long expSeconds = jwt.getExpirationTime();
        Instant expiresAt = Instant.ofEpochSecond(expSeconds);
        tokenDenyList.revoke(jwt.getTokenID(), expiresAt);
        LOG.infof("Logout: token revocado jti=%s upn=%s", jwt.getTokenID(), jwt.getName());
    }
}
