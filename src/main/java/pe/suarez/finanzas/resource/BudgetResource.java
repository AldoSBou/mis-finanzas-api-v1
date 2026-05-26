package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.BudgetDtos.MonthlyBudgetRequest;
import pe.suarez.finanzas.dto.BudgetDtos.MonthlyBudgetResponse;
import pe.suarez.finanzas.service.BudgetService;

import java.time.YearMonth;

@Path("/api/budgets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Presupuesto mensual")
public class BudgetResource {

    @Inject BudgetService service;

    @GET
    public Response getForPeriod(@QueryParam("period") String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        MonthlyBudgetResponse b = service.getForPeriod(ym);
        if (b == null) return Response.noContent().build();
        return Response.ok(b).build();
    }

    @POST
    public MonthlyBudgetResponse upsert(@Valid MonthlyBudgetRequest req) {
        return service.upsertBudget(req);
    }
}
