package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.TransactionDtos.*;
import pe.suarez.finanzas.service.TransactionService;

import java.time.YearMonth;

@Path("/api/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Movimientos")
public class TransactionResource {

    @Inject TransactionService service;

    /**
     * Lista paginada de movimientos. El parámetro `period` espera formato YYYY-MM.
     * Ejemplo: GET /api/transactions?period=2026-04&page=0&size=20
     */
    @GET
    public TransactionPage list(
            @QueryParam("period") String period,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        if (size > 100) size = 100;
        return service.listForMonth(ym, page, size);
    }

    @GET
    @Path("/{id}")
    public TransactionResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    @POST
    public Response create(@Valid TransactionRequest req) {
        TransactionResponse resp = service.create(req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @PUT
    @Path("/{id}")
    public TransactionResponse update(@PathParam("id") Long id, @Valid TransactionRequest req) {
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
