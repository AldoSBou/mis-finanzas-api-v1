package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.dto.CashflowDtos.CashflowResponse;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.service.CashflowService;
import pe.suarez.finanzas.service.RecurringService;

import java.time.YearMonth;

@Path("/api/cashflow")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Flujo de caja")
public class CashflowResource {

    @Inject CashflowService service;
    @Inject RecurringService recurringService;

    /**
     * GET /api/cashflow?until=2026-10&months=3: grilla de ingresos, gastos y ahorro por
     * categoría para los {@code months} meses (1 a 6) que terminan en {@code until}.
     */
    @GET
    public CashflowResponse cashflow(@QueryParam("until") String until,
                                     @QueryParam("months") @DefaultValue("1") int months) {
        if (months < 1 || months > CashflowService.MAX_MONTHS) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "months debe estar entre 1 y " + CashflowService.MAX_MONTHS);
        }
        YearMonth ym = until != null ? YearMonth.parse(until) : YearMonth.now();
        recurringService.materializeDue();
        return service.build(ym, months);
    }
}
