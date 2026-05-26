package pe.suarez.finanzas.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import pe.suarez.finanzas.api.ApiResponse;
import pe.suarez.finanzas.api.Meta;
import pe.suarez.finanzas.api.RawResponse;
import pe.suarez.finanzas.dto.TransactionDtos;

/**
 * Envuelve automáticamente las respuestas exitosas (2xx) en {@link ApiResponse}.
 *
 * <p>No envuelve:
 * <ul>
 *   <li>Respuestas de error (los maneja el ExceptionMapper).</li>
 *   <li>Respuestas ya envueltas (alguien que devolvió {@code ApiResponse} explícito).</li>
 *   <li>Respuestas sin contenido (204).</li>
 *   <li>Endpoints anotados con {@link RawResponse}.</li>
 *   <li>Respuestas no JSON (CSV, binarios, etc.).</li>
 *   <li>Páginas de transacciones: se desempaqueta {@code items} como data y se mete pagination en meta.</li>
 * </ul>
 */
@Provider
public class ResponseEnvelopeFilter implements ContainerResponseFilter {

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        // Solo envolvemos 2xx con entidad
        int status = responseContext.getStatus();
        if (status < 200 || status >= 300) return;

        Object entity = responseContext.getEntity();
        if (entity == null) return;

        // Saltar si está marcado como Raw
        if (resourceInfo.getResourceMethod() != null
                && (resourceInfo.getResourceMethod().isAnnotationPresent(RawResponse.class)
                || resourceInfo.getResourceClass().isAnnotationPresent(RawResponse.class))) {
            return;
        }

        // Saltar si ya está envuelto
        if (entity instanceof ApiResponse<?>) return;

        // Solo JSON
        MediaType mediaType = responseContext.getMediaType();
        if (mediaType != null && !MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            return;
        }

        String requestId = requestId(requestContext);

        // Caso especial: TransactionPage → desempaquetar items y mover pagination al meta
        if (entity instanceof TransactionDtos.TransactionPage page) {
            Meta meta = Meta.paginated(requestId,
                    Meta.Pagination.of(page.page(), page.size(), page.total()));
            responseContext.setEntity(ApiResponse.paginated(page.items(), meta));
            return;
        }

        // Caso general
        responseContext.setEntity(ApiResponse.ok(entity, null, Meta.now(requestId)));
    }

    private String requestId(ContainerRequestContext ctx) {
        Object id = ctx.getProperty(RequestIdFilter.REQUEST_ID_PROPERTY);
        return id != null ? id.toString() : null;
    }
}
