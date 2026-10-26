package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.ReceiptDtos.*;
import pe.suarez.finanzas.service.ReceiptService;

/** Boletas leídas desde una foto: un movimiento por categoría más el detalle de productos. */
@Path("/api/receipts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Boletas")
public class ReceiptResource {

    @Inject ReceiptService service;

    /** POST /api/receipts/parse {"images": ["..."], "mediaType": "image/jpeg"} → grupos por categoría para confirmar. */
    @POST
    @Path("/parse")
    public ReceiptPreview parse(@Valid ReceiptParseRequest req) {
        return service.parse(req);
    }

    /** POST /api/receipts: la persona confirmó los grupos; crea la boleta y sus movimientos. */
    @POST
    public Response create(@Valid ReceiptCreateRequest req) {
        return Response.status(Response.Status.CREATED).entity(service.create(req)).build();
    }

    @GET
    @Path("/{id}")
    public ReceiptResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    /** DELETE /api/receipts/{id}: deshacer la boleta completa (sus movimientos incluidos). */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
