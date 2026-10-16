package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.MetricsDtos.*;
import pe.suarez.finanzas.service.MetricsService;

import java.util.List;

/** Métricas de uso que manda la app móvil (velocidad de registro y errores). */
@Path("/api/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Métricas de uso")
public class MetricsResource {

    @Inject MetricsService service;

    /** POST /api/metrics/events: lote de eventos del teléfono (hasta 200 por llamada). */
    @POST
    @Path("/events")
    public Response record(@NotNull @Size(max = MetricsService.MAX_BATCH) List<@Valid ClientEventRequest> events) {
        return Response.status(Response.Status.CREATED).entity(service.record(events)).build();
    }

    /** GET /api/metrics/summary?days=30: velocidad de registro de la persona. */
    @GET
    @Path("/summary")
    public MetricsSummary summary(@QueryParam("days") @DefaultValue("30") int days) {
        return service.summary(Math.max(1, Math.min(days, 365)));
    }
}
