package pe.suarez.finanzas.filter;

import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ApiError;
import pe.suarez.finanzas.api.ApiResponse;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.api.Meta;
import pe.suarez.finanzas.security.RateLimitService;
import pe.suarez.finanzas.security.RateLimited;

/**
 * Filtro que aplica rate limiting a endpoints marcados con {@link RateLimited}.
 *
 * <p>Si la IP excede el límite, devuelve 429 Too Many Requests con un
 * envelope estandarizado y header Retry-After.
 *
 * <p><b>MITIGACIÓN MF-01</b>
 */
@Provider
@RateLimited
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);

    @Inject
    RateLimitService rateLimitService;

    /**
     * Quarkus REST corre sobre Vert.x. RoutingContext nos da acceso al socket
     * subyacente para extraer la IP real del cliente cuando no hay proxy.
     */
    @Context
    RoutingContext routingContext;

    @ConfigProperty(name = "app.security.rate-limit.ip.window-minutes", defaultValue = "15")
    int windowMinutes;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String ip = extractClientIp(ctx);
        if (rateLimitService.tryAcquireIp(ip)) {
            return; // OK, request pasa
        }

        // Excedido — abortar con 429
        LOG.warnf("Rate limit excedido para IP=%s en %s %s",
          ip, ctx.getMethod(), ctx.getUriInfo().getPath());

        ApiError error = ApiError.of(ErrorCode.RATE_LIMIT_EXCEEDED);
        Object requestId = ctx.getProperty(RequestIdFilter.REQUEST_ID_PROPERTY);
        Meta meta = Meta.now(requestId != null ? requestId.toString() : null,
          ctx.getUriInfo().getPath());
        ApiResponse<Object> body = ApiResponse.error(error, meta);

        Response response = Response.status(429)
          .type(MediaType.APPLICATION_JSON)
          .header("Retry-After", String.valueOf(windowMinutes * 60))
          .entity(body)
          .build();

        ctx.abortWith(response);
    }

    /**
     * Extrae la IP real del cliente.
     *
     * <p>En producción detrás de un proxy (Cloudflare, nginx, Railway) la IP real
     * viene en headers como X-Forwarded-For. En localhost sin proxy, viene del
     * socket directo via Vert.x RoutingContext.
     */
    private String extractClientIp(ContainerRequestContext ctx) {
        // Headers comunes de proxy reverse en orden de prioridad
        String[] proxyHeaders = {
          "X-Forwarded-For",
          "X-Real-IP",
          "CF-Connecting-IP",
          "True-Client-IP"
        };
        for (String h : proxyHeaders) {
            String v = ctx.getHeaderString(h);
            if (v != null && !v.isBlank()) {
                // X-Forwarded-For puede ser "client, proxy1, proxy2" — tomamos el primero
                int comma = v.indexOf(',');
                return (comma > 0 ? v.substring(0, comma) : v).trim();
            }
        }
        // Fallback: IP del socket directo via Vert.x
        if (routingContext != null && routingContext.request() != null) {
            String remoteAddr = routingContext.request().remoteAddress() != null
              ? routingContext.request().remoteAddress().host()
              : null;
            if (remoteAddr != null && !remoteAddr.isBlank()) {
                return remoteAddr;
            }
        }
        return "unknown";
    }
}