package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.OverviewDtos.OverviewResponse;
import pe.suarez.finanzas.service.OverviewService;
import pe.suarez.finanzas.service.RecurringService;

@Path("/api/overview")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Te quedan")
public class OverviewResource {

    @Inject OverviewService service;
    @Inject RecurringService recurringService;

    /** GET /api/overview: cuánto te queda hasta que termine el periodo en curso. */
    @GET
    public OverviewResponse overview() {
        recurringService.materializeDue();
        return service.build();
    }
}
