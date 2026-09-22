package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.dto.ReportDtos.ReportResponse;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.service.RecurringService;
import pe.suarez.finanzas.service.ReportService;

import java.time.YearMonth;

@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Reportes")
public class ReportResource {

    @Inject ReportService service;
    @Inject RecurringService recurringService;

    /**
     * GET /api/reports?until=2026-09&months=12
     * Ingresos, gastos, ahorro y patrimonio por mes, y gasto por categoría,
     * para los {@code months} meses que terminan en {@code until}.
     */
    @GET
    public ReportResponse report(@QueryParam("until") String until,
                                 @QueryParam("months") @DefaultValue("12") int months) {
        if (months < 1 || months > 36) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "El rango debe ser de 1 a 36 meses");
        }
        YearMonth ym = until != null ? YearMonth.parse(until) : YearMonth.now();
        recurringService.materializeDue();
        return service.build(ym, months);
    }
}
