package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.CategoryBudgetDtos.*;
import pe.suarez.finanzas.service.CategoryBudgetService;
import pe.suarez.finanzas.service.RecurringService;

import java.time.YearMonth;

@Path("/api/category-budgets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Presupuesto por categoría")
public class CategoryBudgetResource {

    @Inject CategoryBudgetService service;
    @Inject RecurringService recurringService;

    /** GET /api/category-budgets?period=2026-09: límites y gastado del mes por categoría. */
    @GET
    public CategoryBudgetSummary summary(@QueryParam("period") String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        recurringService.materializeDue();
        return service.summary(ym);
    }

    /** Define o cambia el límite mensual de una categoría de gasto. */
    @PUT
    @Path("/{categoryId}")
    public Response upsert(@PathParam("categoryId") Long categoryId, @Valid CategoryBudgetRequest req) {
        service.upsert(categoryId, req);
        return Response.noContent().build();
    }

    /** Quita el límite de la categoría. */
    @DELETE
    @Path("/{categoryId}")
    public Response delete(@PathParam("categoryId") Long categoryId) {
        service.delete(categoryId);
        return Response.noContent().build();
    }
}
