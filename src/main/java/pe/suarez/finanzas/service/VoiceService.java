package pe.suarez.finanzas.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.VoiceDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Intérprete de gastos con Claude: a partir de lo que la persona dijo (respaldo de las reglas
 * del teléfono) o de una foto (voucher, boleta, captura de Yape) devuelve monto, tipo,
 * categoría, descripción y fecha. Solo funciona si hay ANTHROPIC_API_KEY.
 */
@ApplicationScoped
public class VoiceService {

    private static final Logger LOG = Logger.getLogger(VoiceService.class);

    private static final String SYSTEM = """
            Eres el intérprete de una app de gastos personales usada en Perú.
            Recibes la lista de categorías de la persona (con id) y una frase dicha por voz o una foto
            (voucher de tarjeta, boleta, captura de Yape o Plin, comprobante de transferencia).
            Devuelve el monto en soles (si dice "veinte con cincuenta" es 20.50; "mil doscientos" es 1200;
            en una boleta es el TOTAL, no un subtotal ni el vuelto), el tipo (EXPENSE si gastó o pagó;
            INCOME si recibió dinero, p. ej. "te yapearon"), el id de la categoría más probable (null si
            ninguna encaja de verdad), una descripción corta (comercio o a quién se pagó, sin repetir el
            monto ni el nombre de la categoría) y la fecha del comprobante en formato YYYY-MM-DD si se ve,
            o null. No inventes montos: si no se lee con claridad, devuelve null.
            """;

    @ConfigProperty(name = "app.voice.anthropic-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "app.voice.model", defaultValue = "claude-opus-5-5")
    String model;

    @Inject CategoryRepository catRepo;
    @Inject UserContext userContext;

    private AnthropicClient client;

    @PostConstruct
    void init() {
        if (apiKey.isPresent() && !apiKey.get().isBlank()) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey.get().trim()).build();
            LOG.infof("Intérprete activo con %s", model);
        } else {
            LOG.info("Intérprete desactivado: falta ANTHROPIC_API_KEY");
        }
    }

    public boolean available() {
        return client != null;
    }

    /** Frase dicha por voz, ya transcrita por el teléfono. */
    public VoiceParseResponse parse(VoiceParseRequest req) {
        return interpret(List.of(ContentBlockParam.ofText(
                TextBlockParam.builder().text("La persona dijo: \"" + req.text().trim() + "\"").build())));
    }

    /** Foto o captura de pantalla (JPEG/PNG/WEBP en base64, ya reducida por el teléfono). */
    public VoiceParseResponse parseImage(ImageParseRequest req) {
        Base64ImageSource.MediaType mediaType = switch (req.mediaType() == null ? "" : req.mediaType().toLowerCase()) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            default -> Base64ImageSource.MediaType.IMAGE_JPEG;
        };
        ImageBlockParam image = ImageBlockParam.builder()
                .source(Base64ImageSource.builder().mediaType(mediaType).data(req.imageBase64()).build())
                .build();
        return interpret(List.of(
                ContentBlockParam.ofImage(image),
                ContentBlockParam.ofText(TextBlockParam.builder()
                        .text("Esta es la foto del comprobante. Saca monto total, tipo, categoría, descripción y fecha.")
                        .build())));
    }

    private VoiceParseResponse interpret(List<ContentBlockParam> content) {
        if (client == null) throw new ApiException(ErrorCode.VOICE_UNAVAILABLE);
        Long uid = userContext.userId();
        List<Category> categories = catRepo.list("userId = ?1 AND archived = false ORDER BY type, name", uid);
        Map<Long, Category> byId = categories.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));

        String list = categories.stream()
                .map(c -> c.id + " = " + c.name + " (" + (c.type == TransactionType.INCOME ? "ingreso" : "gasto") + ")")
                .collect(Collectors.joining("\n"));

        StructuredMessageCreateParams<Parsed> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(SYSTEM)
                .outputConfig(Parsed.class)
                .addUserMessage("Categorías:\n" + list)
                .addUserMessageOfBlockParams(content)
                .build();

        Parsed parsed;
        try {
            parsed = client.messages().create(params).content().stream()
                    .flatMap(cb -> cb.text().stream())
                    .map(t -> t.text())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.VOICE_UNAVAILABLE, "El modelo no respondió"));
        } catch (AnthropicException e) {
            LOG.warnf("Intérprete falló: %s", e.getMessage());
            throw new ApiException(ErrorCode.VOICE_UNAVAILABLE, "El intérprete no está disponible ahora");
        }

        // Nunca confiar a ciegas: la categoría tiene que ser de la persona y el tipo, coherente
        Category cat = parsed.categoryId() != null ? byId.get(parsed.categoryId()) : null;
        String type = cat != null ? cat.type.name()
                : "INCOME".equalsIgnoreCase(parsed.type()) ? "INCOME" : "EXPENSE";
        BigDecimal amount = parsed.amount() != null && parsed.amount() > 0
                ? BigDecimal.valueOf(parsed.amount()).setScale(2, RoundingMode.HALF_UP) : null;
        String description = parsed.description() != null ? parsed.description().trim() : "";
        if (description.length() > 200) description = description.substring(0, 200);
        LocalDate date = null;
        if (parsed.date() != null && !parsed.date().isBlank()) {
            try {
                date = LocalDate.parse(parsed.date().trim());
                // Una fecha futura o de hace más de un año es casi seguro una lectura errada
                if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusYears(1))) date = null;
            } catch (DateTimeParseException ignored) {
                // sin fecha
            }
        }

        return new VoiceParseResponse(amount, type, cat != null ? cat.id : null, cat != null ? cat.name : null,
                description, date);
    }
}
