package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import pe.suarez.finanzas.domain.AllocationRule;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AllocationRuleRepository implements PanacheRepository<AllocationRule> {

    public List<AllocationRule> listForUser(Long userId) {
        return list("userId = ?1 ORDER BY template DESC, name", userId);
    }

    public Optional<AllocationRule> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }
}
