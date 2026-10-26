package pe.suarez.finanzas.service;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.Receipt;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.ReceiptDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionRequest;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionResponse;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Boletas de supermercado, bodega o farmacia leídas desde una foto. El modelo lista cada
 * producto con su categoría; aquí se agrupan por categoría, se cuadra la suma con el total y,
 * cuando la persona confirma, se crea un movimiento por grupo más la boleta con el detalle.
 */
@ApplicationScoped
public class ReceiptService {

    private static final String SYSTEM = """
            Eres el lector de boletas de una app de gastos personales usada en Perú.
            Recibes las categorías de GASTO de la persona (con id) y una o dos fotos de la misma boleta
            (boleta o factura electrónica de supermercado, bodega, farmacia, tienda de conveniencia como
            Tambo u Oxxo, ferretería; o un voucher/captura sin detalle).
            Devuelve el comercio, la fecha, el TOTAL pagado y TODAS las líneas de producto, cada una con su
            importe y el id de la categoría que mejor encaja. Guía: comida, bebidas, frutas, verduras, abarrotes,
            snacks y licor → la categoría de alimentos; detergente, lejía, papel higiénico, bolsas, esponjas,
            ambientadores → limpieza; shampoo, jabón, pasta dental, pañales, toallas higiénicas, cosméticos →
            cuidado personal; comida o arena de mascotas → mascotas; medicinas → salud; ropa y calzado → ropa;
            menaje, focos, herramientas, decoración → vivienda; comida preparada en restaurante → restaurantes.
            Si ningún id encaja de verdad, deja categoryId null y propone un nombre corto en suggestedCategory.
            Los nombres vienen abreviados ("DET ARIEL 2KG", "LCH GLORIA EVAP"): interprétalos. Los descuentos
            ("DSCTO", "PROMO") van como línea con importe negativo. Las líneas de cantidad × precio unitario
            valen por el importe final de la línea. No incluyas subtotales, IGV, vuelto ni efectivo entregado
            como líneas. Si una foto corta la boleta y la otra la continúa, no repitas líneas. No inventes:
            si un importe no se lee, pon null.
            """;

    @Inject VoiceService voice;
    @Inject CategoryRepository catRepo;
    @Inject AccountRepository accountRepo;
    @Inject TransactionService txService;
    @Inject UserContext userContext;

    /** Lee la foto y devuelve los grupos por categoría para confirmar. */
    public ReceiptPreview parse(ReceiptParseRequest req) {
        Long uid = userContext.userId();
        List<Category> categories = catRepo.list(
                "userId = ?1 AND archived = false AND type = ?2 ORDER BY name", uid, TransactionType.EXPENSE);
        Map<Long, Category> byId = categories.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));

        List<ContentBlockParam> content = new ArrayList<>();
        for (String image : req.images()) content.add(VoiceService.imageBlock(image, req.mediaType()));
        content.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(req.images().size() > 1
                        ? "Estas son " + req.images().size() + " fotos de la misma boleta, en orden. Lista todas las líneas sin repetir."
                        : "Esta es la boleta. Lista todas las líneas de producto con su categoría.")
                .build()));

        // Lo que la persona ya clasificó en boletas anteriores: va al prompt como ejemplos y,
        // si el producto se repite igual, manda sobre lo que diga el modelo.
        Map<String, Long> learned = learnedCategories(uid, byId);
        StringBuilder context = new StringBuilder("Categorías de gasto:\n").append(VoiceService.categoryList(categories));
        if (!learned.isEmpty()) {
            context.append("\n\nAsí clasificó esta persona productos de boletas anteriores (producto → id de categoría). ")
                    .append("Respeta estas decisiones y úsalas de guía para productos parecidos:\n");
            learned.entrySet().stream().limit(PROMPT_EXAMPLES)
                    .forEach(e -> context.append(e.getKey()).append(" → ").append(e.getValue()).append('\n'));
        }

        ParsedReceipt parsed = voice.ask(SYSTEM, ParsedReceipt.class, 4096L, context.toString(), content);

        // Líneas válidas, con categoría verificada (tiene que ser de la persona)
        List<ReceiptItem> items = new ArrayList<>();
        if (parsed.items() != null) {
            for (ParsedItem it : parsed.items()) {
                if (it == null || it.amount() == null || it.amount() == 0) continue;
                Category cat = it.categoryId() != null ? byId.get(it.categoryId()) : null;
                String suggested = cat == null && it.suggestedCategory() != null && !it.suggestedCategory().isBlank()
                        ? cut(it.suggestedCategory().trim(), 40) : null;
                String desc = it.description() != null && !it.description().isBlank() ? cut(it.description().trim(), 120) : "Producto";
                items.add(new ReceiptItem(desc, money(it.amount()), cat != null ? cat.id : null, suggested));
            }
        }
        items = applyLearned(items, learned);
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.RECEIPT_INVALID,
                    "No pude leer productos en la imagen. Prueba con una foto más cerca y con buena luz.");
        }

        // Agrupar por categoría (las que no encajaron, por el nombre propuesto)
        Map<String, List<ReceiptItem>> grouped = new LinkedHashMap<>();
        for (ReceiptItem it : items) {
            String key = it.categoryId() != null ? "c" + it.categoryId()
                    : "s" + (it.suggestedCategory() != null ? it.suggestedCategory().toLowerCase() : "otros");
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }
        List<MutableGroup> groups = new ArrayList<>();
        for (List<ReceiptItem> list : grouped.values()) {
            ReceiptItem first = list.get(0);
            Category cat = first.categoryId() != null ? byId.get(first.categoryId()) : null;
            groups.add(new MutableGroup(cat, first.suggestedCategory(), list));
        }

        // Cuadre con el total: la diferencia (descuentos globales, redondeo) va al grupo más grande
        BigDecimal sum = groups.stream().map(MutableGroup::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = parsed.total() != null && parsed.total() > 0 ? money(parsed.total()) : sum;
        MutableGroup largest = groups.stream().max(Comparator.comparing(g -> g.amount().abs())).orElseThrow();
        BigDecimal diff = total.subtract(sum);
        if (diff.signum() != 0) {
            largest.items.add(new ReceiptItem(diff.signum() < 0 ? "Descuento / ajuste" : "Ajuste", diff, largest.categoryId(), null));
        }
        // Un grupo que quedó en cero o negativo (solo descuentos) se funde con el más grande
        for (MutableGroup g : new ArrayList<>(groups)) {
            if (g != largest && g.amount().signum() <= 0) {
                largest.items.addAll(g.items.stream()
                        .map(i -> new ReceiptItem(i.description(), i.amount(), largest.categoryId(), null)).toList());
                groups.remove(g);
            }
        }
        if (largest.amount().signum() <= 0) {
            throw new ApiException(ErrorCode.RECEIPT_INVALID, "Los importes leídos no cuadran. Prueba con otra foto.");
        }

        LocalDate date = null;
        if (parsed.date() != null && !parsed.date().isBlank()) {
            try {
                date = LocalDate.parse(parsed.date().trim());
                if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusYears(1))) date = null;
            } catch (DateTimeParseException ignored) {
                // sin fecha
            }
        }
        String currency = TransactionService.baseCurrency(uid);
        if (parsed.currency() != null && parsed.currency().trim().matches("[A-Za-z]{3}")) {
            currency = parsed.currency().trim().toUpperCase();
        }
        String merchant = parsed.merchant() != null && !parsed.merchant().isBlank() ? cut(parsed.merchant().trim(), 120) : null;

        return new ReceiptPreview(merchant, date, total, currency,
                groups.stream().map(MutableGroup::toDto).toList());
    }

    /** La persona confirmó: crea la boleta y un movimiento por grupo, todo o nada. */
    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest req) {
        Long uid = userContext.userId();
        Account account = accountRepo.findByIdForUser(req.accountId(), uid)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        BigDecimal sum = req.groups().stream().map(ReceiptGroupRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(req.total().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new ApiException(ErrorCode.RECEIPT_INVALID,
                    "Los grupos suman " + sum.setScale(2, RoundingMode.HALF_UP) + " y el total es " + req.total());
        }

        Receipt r = new Receipt();
        r.userId = uid;
        r.merchant = req.merchant() != null && !req.merchant().isBlank() ? req.merchant().trim() : null;
        r.receiptDate = req.date();
        r.total = req.total().setScale(2, RoundingMode.HALF_UP);
        r.currency = account.currency;
        r.accountId = account.id;
        for (ReceiptGroupRequest g : req.groups()) {
            if (g.items() == null) continue;
            for (ReceiptItemRequest it : g.items()) {
                r.items.add(new Receipt.Item(it.description(), it.amount(), g.categoryId()));
            }
        }
        r.persist();

        String merchant = r.merchant != null ? r.merchant : "Boleta";
        for (ReceiptGroupRequest g : req.groups()) {
            String summary = g.items() == null ? "" : g.items().stream()
                    .map(ReceiptItemRequest::description)
                    .filter(d -> d != null && !d.isBlank())
                    .limit(4)
                    .collect(Collectors.joining(", "));
            String description = cut(summary.isBlank() ? merchant : merchant + " · " + summary.toLowerCase(), 200);
            TransactionRequest txReq = new TransactionRequest(TransactionType.EXPENSE, account.id, g.categoryId(), null,
                    g.amount().setScale(2, RoundingMode.HALF_UP), null, req.exchangeRate(), req.date(), description, null);
            Transaction t = txService.createWithinTransaction(txReq, uid);
            t.receiptId = r.id;
        }
        return get(r.id);
    }

    public ReceiptResponse get(Long id) {
        Long uid = userContext.userId();
        Receipt r = find(id, uid);
        List<Transaction> txs = Transaction.list("receiptId = ?1 AND userId = ?2 ORDER BY amount DESC", r.id, uid);
        List<TransactionResponse> responses = txs.stream().map(t -> txService.get(t.id)).toList();
        List<ReceiptItem> items = r.items.stream()
                .map(i -> new ReceiptItem(i.description, i.amount, i.categoryId, null)).toList();
        return new ReceiptResponse(r.id, r.merchant, r.receiptDate, r.total, r.currency, r.accountId, items, responses);
    }

    /** Deshacer: borra la boleta y sus movimientos. */
    @Transactional
    public void delete(Long id) {
        Long uid = userContext.userId();
        Receipt r = find(id, uid);
        Transaction.delete("receiptId = ?1 AND userId = ?2", r.id, uid);
        r.delete();
    }

    private Receipt find(Long id, Long uid) {
        Receipt r = Receipt.find("id = ?1 AND userId = ?2", id, uid).firstResult();
        if (r == null) throw new ApiException(ErrorCode.RECEIPT_NOT_FOUND);
        return r;
    }

    /** Cuántos ejemplos de clasificaciones anteriores van al prompt (los más recientes). */
    static final int PROMPT_EXAMPLES = 150;
    /** Cuántas boletas recientes se miran para aprender. */
    private static final int LEARN_RECEIPTS = 60;

    /**
     * Producto (normalizado) → categoría, según las boletas que la persona ya confirmó.
     * La más reciente manda. Solo categorías que siguen activas.
     */
    Map<String, Long> learnedCategories(Long uid, Map<Long, Category> activeById) {
        List<Receipt> recent = Receipt.find("userId = ?1 ORDER BY createdAt DESC", uid).page(0, LEARN_RECEIPTS).list();
        Map<String, Long> learned = new LinkedHashMap<>();
        for (Receipt r : recent) {
            for (Receipt.Item it : r.items) {
                if (it.categoryId == null || it.description == null || !activeById.containsKey(it.categoryId)) continue;
                String key = normalize(it.description);
                if (key.length() < 3 || key.startsWith("ajuste") || key.startsWith("descuento")) continue;
                learned.putIfAbsent(key, it.categoryId);
            }
        }
        return learned;
    }

    /** Si el producto ya se clasificó antes con el mismo nombre, esa categoría manda sobre el modelo. */
    static List<ReceiptItem> applyLearned(List<ReceiptItem> items, Map<String, Long> learned) {
        if (learned.isEmpty()) return items;
        List<ReceiptItem> out = new ArrayList<>(items.size());
        for (ReceiptItem it : items) {
            Long known = learned.get(normalize(it.description()));
            out.add(known != null && !known.equals(it.categoryId())
                    ? new ReceiptItem(it.description(), it.amount(), known, null)
                    : it);
        }
        return out;
    }

    /** Minúsculas, sin tildes ni signos, espacios simples: "LCH. GLORIA  EVAP" = "lch gloria evap". */
    static String normalize(String s) {
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9ñ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Grupo en construcción mientras se cuadra la boleta. */
    private static final class MutableGroup {
        final Category category;
        final String suggested;
        final List<ReceiptItem> items;

        MutableGroup(Category category, String suggested, List<ReceiptItem> items) {
            this.category = category;
            this.suggested = suggested;
            this.items = new ArrayList<>(items);
        }

        Long categoryId() {
            return category != null ? category.id : null;
        }

        BigDecimal amount() {
            return items.stream().map(ReceiptItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        ReceiptGroup toDto() {
            return new ReceiptGroup(categoryId(), category != null ? category.name : null,
                    category == null ? (suggested != null ? suggested : "Otros") : null, amount(), List.copyOf(items));
        }
    }
}
