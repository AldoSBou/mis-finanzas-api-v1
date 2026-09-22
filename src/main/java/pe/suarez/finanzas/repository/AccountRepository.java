package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.suarez.finanzas.domain.Account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AccountRepository implements PanacheRepository<Account> {

    @Inject
    EntityManager em;

    public List<Account> listForUser(Long userId, boolean includeArchived) {
        if (includeArchived) {
            return list("userId = ?1 ORDER BY archived, name", userId);
        }
        return list("userId = ?1 AND archived = false ORDER BY name", userId);
    }

    public Optional<Account> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }

    public Map<Long, Account> mapByIds(List<Long> ids, Long userId) {
        Map<Long, Account> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        list("id IN ?1 AND userId = ?2", ids, userId).forEach(a -> result.put(a.id, a));
        return result;
    }

    /**
     * Suma neta de movimientos por cuenta, en la moneda de cada cuenta (sin el saldo inicial).
     * Ingresos suman; gastos y transferencias salientes restan; transferencias entrantes
     * suman {@code to_amount}.
     */
    public Map<Long, BigDecimal> movementTotals(Long userId) {
        return movementTotals(userId, LocalDate.of(9999, 12, 31));
    }

    /** Igual que {@link #movementTotals(Long)}, contando solo movimientos hasta {@code upTo} inclusive. */
    public Map<Long, BigDecimal> movementTotals(Long userId, LocalDate upTo) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT m.account_id, SUM(m.delta)
                        FROM (
                            SELECT account_id,
                                   CASE WHEN type = 'INCOME' THEN amount ELSE -amount END AS delta
                            FROM transactions
                            WHERE user_id = :uid AND transaction_date <= :upTo
                            UNION ALL
                            SELECT to_account_id, to_amount
                            FROM transactions
                            WHERE user_id = :uid AND type = 'TRANSFER' AND transaction_date <= :upTo
                        ) m
                        GROUP BY m.account_id
                        """)
                .setParameter("uid", userId)
                .setParameter("upTo", upTo)
                .getResultList();
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return result;
    }

    public boolean hasTransactions(Long accountId) {
        Long count = em.createQuery("""
                        SELECT COUNT(t) FROM Transaction t
                        WHERE t.accountId = :id OR t.toAccountId = :id
                        """, Long.class)
                .setParameter("id", accountId)
                .getSingleResult();
        return count > 0;
    }
}
