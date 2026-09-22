package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.Account;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.RecurringTransaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.RecurringDtos.*;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionRequest;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.repository.AccountRepository;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.repository.RecurringRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Movimientos recurrentes. El backend puede estar dormido (Railway Serverless), así que
 * no hay tarea programada: las ocurrencias vencidas se registran al abrir la app
 * ({@link #materializeDue()}), llamado desde los endpoints de lectura principales.
 */
@ApplicationScoped
public class RecurringService {

    private static final Logger LOG = Logger.getLogger(RecurringService.class);

    /** Tope de ocurrencias a registrar de una vez (evita bucles con fechas muy antiguas). */
    private static final int MAX_CATCH_UP = 60;
    /** Tope de ocurrencias por recurrente al listar próximos (un semanal da ~5 por mes). */
    private static final int MAX_UPCOMING = 40;

    @Inject RecurringRepository repo;
    @Inject TransactionService txService;
    @Inject AccountRepository accountRepo;
    @Inject CategoryRepository catRepo;
    @Inject UserContext userContext;

    public List<RecurringResponse> list() {
        Long uid = userContext.userId();
        return toResponses(repo.listForUser(uid), uid);
    }

    @Transactional
    public RecurringResponse create(RecurringRequest req) {
        Long uid = userContext.userId();
        RecurringTransaction r = new RecurringTransaction();
        r.userId = uid;
        apply(r, req);
        r.persist();
        materialize(r, LocalDate.now());
        return toResponses(List.of(r), uid).get(0);
    }

    @Transactional
    public RecurringResponse update(Long id, RecurringRequest req) {
        Long uid = userContext.userId();
        RecurringTransaction r = find(id, uid);
        apply(r, req);
        materialize(r, LocalDate.now());
        return toResponses(List.of(r), uid).get(0);
    }

    /** Elimina el recurrente. Los movimientos ya registrados se conservan. */
    @Transactional
    public void delete(Long id) {
        find(id, userContext.userId()).delete();
    }

    /** Registra la próxima ocurrencia (pendiente o adelantada) y avanza a la siguiente. */
    @Transactional
    public void register(Long id, RegisterOccurrenceRequest req) {
        Long uid = userContext.userId();
        RecurringTransaction r = repo.findByIdForUpdate(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.RECURRING_NOT_FOUND));
        if (!r.active) throw new ApiException(ErrorCode.RECURRING_INACTIVE);
        LocalDate date = req != null && req.date() != null ? req.date() : LocalDate.now();
        txService.createFromRecurring(r, date,
                req != null ? req.amount() : null,
                req != null ? req.exchangeRate() : null);
        r.advance();
    }

    /** Salta la próxima ocurrencia sin registrar nada. */
    @Transactional
    public void skip(Long id) {
        Long uid = userContext.userId();
        RecurringTransaction r = repo.findByIdForUpdate(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.RECURRING_NOT_FOUND));
        if (!r.active) throw new ApiException(ErrorCode.RECURRING_INACTIVE);
        r.advance();
    }

    /** Registra todas las ocurrencias automáticas vencidas del usuario actual. */
    @Transactional
    public void materializeDue() {
        LocalDate today = LocalDate.now();
        for (RecurringTransaction r : repo.dueAutoForUpdate(userContext.userId(), today)) {
            materialize(r, today);
        }
    }

    /**
     * Ocurrencias entre hoy y {@code until}, más las pendientes ya vencidas (las de
     * confirmación manual). Montos aproximados en moneda base.
     */
    public List<UpcomingItem> upcoming(Long uid, LocalDate until) {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> active = repo.activeForUser(uid);
        Map<Long, Account> accounts = accountsFor(active, uid);
        Map<Long, Category> categories = categoriesFor(active, uid);
        String base = TransactionService.baseCurrency(uid);

        List<UpcomingItem> items = new ArrayList<>();
        for (RecurringTransaction r : active) {
            Account from = accounts.get(r.accountId);
            Account to = r.toAccountId != null ? accounts.get(r.toAccountId) : null;
            Category c = r.categoryId != null ? categories.get(r.categoryId) : null;
            BigDecimal amountBase = baseAmount(r, from, to, base);
            LocalDate d = r.nextDate;
            for (int n = 0; n < MAX_UPCOMING && !d.isAfter(until)
                    && (r.endDate == null || !d.isAfter(r.endDate)); n++) {
                items.add(new UpcomingItem(r.id, r.type, r.categoryId, r.description,
                        c != null ? c.name : null,
                        from != null ? from.name : null,
                        to != null ? to.name : null,
                        r.amount, from != null ? from.currency : base, amountBase,
                        d, !d.isAfter(today), r.autoCreate));
                d = r.frequency.next(d, r.anchorDay);
            }
        }
        items.sort(Comparator.comparing(UpcomingItem::date));
        return items;
    }

    /**
     * Efecto de una ocurrencia sobre el "disponible" del panel
     * (ingresos − gastos − ahorro): ingresos suman, gastos restan, transferencias
     * hacia ahorro restan y desde ahorro suman.
     */
    public BigDecimal effectOnAvailable(UpcomingItem item, Map<Long, AllocationBucket[]> transferBuckets) {
        return switch (item.type()) {
            case INCOME -> item.amountBase();
            case EXPENSE -> item.amountBase().negate();
            case TRANSFER -> {
                AllocationBucket[] b = transferBuckets.get(item.recurringId());
                if (b == null || b[0] == b[1]) yield BigDecimal.ZERO;
                if (b[1] != null) yield item.amountBase().negate();
                yield item.amountBase();
            }
        };
    }

    /** Por recurrente de transferencia: [bucket de ahorro origen, bucket de ahorro destino]. */
    public Map<Long, AllocationBucket[]> transferBuckets(Long uid) {
        List<RecurringTransaction> transfers = repo.activeForUser(uid).stream()
                .filter(r -> r.type == TransactionType.TRANSFER).toList();
        Map<Long, Account> accounts = accountsFor(transfers, uid);
        Map<Long, AllocationBucket[]> result = new HashMap<>();
        for (RecurringTransaction r : transfers) {
            Account from = accounts.get(r.accountId);
            Account to = accounts.get(r.toAccountId);
            if (from == null || to == null) continue;
            result.put(r.id, new AllocationBucket[]{from.type.savingsBucket(), to.type.savingsBucket()});
        }
        return result;
    }

    // ---------------------------------------------------------------

    private void apply(RecurringTransaction r, RecurringRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio");
        }
        // Mismas reglas que un movimiento normal (cuentas, categoría, tipo de cambio...)
        txService.validate(new TransactionRequest(
                req.type(), req.accountId(), req.categoryId(), req.toAccountId(),
                req.amount(), req.toAmount(), req.exchangeRate(), req.startDate(), req.description(), null));

        boolean transfer = req.type() == TransactionType.TRANSFER;
        r.type = req.type();
        r.accountId = req.accountId();
        r.categoryId = transfer ? null : req.categoryId();
        r.toAccountId = transfer ? req.toAccountId() : null;
        r.amount = req.amount();
        r.toAmount = transfer ? req.toAmount() : null;
        r.exchangeRate = req.exchangeRate();
        r.description = req.description();
        r.frequency = req.frequency();
        r.anchorDay = req.startDate().getDayOfMonth();
        r.nextDate = req.startDate();
        r.endDate = req.endDate();
        r.autoCreate = req.autoCreate();
        r.active = req.endDate() == null || !req.startDate().isAfter(req.endDate());
    }

    /**
     * Registra las ocurrencias automáticas vencidas de {@code r}. Si una falla (p. ej. la
     * cuenta se archivó), el recurrente pasa a confirmación manual y queda como pendiente
     * en el panel, en vez de romper la carga de la app.
     */
    private void materialize(RecurringTransaction r, LocalDate today) {
        for (int n = 0; n < MAX_CATCH_UP && r.active && r.autoCreate && !r.nextDate.isAfter(today); n++) {
            try {
                txService.createFromRecurring(r, r.nextDate, null, null);
            } catch (ApiException e) {
                LOG.warnf("Recurrente %d no se pudo registrar (%s); pasa a confirmación manual",
                        r.id, e.getMessage());
                r.autoCreate = false;
                return;
            }
            r.advance();
        }
    }

    private BigDecimal baseAmount(RecurringTransaction r, Account from, Account to, String base) {
        if (from == null || from.currency.equals(base)) return r.amount;
        if (to != null && to.currency.equals(base) && r.toAmount != null) return r.toAmount;
        BigDecimal rate = r.exchangeRate != null ? r.exchangeRate : BigDecimal.ONE;
        return r.amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private RecurringTransaction find(Long id, Long uid) {
        return repo.findByIdForUser(id, uid)
                .orElseThrow(() -> new ApiException(ErrorCode.RECURRING_NOT_FOUND));
    }

    private Map<Long, Account> accountsFor(List<RecurringTransaction> list, Long uid) {
        return accountRepo.mapByIds(list.stream()
                .flatMap(r -> Stream.of(r.accountId, r.toAccountId))
                .filter(Objects::nonNull).distinct().toList(), uid);
    }

    private Map<Long, Category> categoriesFor(List<RecurringTransaction> list, Long uid) {
        var ids = list.stream().map(r -> r.categoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return catRepo.list("id IN ?1 AND userId = ?2", ids, uid).stream()
                .collect(Collectors.toMap(c -> c.id, c -> c));
    }

    private List<RecurringResponse> toResponses(List<RecurringTransaction> list, Long uid) {
        Map<Long, Account> accounts = accountsFor(list, uid);
        Map<Long, Category> categories = categoriesFor(list, uid);
        return list.stream().map(r -> {
            Account from = accounts.get(r.accountId);
            Account to = r.toAccountId != null ? accounts.get(r.toAccountId) : null;
            Category c = r.categoryId != null ? categories.get(r.categoryId) : null;
            return new RecurringResponse(r.id, r.type,
                    r.accountId, from != null ? from.name : null,
                    r.toAccountId, to != null ? to.name : null,
                    r.categoryId, c != null ? c.name : null, c != null ? c.color : null,
                    r.amount, from != null ? from.currency : null,
                    r.toAmount, r.exchangeRate, r.description,
                    r.frequency, r.nextDate, r.endDate, r.autoCreate, r.active);
        }).toList();
    }
}
