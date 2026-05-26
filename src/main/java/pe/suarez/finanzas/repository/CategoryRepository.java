package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.domain.TransactionType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CategoryRepository implements PanacheRepository<Category> {

    public List<Category> listForUser(Long userId, boolean includeArchived) {
        if (includeArchived) {
            return list("userId = ?1 ORDER BY name", userId);
        }
        return list("userId = ?1 AND archived = false ORDER BY name", userId);
    }

    public List<Category> listByType(Long userId, TransactionType type) {
        return list("userId = ?1 AND type = ?2 AND archived = false ORDER BY name", userId, type);
    }

    public Optional<Category> findByIdForUser(Long id, Long userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }
}
