package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.LiquidityDtos.LiquidityResponse;
import pe.suarez.finanzas.service.LiquidityService;
import pe.suarez.finanzas.service.RecurringService;

import java.math.BigDecimal;

@Path("/api/liquidity")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Liquidez")
public class LiquidityResource {

    @Inject LiquidityService service;
    @Inject RecurringService recurringService;

    /**
     * GET /api/liquidity?daily=45: efectivo proyectado día a día hasta el fin del periodo
     * siguiente. {@code daily} reemplaza el gasto variable diario estimado.
     */
    @GET
    public LiquidityResponse liquidity(@QueryParam("daily") BigDecimal daily) {
        recurringService.materializeDue();
        return service.build(daily);
    }
}
