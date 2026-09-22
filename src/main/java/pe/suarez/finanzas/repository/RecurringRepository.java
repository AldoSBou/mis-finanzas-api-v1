package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.suarez.finanzas.domain.RecurringTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RecurringRepository implements PanacheRepository<RecurringTransaction> {

    public List<RecurringTransaction> listForUser(Long userId) {
        return list("userId = ?1 ORDER BY active DESC, nextDate", userId);
    }

    public List<RecurringTransaction> activeForUser(Long userId) {
        return list("userId = ?1 AND active = true ORDER BY nextDate", userId);
    }

    public Optional<RecurringTransaction> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }

    /**
     * Automáticos vencidos, bloqueados (SELECT ... FOR UPDATE): si dos peticiones
     * materializan a la vez, la segunda espera y ya no los ve vencidos, así no duplica.
     */
    public List<RecurringTransaction> dueAutoForUpdate(Long userId, LocalDate today) {
        return find("userId = ?1 AND active = true AND autoCreate = true AND nextDate <= ?2", userId, today)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }

    public Optional<RecurringTransaction> findByIdForUpdate(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
