package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.RecurringDtos.*;
import pe.suarez.finanzas.service.RecurringService;

import java.util.List;

@Path("/api/recurring")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Recurrentes")
public class RecurringResource {

    @Inject RecurringService service;

    @GET
    public List<RecurringResponse> list() {
        service.materializeDue();
        return service.list();
    }

    @POST
    public Response create(@Valid RecurringRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.create(req)).build();
    }

    @PUT
    @Path("/{id}")
    public RecurringResponse update(@PathParam("id") Long id, @Valid RecurringRequest req) {
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /** Registra la ocurrencia pendiente (cuerpo opcional: monto, tipo de cambio, fecha). */
    @POST
    @Path("/{id}/register")
    public Response register(@PathParam("id") Long id, @Valid RegisterOccurrenceRequest req) {
        service.register(id, req);
        return Response.noContent().build();
    }

    /** Omite la ocurrencia pendiente sin registrar nada. */
    @POST
    @Path("/{id}/skip")
    public Response skip(@PathParam("id") Long id) {
        service.skip(id);
        return Response.noContent().build();
    }
}
