package pe.suarez.finanzas.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ApiError;
import pe.suarez.finanzas.api.ApiResponse;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.api.Meta;
import pe.suarez.finanzas.security.TokenDenyList;

/**
 * Filtro global que rechaza requests con tokens revocados.
 *
 * <p>Se ejecuta en TODAS las requests, pero actúa SOLO cuando hay un JWT presente
 * y válido. En endpoints públicos (login, register, /api/errors) no hay token,
 * así que el filtro no hace nada y la request continúa normal.
 *
 * <p>No usa name-binding: {@code io.quarkus.security.Authenticated} NO es un
 * {@code @NameBinding} y usarlo como tal rompe el manejo de endpoints @PermitAll.
 * En su lugar, el guard {@code jwt.getRawToken() == null} hace el trabajo:
 * si no hay token, no es nuestro problema.
 *
 * <p><b>MITIGACIÓN MF-03</b>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class TokenRevocationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(TokenRevocationFilter.class);

    @Inject
    TokenDenyList denyList;

    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext ctx) {
        // Guard: si no hay JWT, es un endpoint público (login, register, errors)
        // o una request sin autenticar — no es trabajo de este filtro rechazarla.
        // La capa de seguridad de Quarkus ya maneja los endpoints @Authenticated.
        if (jwt == null || jwt.getRawToken() == null || jwt.getRawToken().isBlank()) {
            return;
        }

        // A partir de aquí: hay un JWT presente y ya validado por SmallRye
        // (firma, exp, iss). Solo verificamos si fue revocado vía logout.
        String jti = jwt.getTokenID();
        if (jti == null || jti.isBlank()) {
            // Token válido pero sin jti — no se puede revocar individualmente.
            // Un emisor confiable siempre incluye jti; lo rechazamos por seguridad.
            abortRevoked(ctx, "Token sin claim jti");
            return;
        }

        if (denyList.isRevoked(jti)) {
            LOG.warnf("Token revocado intentó acceder: jti=%s upn=%s",
              jti, jwt.getName());
            abortRevoked(ctx, null);
        }
    }

    private void abortRevoked(ContainerRequestContext ctx, String customMessage) {
        ApiError error = customMessage != null
          ? ApiError.of(ErrorCode.AUTH_TOKEN_REVOKED, customMessage)
          : ApiError.of(ErrorCode.AUTH_TOKEN_REVOKED);
        Object requestId = ctx.getProperty(RequestIdFilter.REQUEST_ID_PROPERTY);
        Meta meta = Meta.now(requestId != null ? requestId.toString() : null,
          ctx.getUriInfo().getPath());
        ApiResponse<Object> body = ApiResponse.error(error, meta);
        ctx.abortWith(Response.status(401).type(MediaType.APPLICATION_JSON).entity(body).build());
    }
}