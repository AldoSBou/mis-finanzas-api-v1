package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.ImportDtos.*;
import pe.suarez.finanzas.service.CategorizationService;
import pe.suarez.finanzas.service.ImportService;

import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Importación")
public class ImportResource {

    @Inject ImportService imports;
    @Inject CategorizationService categorization;

    /** Tipo, categoría sugerida y posibles duplicados de las filas leídas del archivo. */
    @POST
    @Path("/imports/preview")
    public PreviewResponse preview(@Valid PreviewRequest req) {
        return imports.preview(req);
    }

    /** Importa las filas confirmadas (todo o nada). */
    @POST
    @Path("/imports")
    public Response commit(@Valid CommitRequest req) {
        return Response.status(Response.Status.CREATED).entity(imports.commit(req)).build();
    }

    @GET
    @Path("/imports")
    public List<ImportBatchResponse> recent() {
        return imports.recent();
    }

    /** Deshace una importación: borra todos sus movimientos. */
    @DELETE
    @Path("/imports/{id}")
    public Response undo(@PathParam("id") Long id) {
        imports.undo(id);
        return Response.noContent().build();
    }

    // ---------- Reglas de categorización ----------

    @GET
    @Path("/categorization-rules")
    public List<RuleResponse> rules() {
        return categorization.listRules();
    }

    @POST
    @Path("/categorization-rules")
    public Response createRule(@Valid RuleRequest req) {
        return Response.status(Response.Status.CREATED).entity(categorization.createRule(req)).build();
    }

    @PUT
    @Path("/categorization-rules/{id}")
    public RuleResponse updateRule(@PathParam("id") Long id, @Valid RuleRequest req) {
        return categorization.updateRule(id, req);
    }

    @DELETE
    @Path("/categorization-rules/{id}")
    public Response deleteRule(@PathParam("id") Long id) {
        categorization.deleteRule(id);
        return Response.noContent().build();
    }
}
