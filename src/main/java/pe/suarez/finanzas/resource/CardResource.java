package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.CardDtos.*;
import pe.suarez.finanzas.service.CardService;
import pe.suarez.finanzas.service.RecurringService;

import java.util.List;

@Path("/api/cards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Tarjetas de crédito")
public class CardResource {

    @Inject CardService service;
    @Inject RecurringService recurringService;

    /** Tarjetas con deuda, disponible, próximo vencimiento y estado del último pago. */
    @GET
    public List<CardSummary> list() {
        recurringService.materializeDue();
        return service.summaries();
    }

    @GET
    @Path("/{id}")
    public CardDetail detail(@PathParam("id") Long id) {
        recurringService.materializeDue();
        return service.detail(id);
    }

    /** Registra el estado de cuenta del ciclo (si ya existe uno con ese cierre, lo actualiza). */
    @POST
    @Path("/{id}/statements")
    public StatementResponse saveStatement(@PathParam("id") Long id, @Valid StatementRequest req) {
        return service.saveStatement(id, req);
    }

    @DELETE
    @Path("/{id}/statements/{statementId}")
    public Response deleteStatement(@PathParam("id") Long id, @PathParam("statementId") Long statementId) {
        service.deleteStatement(id, statementId);
        return Response.noContent().build();
    }
}
