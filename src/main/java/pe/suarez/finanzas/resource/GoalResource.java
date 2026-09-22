package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.GoalDtos.*;
import pe.suarez.finanzas.service.GoalService;
import pe.suarez.finanzas.service.RecurringService;

import java.util.List;

@Path("/api/goals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Metas de ahorro")
public class GoalResource {

    @Inject GoalService service;
    @Inject RecurringService recurringService;

    /** Metas con su progreso y, por cuenta, cuánto queda sin asignar. */
    @GET
    public GoalsOverview overview(@QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
        recurringService.materializeDue();
        return service.overview(includeArchived);
    }

    @POST
    public Response create(@Valid GoalRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.create(req)).build();
    }

    @PUT
    @Path("/{id}")
    public GoalResponse update(@PathParam("id") Long id, @Valid GoalRequest req) {
        return service.update(id, req);
    }

    /** Borra la meta; las transferencias de sus aportes se conservan. */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/contributions")
    public List<ContributionResponse> contributions(@PathParam("id") Long id) {
        return service.contributions(id);
    }

    /** Aportar (IN) o retirar (OUT); con otra cuenta registra la transferencia. */
    @POST
    @Path("/{id}/contributions")
    public Response contribute(@PathParam("id") Long id, @Valid ContributionRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.contribute(id, req)).build();
    }

    @DELETE
    @Path("/{id}/contributions/{contributionId}")
    public Response deleteContribution(@PathParam("id") Long id, @PathParam("contributionId") Long contributionId) {
        service.deleteContribution(id, contributionId);
        return Response.noContent().build();
    }
}
