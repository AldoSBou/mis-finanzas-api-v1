package pe.suarez.finanzas.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Respaldo del intérprete de voz del teléfono: cuando las reglas locales no reconocen la
 * categoría, Claude lee la frase junto con las categorías de la persona y devuelve
 * monto, tipo, categoría y descripción. Solo funciona si hay ANTHROPIC_API_KEY.
 */
@ApplicationScoped
public class VoiceService {

    private static final Logger LOG = Logger.getLogger(VoiceService.class);

    private static final String SYSTEM = """
            Eres el intérprete de voz de una app de gastos personales usada en Perú.
            Recibes lo que la persona dijo (transcrito por el teléfono, puede tener errores) y la lista de
            sus categorías con id. Devuelve el monto en soles (si dice "veinte con cincuenta" es 20.50;
            "mil doscientos" es 1200), el tipo (EXPENSE si gastó o pagó; INCOME si recibió dinero), el id
            de la categoría más probable (null si ninguna encaja de verdad) y una descripción corta de en
            qué fue, sin repetir el monto ni el nombre de la categoría. No inventes montos.
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
            LOG.infof("Intérprete de voz activo con %s", model);
        } else {
            LOG.info("Intérprete de voz desactivado: falta ANTHROPIC_API_KEY");
        }
    }

    public boolean available() {
        return client != null;
    }

    public VoiceParseResponse parse(VoiceParseRequest req) {
        if (client == null) throw new ApiException(ErrorCode.VOICE_UNAVAILABLE);
        Long uid = userContext.userId();
        List<Category> categories = catRepo.list("userId = ?1 AND archived = false ORDER BY type, name", uid);
        Map<Long, Category> byId = categories.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));

        String list = categories.stream()
                .map(c -> c.id + " = " + c.name + " (" + (c.type == TransactionType.INCOME ? "ingreso" : "gasto") + ")")
                .collect(Collectors.joining("\n"));
        String user = "Categorías:\n" + list + "\n\nLa persona dijo: \"" + req.text().trim() + "\"";

        StructuredMessageCreateParams<Parsed> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(SYSTEM)
                .outputConfig(Parsed.class)
                .addUserMessage(user)
                .build();

        Parsed parsed;
        try {
            parsed = client.messages().create(params).content().stream()
                    .flatMap(cb -> cb.text().stream())
                    .map(t -> t.text())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.VOICE_UNAVAILABLE, "El modelo no respondió"));
        } catch (AnthropicException e) {
            LOG.warnf("Intérprete de voz falló: %s", e.getMessage());
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

        return new VoiceParseResponse(amount, type, cat != null ? cat.id : null, cat != null ? cat.name : null, description);
    }
}
