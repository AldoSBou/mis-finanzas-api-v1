package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.CategorizationRule;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.ImportDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.security.UserContext;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sugiere categorías a partir de la descripción: primero las reglas del usuario
 * ("contiene X"), luego su historial: la misma descripción o una muy parecida ya categorizada.
 */
@ApplicationScoped
public class CategorizationService {

    /** Cuánto historial se usa para aprender (suficiente para uso personal). */
    private static final int HISTORY_MONTHS = 18;
    /** Palabras en común mínimas (Jaccard) para considerar dos descripciones el mismo comercio. */
    private static final double MIN_SIMILARITY = 0.6;

    @Inject CategoryRepository catRepo;
    @Inject UserContext userContext;

    public record Suggestion(Long categoryId, SuggestionSource source) {}

    /** Precalcula reglas e historial una vez para sugerir muchas filas. */
    public final class Suggester {
        private final List<CategorizationRule> rules;
        /** Por tipo: descripción normalizada → categoría, del uso más antiguo al más reciente */
        private final Map<TransactionType, LinkedHashMap<String, Long>> history = new EnumMap<>(TransactionType.class);
        private final Map<Long, Category> categories;

        Suggester(Long uid) {
            this.categories = catRepo.listForUser(uid, false).stream()
                    .collect(Collectors.toMap(c -> c.id, c -> c));
            // Reglas más específicas (patrón más largo) primero
            this.rules = CategorizationRule.<CategorizationRule>list("userId", uid).stream()
                    .filter(r -> categories.containsKey(r.categoryId))
                    .sorted(Comparator.comparingInt((CategorizationRule r) -> normalize(r.pattern).length()).reversed())
                    .toList();
            // Del más antiguo al más reciente: al re-insertar, la entrada pasa al final,
            // así el orden refleja el uso más reciente
            Transaction.<Transaction>list(
                    "userId = ?1 AND categoryId IS NOT NULL AND description IS NOT NULL AND transactionDate >= ?2 "
                            + "ORDER BY transactionDate, id",
                    uid, LocalDate.now().minusMonths(HISTORY_MONTHS))
                    .forEach(t -> {
                        String key = normalize(t.description);
                        if (key.isEmpty()) return;
                        var byType = history.computeIfAbsent(t.type, k -> new LinkedHashMap<>());
                        byType.remove(key);
                        byType.put(key, t.categoryId);
                    });
        }

        public Suggestion suggest(String description, TransactionType type) {
            if (description == null || description.isBlank()) return new Suggestion(null, SuggestionSource.NONE);
            String norm = normalize(description);
            for (CategorizationRule r : rules) {
                Category c = categories.get(r.categoryId);
                if (c.type == type && norm.contains(normalize(r.pattern))) {
                    return new Suggestion(c.id, SuggestionSource.RULE);
                }
            }
            Long learned = learnedCategory(norm, history.getOrDefault(type, new LinkedHashMap<>()));
            if (learned != null && categories.containsKey(learned)) {
                return new Suggestion(learned, SuggestionSource.HISTORY);
            }
            return new Suggestion(null, SuggestionSource.NONE);
        }

        /** Coincidencia exacta o, si no hay, la descripción más parecida (a igual parecido, la más reciente). */
        private Long learnedCategory(String norm, LinkedHashMap<String, Long> byType) {
            Long exact = byType.get(norm);
            if (exact != null) return exact;
            Set<String> tokens = tokens(norm);
            Long best = null;
            double bestScore = MIN_SIMILARITY;
            for (var e : byType.entrySet()) {
                double score = similarity(tokens, tokens(e.getKey()));
                if (score >= bestScore) {
                    bestScore = score;
                    best = e.getValue();
                }
            }
            return best;
        }
    }

    public Suggester suggester() {
        return new Suggester(userContext.userId());
    }

    /**
     * Minúsculas, sin tildes, sin números ni símbolos: "UBER *TRIP 4821 LIMA" y
     * "Uber Trip 9913 Lima" quedan igual ("uber trip lima").
     */
    public static String normalize(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z]+", " ")
                .trim();
        return s.replaceAll("\\s+", " ");
    }

    /** Palabras de 3+ letras (se ignoran "de", "la", etc.). */
    static Set<String> tokens(String normalized) {
        Set<String> set = new HashSet<>();
        for (String t : normalized.split(" ")) {
            if (t.length() >= 3) set.add(t);
        }
        return set;
    }

    /** Jaccard: palabras en común / palabras totales. */
    static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }

    // ---------- CRUD de reglas ----------

    public List<RuleResponse> listRules() {
        Long uid = userContext.userId();
        Map<Long, String> names = catRepo.listForUser(uid, true).stream()
                .collect(Collectors.toMap(c -> c.id, c -> c.name));
        return CategorizationRule.<CategorizationRule>list("userId = ?1 ORDER BY pattern", uid).stream()
                .map(r -> new RuleResponse(r.id, r.pattern, r.categoryId, names.get(r.categoryId)))
                .toList();
    }

    @Transactional
    public RuleResponse createRule(RuleRequest req) {
        Long uid = userContext.userId();
        Category c = validCategory(req.categoryId(), uid);
        String pattern = cleanPattern(req.pattern());
        CategorizationRule r = new CategorizationRule();
        r.userId = uid;
        r.pattern = pattern;
        r.categoryId = c.id;
        r.persist();
        return new RuleResponse(r.id, r.pattern, c.id, c.name);
    }

    @Transactional
    public RuleResponse updateRule(Long id, RuleRequest req) {
        Long uid = userContext.userId();
        CategorizationRule r = findRule(id, uid);
        Category c = validCategory(req.categoryId(), uid);
        r.pattern = cleanPattern(req.pattern());
        r.categoryId = c.id;
        return new RuleResponse(r.id, r.pattern, c.id, c.name);
    }

    @Transactional
    public void deleteRule(Long id) {
        findRule(id, userContext.userId()).delete();
    }

    private CategorizationRule findRule(Long id, Long uid) {
        return CategorizationRule.<CategorizationRule>find("id = ?1 AND userId = ?2", id, uid)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Regla no encontrada"));
    }

    private Category validCategory(Long categoryId, Long uid) {
        return catRepo.findByIdForUser(categoryId, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** El patrón debe tener letras: la comparación ignora números y símbolos. */
    private static String cleanPattern(String pattern) {
        String p = pattern.trim();
        if (normalize(p).isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "El texto de la regla debe tener letras (los números y símbolos se ignoran al comparar)");
        }
        return p;
    }
}
