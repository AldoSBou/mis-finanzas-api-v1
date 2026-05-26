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

@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    @Inject
    EntityManager em;

    public Optional<Transaction> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }

    public List<Transaction> listForMonth(Long userId, YearMonth ym, int page, int size) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return find("userId = ?1 AND transactionDate BETWEEN ?2 AND ?3",
                Sort.by("transactionDate").descending().and("id", Sort.Direction.Descending),
                userId, from, to)
                .page(Page.of(page, size))
                .list();
    }

    public long countForMonth(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return count("userId = ?1 AND transactionDate BETWEEN ?2 AND ?3", userId, from, to);
    }

    /**
     * Suma total para un mes y tipo (INCOME o EXPENSE).
     * Devuelve ZERO si no hay registros.
     */
    public BigDecimal sumByType(Long userId, YearMonth ym, TransactionType type) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        BigDecimal result = em.createQuery("""
                        SELECT COALESCE(SUM(t.amount), 0)
                        FROM Transaction t
                        WHERE t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        """, BigDecimal.class)
                .setParameter("uid", userId)
                .setParameter("type", type)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * Agrupa gastos del mes por categoría.
     * Devuelve filas: [categoryId, categoryName, totalAmount].
     */
    public List<Object[]> sumByCategoryForMonth(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return em.createQuery("""
                        SELECT c.id, c.name, COALESCE(SUM(t.amount), 0)
                        FROM Transaction t, Category c
                        WHERE t.categoryId = c.id
                          AND t.userId = :uid
                          AND t.type = :type
                          AND t.transactionDate BETWEEN :from AND :to
                        GROUP BY c.id, c.name
                        ORDER BY 3 DESC
                        """, Object[].class)
                .setParameter("uid", userId)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /**
     * Agrupa gastos del mes por bucket de asignación.
     * Devuelve filas: [bucket, totalAmount].
     */
    public List<Object[]> sumByBucketForMonth(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return em.createQuery("""
                        SELECT c.defaultBucket, COALESCE(SUM(t.amount), 0)
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
     * Total de ahorro acumulado del año (suma de transacciones cuyo bucket es SAVINGS o INVESTMENT).
     */
    public BigDecimal sumSavingsYearToDate(Long userId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        BigDecimal result = em.createQuery("""
                        SELECT COALESCE(SUM(t.amount), 0)
                        FROM Transaction t, Category c
                        WHERE t.categoryId = c.id
                          AND t.userId = :uid
                          AND c.defaultBucket IN (:buckets)
                          AND t.transactionDate BETWEEN :from AND :to
                        """, BigDecimal.class)
                .setParameter("uid", userId)
                .setParameter("buckets", List.of(AllocationBucket.SAVINGS, AllocationBucket.INVESTMENT))
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }
}
