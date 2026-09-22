package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.TransactionDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.dto.CardDtos.InstallmentRequest;
import pe.suarez.finanzas.dto.CardDtos.InstallmentResponse;
import pe.suarez.finanzas.service.CardService;
import pe.suarez.finanzas.service.RecurringService;
import pe.suarez.finanzas.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Path("/api/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Movimientos")
public class TransactionResource {

    @Inject TransactionService service;
    @Inject RecurringService recurringService;
    @Inject CardService cardService;

    /**
     * Filtros de búsqueda compartidos por el listado y la exportación.
     * Rango: {@code from}/{@code to} (YYYY-MM-DD) si vienen; si no, el mes {@code period}
     * (YYYY-MM); si no, el mes actual.
     */
    public static class SearchParams {
        @QueryParam("period") String period;
        @QueryParam("from") LocalDate from;
        @QueryParam("to") LocalDate to;
        @QueryParam("accountId") Long accountId;
        @QueryParam("categoryId") Long categoryId;
        @QueryParam("type") TransactionType type;
        @QueryParam("q") String q;
        @QueryParam("minAmount") BigDecimal minAmount;
        @QueryParam("maxAmount") BigDecimal maxAmount;

        TransactionFilter toFilter() {
            LocalDate start = from;
            LocalDate end = to;
            if (start == null || end == null) {
                YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
                if (start == null) start = ym.atDay(1);
                if (end == null) end = ym.atEndOfMonth();
            }
            if (start.isAfter(end)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "La fecha inicial no puede ser posterior a la final");
            }
            return new TransactionFilter(start, end, accountId, categoryId, type, q, minAmount, maxAmount);
        }
    }

    /**
     * Lista paginada de movimientos con filtros opcionales.
     * Ejemplos:
     *   GET /api/transactions?period=2026-04&accountId=3
     *   GET /api/transactions?from=2025-01-01&to=2026-12-31&q=soat&type=EXPENSE
     */
    @GET
    public TransactionPage list(
            @BeanParam SearchParams params,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (size > 100) size = 100;
        TransactionFilter filter = params.toFilter();
        recurringService.materializeDue();
        return service.search(filter, page, size);
    }

    /** Descarga en CSV los movimientos que cumplen los filtros (mismos parámetros que el listado). */
    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response export(@BeanParam SearchParams params) {
        TransactionFilter filter = params.toFilter();
        String name = "movimientos-" + filter.from() + "_" + filter.to() + ".csv";
        return Response.ok(service.exportCsv(filter))
                .type("text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .build();
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

    /** Plan de cuotas de una compra con tarjeta (204 si se pagó en una sola). */
    @GET
    @Path("/{id}/installments")
    public Response installments(@PathParam("id") Long id) {
        return cardService.getInstallments(id)
                .map(p -> Response.ok(p).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}/installments")
    public InstallmentResponse setInstallments(@PathParam("id") Long id, @Valid InstallmentRequest req) {
        return cardService.setInstallments(id, req);
    }

    @DELETE
    @Path("/{id}/installments")
    public Response removeInstallments(@PathParam("id") Long id) {
        cardService.removeInstallments(id);
        return Response.noContent().build();
    }
}
