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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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

    private record Filter(String query, Object[] params) {}

    /** Movimientos del mes; si {@code accountId} no es null, solo los que tocan esa cuenta. */
    private Filter monthFilter(Long userId, YearMonth ym, Long accountId) {
        String q = "userId = ?1 AND transactionDate BETWEEN ?2 AND ?3";
        if (accountId == null) {
            return new Filter(q, new Object[]{userId, ym.atDay(1), ym.atEndOfMonth()});
        }
        return new Filter(q + " AND (accountId = ?4 OR toAccountId = ?4)",
                new Object[]{userId, ym.atDay(1), ym.atEndOfMonth(), accountId});
    }

    public List<Transaction> listForMonth(Long userId, YearMonth ym, Long accountId, int page, int size) {
        Filter f = monthFilter(userId, ym, accountId);
        return find(f.query(),
                Sort.by("transactionDate").descending().and("id", Sort.Direction.Descending),
                f.params())
                .page(Page.of(page, size))
                .list();
    }

    public long countForMonth(Long userId, YearMonth ym, Long accountId) {
        Filter f = monthFilter(userId, ym, accountId);
        return count(f.query(), f.params());
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
