package pe.suarez.finanzas.api;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint para que su respuesta NO sea envuelta automáticamente
 * en {@link ApiResponse}. Útil para webhooks, exports CSV, o respuestas
 * con formato esperado por terceros.
 *
 * <pre>{@code
 *   @GET
 *   @Path("/export.csv")
 *   @RawResponse
 *   @Produces("text/csv")
 *   public Response export() { ... }
 * }</pre>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RawResponse {
}
