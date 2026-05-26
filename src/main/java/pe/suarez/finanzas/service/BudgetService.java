package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.AllocationRule;
import pe.suarez.finanzas.domain.MonthlyBudget;
import pe.suarez.finanzas.dto.BudgetDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.AllocationRuleRepository;
import pe.suarez.finanzas.repository.MonthlyBudgetRepository;
import pe.suarez.finanzas.security.UserContext;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;

@ApplicationScoped
public class BudgetService {

    @Inject MonthlyBudgetRepository budgetRepo;
    @Inject AllocationRuleRepository ruleRepo;
    @Inject UserContext userContext;

    // -------- Reglas de asignación --------

    public List<AllocationRuleResponse> listRules() {
        return ruleRepo.listForUser(userContext.userId()).stream()
                .map(Mappers::toAllocationRuleResponse)
                .toList();
    }

    public AllocationRuleResponse getRule(Long id) {
        AllocationRule r = ruleRepo.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
        return Mappers.toAllocationRuleResponse(r);
    }

    @Transactional
    public AllocationRuleResponse createRule(AllocationRuleRequest req) {
        validatePercentages(req.percentages());

        AllocationRule r = new AllocationRule();
        r.userId = userContext.userId();
        r.name = req.name().trim();
        r.description = req.description();
        r.percentages = new HashMap<>(req.percentages());
        r.template = false;
        r.persist();
        return Mappers.toAllocationRuleResponse(r);
    }

    @Transactional
    public AllocationRuleResponse updateRule(Long id, AllocationRuleRequest req) {
        AllocationRule r = ruleRepo.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
        if (r.template) {
            throw new ApiException(ErrorCode.RULE_TEMPLATE_LOCKED);
        }
        validatePercentages(req.percentages());
        r.name = req.name().trim();
        r.description = req.description();
        r.percentages = new HashMap<>(req.percentages());
        return Mappers.toAllocationRuleResponse(r);
    }

    @Transactional
    public void deleteRule(Long id) {
        AllocationRule r = ruleRepo.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
        if (r.template) {
            throw new ApiException(ErrorCode.RULE_TEMPLATE_LOCKED);
        }
        r.delete();
    }

    private void validatePercentages(java.util.Map<String, Integer> percentages) {
        if (percentages == null || percentages.isEmpty()) {
            throw new ApiException(ErrorCode.RULE_PERCENTAGES_INVALID,
                    "Debes especificar al menos un cubo con porcentaje");
        }
        int sum = 0;
        for (var e : percentages.entrySet()) {
            try {
                AllocationBucket.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.RULE_PERCENTAGES_INVALID,
                        "Cubo inválido: " + e.getKey());
            }
            if (e.getValue() == null || e.getValue() < 0 || e.getValue() > 100) {
                throw new ApiException(ErrorCode.RULE_PERCENTAGES_INVALID,
                        "Porcentaje inválido para " + e.getKey() + ": debe estar entre 0 y 100");
            }
            sum += e.getValue();
        }
        if (sum != 100) {
            throw new ApiException(ErrorCode.RULE_PERCENTAGES_INVALID,
                    "Los porcentajes deben sumar 100. Suma actual: " + sum);
        }
    }

    // -------- Presupuesto mensual --------

    public MonthlyBudgetResponse getForPeriod(YearMonth ym) {
        MonthlyBudget b = budgetRepo.findForPeriod(userContext.userId(), ym).orElse(null);
        if (b == null) return null;
        return toResponse(b);
    }

    @Transactional
    public MonthlyBudgetResponse upsertBudget(MonthlyBudgetRequest req) {
        Long uid = userContext.userId();
        YearMonth ym = YearMonth.of(req.year(), req.month());

        if (req.activeRuleId() != null) {
            ruleRepo.findByIdForUser(req.activeRuleId(), uid)
                    .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
        }

        MonthlyBudget b = budgetRepo.findForPeriod(uid, ym).orElseGet(() -> {
            MonthlyBudget n = new MonthlyBudget();
            n.userId = uid;
            n.year = req.year();
            n.month = req.month();
            return n;
        });
        b.expectedIncome = req.expectedIncome();
        b.activeRuleId = req.activeRuleId();

        if (b.id == null) b.persist();
        return toResponse(b);
    }

    private MonthlyBudgetResponse toResponse(MonthlyBudget b) {
        String ruleName = null;
        if (b.activeRuleId != null) {
            AllocationRule r = ruleRepo.findByIdForUser(b.activeRuleId, b.userId).orElse(null);
            if (r != null) ruleName = r.name;
        }
        return new MonthlyBudgetResponse(
                b.id, b.year, b.month, b.expectedIncome, b.activeRuleId, ruleName);
    }
}
