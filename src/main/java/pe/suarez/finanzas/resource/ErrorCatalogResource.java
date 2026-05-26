package pe.suarez.finanzas.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.api.ErrorCatalog;
import pe.suarez.finanzas.api.ErrorCode;

import java.util.List;

/**
 * Expone el catálogo de errores. Útil para que el frontend tenga la lista
 * actualizada y para documentación / soporte.
 */
@Path("/api/errors")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@Tag(name = "Catálogo de errores")
public class ErrorCatalogResource {

    public record ErrorEntry(String code, int httpStatus, String message, String cause) {}

    @GET
    public List<ErrorEntry> list() {
        return ErrorCatalog.all().entrySet().stream()
                .map(e -> new ErrorEntry(
                        e.getKey().name(),
                        e.getKey().httpStatus(),
                        e.getValue().message(),
                        e.getValue().cause()))
                .toList();
    }
}
