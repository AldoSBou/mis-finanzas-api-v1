package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.domain.Commitment;
import pe.suarez.finanzas.dto.CommitmentDtos.*;
import pe.suarez.finanzas.service.CommitmentService;

import java.util.List;

/** Compromisos de pago: dinero que vas a tener que soltar en una fecha. */
@Path("/api/commitments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Compromisos")
public class CommitmentResource {

    @Inject CommitmentService service;

    /** GET /api/commitments?status=PENDING (sin status: todos, pendientes primero). */
    @GET
    public List<CommitmentResponse> list(@QueryParam("status") Commitment.Status status) {
        return service.list(status);
    }

    @POST
    public Response create(@Valid CommitmentRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.create(req)).build();
    }

    @PUT
    @Path("/{id}")
    public CommitmentResponse update(@PathParam("id") Long id, @Valid CommitmentRequest req) {
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /** Registra el pago (gasto, o "mover dinero" si tiene cuenta destino) y lo cierra. */
    @POST
    @Path("/{id}/pay")
    public CommitmentResponse pay(@PathParam("id") Long id, @Valid PayRequest req) {
        return service.pay(id, req);
    }

    /** Lo da por resuelto sin registrar nada. */
    @POST
    @Path("/{id}/skip")
    public CommitmentResponse skip(@PathParam("id") Long id) {
        return service.skip(id);
    }

    /** Lo vuelve a dejar pendiente. */
    @POST
    @Path("/{id}/reopen")
    public CommitmentResponse reopen(@PathParam("id") Long id) {
        return service.reopen(id);
    }
}
