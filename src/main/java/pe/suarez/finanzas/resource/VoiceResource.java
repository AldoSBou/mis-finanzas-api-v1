package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.VoiceDtos.*;
import pe.suarez.finanzas.service.VoiceService;

import java.util.Map;

/** Respaldo del registro por voz: interpreta una frase cuando el teléfono no la entendió. */
@Path("/api/voice")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Voz")
public class VoiceResource {

    @Inject VoiceService service;

    /** GET /api/voice: si el intérprete está configurado en el servidor. */
    @GET
    public Map<String, Boolean> status() {
        return Map.of("available", service.available());
    }

    /** POST /api/voice/parse {"text": "veinte con cincuenta almuerzo"} → monto, tipo, categoría y descripción. */
    @POST
    @Path("/parse")
    public VoiceParseResponse parse(@Valid VoiceParseRequest req) {
        return service.parse(req);
    }

    /** POST /api/voice/parse-image {"imageBase64": "...", "mediaType": "image/jpeg"} → lo mismo a partir de una foto, más la fecha. */
    @POST
    @Path("/parse-image")
    public VoiceParseResponse parseImage(@Valid ImageParseRequest req) {
        return service.parseImage(req);
    }
}
