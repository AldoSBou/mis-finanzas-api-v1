package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.Transaction;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.TransactionDtos.TransactionFilter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Las agregaciones suman siempre {@code amountBase} (moneda base del usuario),
 * nunca {@code amount}, para no mezclar monedas.
 */
@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    private static final List<AllocationBucket> SAVINGS_BUCKETS =
            List.of(AllocationBucket.SAVINGS, AllocationBucket.INVESTMENT);

    @Inject
    EntityManager em;

    public Optional<Transaction> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }

    private record Query(String text, Map<String, Object> params) {}

    private static final Sort NEWEST_FIRST =
            Sort.by("transactionDate").descending().and("id", Sort.Direction.Descending);

    /** Arma la consulta con los filtros no nulos (todos se combinan con AND). */
    private static Query where(Long userId, TransactionFilter f) {
        StringBuilder q = new StringBuilder("userId = :uid AND transactionDate BETWEEN :from AND :to");
        Map<String, Object> p = new HashMap<>();
        p.put("uid", userId);
        p.put("from", f.from());
        p.put("to", f.to());
        if (f.accountId() != null) {
            q.append(" AND (accountId = :account OR toAccountId = :account)");
            p.put("account", f.accountId());
        }
        if (f.categoryId() != null) {
            q.append(" AND categoryId = :category");
            p.put("category", f.categoryId());
        }
        if (f.type() != null) {
            q.append(" AND type = :type");
            p.put("type", f.type());
        }
        if (f.text() != null && !f.text().isBlank()) {
            q.append(" AND lower(description) LIKE :text");
            p.put("text", "%" + f.text().trim().toLowerCase() + "%");
        }
        if (f.minAmount() != null) {
            q.append(" AND amount >= :min");
            p.put("min", f.minAmount());
        }
        if (f.maxAmount() != null) {
            q.append(" AND amount <= :max");
            p.put("max", f.maxAmount());
        }
        return new Query(q.toString(), p);
    }

    public List<Transaction> search(Long userId, TransactionFilter f, int page, int size) {
        Query q = where(userId, f);
        return find(q.text(), NEWEST_FIRST, q.params()).page(Page.of(page, size)).list();
    }

    public long count(Long userId, TransactionFilter f) {
        Query q = where(userId, f);
        return count(q.text(), q.params());
    }

    /** Todos los resultados (para exportar), del más reciente al más antiguo. */
    public List<Transaction> searchAll(Long userId, TransactionFilter f) {
        Query q = where(userId, f);
        return find(q.text(), NEWEST_FIRST, q.params()).list();
    }

    /** Suma de ingresos en el rango. Devuelve ZERO si no hay registros. */
    public BigDecimal sumIncome(Long userId, LocalDate from, LocalDate to) {
        BigDecimal result = em.createQuery("""
                        SELECT COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t
                        WHERE t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        """, BigDecimal.class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.INCOME)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * Top categorías de consumo (excluye categorías de ahorro/inversión).
     * Devuelve filas: [categoryId, categoryName, totalAmount].
     */
    public List<Object[]> sumConsumptionByCategory(Long userId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        SELECT c.id, c.name, COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t, Category c
                        WHERE t.categoryId = c.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND (c.defaultBucket IS NULL OR c.defaultBucket NOT IN (:savings))
                          AND t.transactionDate BETWEEN :from AND :to
                        GROUP BY c.id, c.name
                        ORDER BY 3 DESC
                        """, Object[].class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("savings", SAVINGS_BUCKETS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Gastos del rango por categoría (todas las de gasto), en moneda base. */
    public Map<Long, BigDecimal> sumExpensesByCategory(Long userId, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> result = new HashMap<>();
        em.createQuery("""
                        SELECT t.categoryId, COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t
                        WHERE t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        GROUP BY t.categoryId
                        """, Object[].class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()
                .forEach(row -> result.put((Long) row[0], (BigDecimal) row[1]));
        return result;
    }

    /**
     * Agrupa gastos por bucket de su categoría (incluye categorías de ahorro heredadas).
     * Devuelve filas: [bucket (puede ser null), totalAmount].
     */
    public List<Object[]> sumExpensesByBucket(Long userId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        SELECT c.defaultBucket, COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t, Category c
                        WHERE t.categoryId = c.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        GROUP BY c.defaultBucket
                        """, Object[].class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /**
     * Transferencias agrupadas por tipo de cuenta origen y destino.
     * Devuelve filas: [AccountType origen, AccountType destino, totalAmountBase].
     */
    public List<Object[]> sumTransfersByAccountTypes(Long userId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        SELECT a.type, b.type, COALESCE(SUM(t.amountBase), 0)
                        FROM Transaction t, Account a, Account b
                        WHERE t.accountId = a.id
                          AND t.toAccountId = b.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        GROUP BY a.type, b.type
                        """, Object[].class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.TRANSFER)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Último movimiento del usuario en una moneda (para sugerir su tipo de cambio). */
    public Optional<Transaction> latestInCurrency(Long userId, String currency) {
        return find("userId = ?1 AND currency = ?2",
                Sort.by("transactionDate").descending().and("id", Sort.Direction.Descending),
                userId, currency)
                .firstResultOptional();
    }

    /** Última compra de {@code currency} con moneda base: transferencia base → cuenta en {@code currency}. */
    public Optional<Transaction> latestTransferInto(Long userId, String currency, String baseCurrency) {
        return em.createQuery("""
                        SELECT t FROM Transaction t, Account b
                        WHERE t.toAccountId = b.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND b.currency = :currency
                          AND t.currency = :base
                        ORDER BY t.transactionDate DESC, t.id DESC
                        """, Transaction.class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.TRANSFER)
                .setParameter("currency", currency)
                .setParameter("base", baseCurrency)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
