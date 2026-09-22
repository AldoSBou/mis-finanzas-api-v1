package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.dto.TransactionDtos.*;
import pe.suarez.finanzas.exception.ApiException;
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
     * `accountId` (opcional) filtra los movimientos que entran o salen de esa cuenta.
     * Ejemplo: GET /api/transactions?period=2026-04&accountId=3&page=0&size=20
     */
    @GET
    public TransactionPage list(
            @QueryParam("period") String period,
            @QueryParam("accountId") Long accountId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        if (size > 100) size = 100;
        return service.listForMonth(ym, accountId, page, size);
    }

    /**
     * Último tipo de cambio que usaste para una moneda (para prellenar el formulario).
     * Ejemplo: GET /api/transactions/exchange-rate?currency=USD → 204 si nunca la usaste.
     */
    @GET
    @Path("/exchange-rate")
    public Response latestExchangeRate(@QueryParam("currency") String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new ApiException(ErrorCode.CURRENCY_INVALID);
        }
        return service.latestRate(currency)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.noContent().build());
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
