package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.*;
import pe.suarez.finanzas.domain.AccountType;

import java.math.BigDecimal;

public final class AccountDtos {

    private AccountDtos() {}

    public record AccountRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull AccountType type,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Usa un código de moneda de 3 letras (PEN, USD...)") String currency,
            @Digits(integer = 12, fraction = 2) BigDecimal initialBalance,
            @Size(max = 7) String color,
            @Size(max = 40) String icon,
            /** Solo tarjetas de crédito */
            @DecimalMin(value = "0.01", message = "La línea debe ser mayor a 0") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
            @Min(1) @Max(31) Integer statementDay,
            @Min(1) @Max(31) Integer dueDay
    ) {}

    public record AccountResponse(
            Long id,
            String name,
            AccountType type,
            String currency,
            BigDecimal initialBalance,
            BigDecimal balance,
            String color,
            String icon,
            boolean archived,
            BigDecimal creditLimit,
            Integer statementDay,
            Integer dueDay
    ) {}
}
