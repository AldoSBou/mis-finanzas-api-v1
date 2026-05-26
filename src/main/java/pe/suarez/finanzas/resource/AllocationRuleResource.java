package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.BudgetDtos.*;
import pe.suarez.finanzas.service.BudgetService;

import java.util.List;

@Path("/api/allocation-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Reglas de asignación")
public class AllocationRuleResource {

    @Inject BudgetService service;

    @GET
    public List<AllocationRuleResponse> list() {
        return service.listRules();
    }

    @GET
    @Path("/{id}")
    public AllocationRuleResponse get(@PathParam("id") Long id) {
        return service.getRule(id);
    }

    @POST
    public Response create(@Valid AllocationRuleRequest req) {
        AllocationRuleResponse resp = service.createRule(req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @PUT
    @Path("/{id}")
    public AllocationRuleResponse update(@PathParam("id") Long id, @Valid AllocationRuleRequest req) {
        return service.updateRule(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteRule(id);
        return Response.noContent().build();
    }
}
