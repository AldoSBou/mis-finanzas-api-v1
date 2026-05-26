package pe.suarez.finanzas.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Acceso al usuario autenticado a partir del JWT.
 * Inyecta este bean en services/resources que necesiten saber quién hace la llamada.
 * Todas las queries multi-tenant deben filtrar por userId().
 */
@RequestScoped
public class UserContext {

    @Inject
    JsonWebToken jwt;

    /**
     * Devuelve el userId del JWT (claim "uid" como Long).
     * @throws NotAuthorizedException si no hay JWT o no contiene el claim.
     */
    public Long userId() {
        if (jwt == null || jwt.getName() == null) {
            throw new NotAuthorizedException("Token no presente");
        }
        Object uidClaim = jwt.getClaim("uid");
        if (uidClaim == null) {
            throw new NotAuthorizedException("Token sin claim 'uid'");
        }
        return switch (uidClaim) {
            case Number n -> n.longValue();
            case jakarta.json.JsonNumber jn -> jn.longValue();
            case String s -> Long.parseLong(s);
            default -> throw new NotAuthorizedException(
              "Claim 'uid' tiene tipo inesperado: " + uidClaim.getClass().getName());
        };
    }

    public String email() {
        return jwt != null ? jwt.getName() : null;
    }
}
