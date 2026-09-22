package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GoalDtos {

    private GoalDtos() {}

    public enum Direction { IN, OUT }

    public record GoalRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin(value = "0.01", message = "El objetivo debe ser mayor a 0")
            @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
            LocalDate targetDate,
            @NotNull Long accountId,
            @Size(max = 7) String color,
            Boolean archived
    ) {}

    /**
     * Estado de una meta, en la moneda de su cuenta.
     * {@code monthlyNeeded}: cuánto aportar al mes para llegar a la fecha (null sin fecha o cumplida).
     * {@code monthlyPace}: aporte neto promedio de los últimos 3 meses.
     * {@code projectedPeriod}: mes (YYYY-MM) en que se llegaría a ese ritmo (null si no avanza).
     */
    public record GoalResponse(
            Long id,
            String name,
            Long accountId,
            String accountName,
            String currency,
            BigDecimal targetAmount,
            LocalDate targetDate,
            String color,
            boolean archived,
            BigDecimal saved,
            BigDecimal remaining,
            BigDecimal percentage,
            boolean completed,
            Integer monthsLeft,
            BigDecimal monthlyNeeded,
            BigDecimal monthlyPace,
            String projectedPeriod,
            /** Solo con fecha y sin cumplir: el ritmo actual alcanza para llegar a tiempo */
            Boolean onTrack
    ) {}

    /** Saldo de una cuenta con metas: cuánto está asignado a metas y cuánto queda libre. */
    public record AccountAllocation(
            Long accountId,
            String accountName,
            String currency,
            BigDecimal balance,
            BigDecimal assigned,
            BigDecimal unassigned
    ) {}

    public record GoalsOverview(
            List<GoalResponse> goals,
            List<AccountAllocation> accounts
    ) {}

    /**
     * Aporte (IN) o retiro (OUT). Con {@code otherAccountId} se registra la transferencia real
     * (desde/hacia esa cuenta); sin ella solo se asigna o libera saldo de la cuenta de la meta.
     */
    public record ContributionRequest(
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
            @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull Direction direction,
            Long otherAccountId,
            @DecimalMin(value = "0.000001") BigDecimal exchangeRate,
            @NotNull @PastOrPresent LocalDate date,
            @Size(max = 200) String note
    ) {}

    public record ContributionResponse(
            Long id,
            BigDecimal amount,
            LocalDate date,
            String note,
            Long transactionId
    ) {}
}
