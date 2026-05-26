package pe.suarez.finanzas.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Asigna un requestId único a cada request entrante.
 * - Lo expone en el header X-Request-Id de la respuesta.
 * - Lo guarda en MDC para que aparezca en los logs.
 * - Lo guarda como property del request para que el ResponseEnvelopeFilter lo incluya en el meta.
 *
 * <p>Si la request ya trae X-Request-Id (ej. desde un API gateway), se respeta ese valor.
 */
@Provider
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_PROPERTY = "app.requestId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String incoming = requestContext.getHeaderString(REQUEST_ID_HEADER);
        String requestId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();
        requestContext.setProperty(REQUEST_ID_PROPERTY, requestId);
        MDC.put("requestId", requestId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(REQUEST_ID_PROPERTY);
        if (requestId != null) {
            responseContext.getHeaders().putSingle(REQUEST_ID_HEADER, requestId);
        }
        MDC.remove("requestId");
    }
}
