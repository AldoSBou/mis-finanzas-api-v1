package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.AllocationRule;
import pe.suarez.finanzas.domain.MonthlyBudget;
import pe.suarez.finanzas.domain.TransactionType;
import pe.suarez.finanzas.dto.BudgetDtos.*;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.AllocationRuleRepository;
import pe.suarez.finanzas.repository.MonthlyBudgetRepository;
import pe.suarez.finanzas.repository.TransactionRepository;
import pe.suarez.finanzas.security.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

@ApplicationScoped
public class DashboardService {

    @Inject TransactionRepository txRepo;
    @Inject MonthlyBudgetRepository budgetRepo;
    @Inject AllocationRuleRepository ruleRepo;
    @Inject UserContext userContext;

    public DashboardResponse build(YearMonth ym) {
        Long uid = userContext.userId();

        BigDecimal income = txRepo.sumByType(uid, ym, TransactionType.INCOME);
        BigDecimal expenses = txRepo.sumByType(uid, ym, TransactionType.EXPENSE);
        BigDecimal balance = income.subtract(expenses);
        BigDecimal savingsYtd = txRepo.sumSavingsYearToDate(uid, ym.getYear());

        MonthlyBudget budget = budgetRepo.findForPeriod(uid, ym).orElse(null);
        AllocationRule activeRule = resolveActiveRule(uid, budget);

        // Si hay budget configurado se usa expectedIncome; si no, fallback al ingreso real
        boolean budgetConfigured = budget != null;
        BigDecimal expectedIncome = budget != null ? budget.expectedIncome : income;

        Map<AllocationBucket, BigDecimal> spentByBucket = new EnumMap<>(AllocationBucket.class);
        for (Object[] row : txRepo.sumByBucketForMonth(uid, ym)) {
            AllocationBucket b = (AllocationBucket) row[0];
            BigDecimal total = (BigDecimal) row[1];
            spentByBucket.merge(b, total, BigDecimal::add);
        }

        List<BucketSummary> bucketSummaries = buildBucketSummaries(activeRule, expectedIncome, spentByBucket);

        List<CategoryTotal> topCategories = txRepo.sumByCategoryForMonth(uid, ym).stream()
                .limit(5)
                .map(row -> new CategoryTotal((Long) row[0], (String) row[1], (BigDecimal) row[2]))
                .toList();

        return new DashboardResponse(
                ym.getYear(), ym.getMonthValue(),
                income, expenses, balance, savingsYtd,
                expectedIncome, budgetConfigured,
                activeRule != null ? Mappers.toAllocationRuleResponse(activeRule) : null,
                bucketSummaries,
                topCategories
        );
    }

    private AllocationRule resolveActiveRule(Long uid, MonthlyBudget budget) {
        if (budget != null && budget.activeRuleId != null) {
            return ruleRepo.findByIdForUser(budget.activeRuleId, uid).orElse(null);
        }
        return ruleRepo.find("userId = ?1 AND template = true", uid).firstResult();
    }

    private List<BucketSummary> buildBucketSummaries(AllocationRule rule, BigDecimal expectedIncome,
                                                     Map<AllocationBucket, BigDecimal> spentByBucket) {
        List<BucketSummary> result = new ArrayList<>();
        if (rule == null) return result;

        for (var entry : rule.percentages.entrySet()) {
            AllocationBucket bucket;
            try {
                bucket = AllocationBucket.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            BigDecimal pct = BigDecimal.valueOf(entry.getValue());
            BigDecimal allocated = expectedIncome.multiply(pct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal spent = spentByBucket.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal pctUsed = allocated.signum() == 0
                    ? BigDecimal.ZERO
                    : spent.multiply(BigDecimal.valueOf(100))
                        .divide(allocated, 1, RoundingMode.HALF_UP);
            result.add(new BucketSummary(bucket, allocated, spent, pctUsed));
        }
        return result;
    }
}
