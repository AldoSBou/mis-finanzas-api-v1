package pe.suarez.finanzas.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import pe.suarez.finanzas.domain.MonthlyBudget;

import java.time.YearMonth;
import java.util.Optional;

@ApplicationScoped
public class MonthlyBudgetRepository implements PanacheRepository<MonthlyBudget> {

    public Optional<MonthlyBudget> findForPeriod(Long userId, YearMonth ym) {
        return find("userId = ?1 AND year = ?2 AND month = ?3",
                userId, ym.getYear(), ym.getMonthValue()).firstResultOptional();
    }
}
