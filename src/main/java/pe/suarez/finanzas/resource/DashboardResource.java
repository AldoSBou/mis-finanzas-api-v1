package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.BudgetDtos.DashboardResponse;
import pe.suarez.finanzas.service.DashboardService;

import java.time.YearMonth;

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Panel principal")
public class DashboardResource {

    @Inject DashboardService service;

    /**
     * GET /api/dashboard?period=2026-04
     * Devuelve el resumen completo del mes: ingresos, gastos, balance,
     * progreso por bucket de la regla activa, y top categorías gastadas.
     */
    @GET
    public DashboardResponse dashboard(@QueryParam("period") String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.build(ym);
    }
}
