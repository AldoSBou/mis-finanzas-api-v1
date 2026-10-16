package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class LiquidityTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void projectsCashDayByDay() {
        String token = register();
        long principal = accountId(token, "Principal");
        long ahorros = savingsAccount(token);
        long visa = createAccount(token, "Visa", "CREDIT_CARD", "PEN", "0");

        // Saldo líquido: 1000 de ingreso − 280 de gastos variables (7 días × 40) = 720; el ahorro no es líquido
        createTx(token, tx("INCOME", principal, "1000").with("categoryId", categoryId(token, "Salario"))
                .with("transactionDate", TODAY.minusDays(10).toString()));
        for (int i = 1; i <= 7; i++) {
            createTx(token, tx("EXPENSE", principal, "40").with("categoryId", categoryId(token, "Alimentación"))
                    .with("transactionDate", TODAY.minusDays(i).toString()));
        }
        createTx(token, tx("TRANSFER", principal, "100").with("toAccountId", ahorros));
        // Compra con tarjeta: no toca el efectivo hasta el pago
        createTx(token, tx("EXPENSE", visa, "300").with("categoryId", categoryId(token, "Ropa")));

        // Compromiso dentro del horizonte
        Map<String, Object> c = new HashMap<>();
        c.put("name", "Alquiler");
        c.put("amount", "200");
        c.put("dueDate", TODAY.plusDays(3).toString());
        c.put("categoryId", categoryId(token, "Vivienda"));
        as(token).body(c).post("/api/commitments").then().statusCode(201);

        // 280 / 28 días = 10 al día
        as(token).get("/api/liquidity").then().statusCode(200)
                .body("data.baseCurrency", equalTo("PEN"))
                .body("data.currencies", hasSize(1))
                .body("data.currencies[0].currency", equalTo("PEN"))
                .body("data.currencies[0].opening", money("620"))
                .body("data.currencies[0].dailyVariableEstimated", money("10"))
                .body("data.currencies[0].accounts", hasSize(1))
                .body("data.currencies[0].accounts[0].name", equalTo("Principal"))
                .body("data.currencies[0].accounts[0].balance", money("620"))
                .body("data.currencies[0].events", hasSize(1))
                .body("data.currencies[0].events[0].kind", equalTo("COMMITMENT"))
                .body("data.currencies[0].events[0].amount", money("-200"))
                .body("data.currencies[0].days[0].date", equalTo(TODAY.toString()))
                .body("data.currencies[0].days[0].balance", money("620"))
                .body("data.currencies[0].days[1].balance", money("610"))
                .body("data.currencies[0].days[3].balance", money("390"));

        // Sin gasto variable nunca baja de cero
        as(token).get("/api/liquidity?daily=0").then().statusCode(200)
                .body("data.currencies[0].dailyVariable", money("0"))
                .body("data.currencies[0].days[3].balance", money("420"))
                .body("data.currencies[0].firstNegative", nullValue())
                .body("data.currencies[0].endBalance", money("420"));

        // Con un gasto diario propio
        as(token).get("/api/liquidity?daily=100").then().statusCode(200)
                .body("data.currencies[0].dailyVariable", money("100"))
                .body("data.currencies[0].days[3].balance", money("120"))
                .body("data.currencies[0].firstNegative", notNullValue())
                .body("data.currencies[0].lowest.balance", lessThan(java.math.BigDecimal.ZERO));
    }

    @Test
    void projectsEachCurrencySeparately() {
        String token = register();
        long principal = accountId(token, "Principal");
        long dolares = createAccount(token, "Dólares", "BANK", "USD", "500");

        createTx(token, tx("INCOME", principal, "1000").with("categoryId", categoryId(token, "Salario")));
        // Gasto en dólares: pesa en la proyección en dólares, no en la de soles (aunque tenga tipo de cambio)
        createTx(token, tx("EXPENSE", dolares, "56").with("categoryId", categoryId(token, "Ropa"))
                .with("exchangeRate", "3.70"));

        // Compromiso que se paga desde la cuenta en dólares
        Map<String, Object> c = new HashMap<>();
        c.put("name", "Suscripción");
        c.put("amount", "44");
        c.put("dueDate", TODAY.plusDays(2).toString());
        c.put("accountId", dolares);
        c.put("categoryId", categoryId(token, "Ropa"));
        as(token).body(c).post("/api/commitments").then().statusCode(201);

        // 56 / 28 = 2 al día en dólares; en soles no hay gasto variable
        as(token).get("/api/liquidity?daily=5").then().statusCode(200)
                .body("data.currencies", hasSize(2))
                .body("data.currencies[0].currency", equalTo("PEN"))
                .body("data.currencies[0].opening", money("1000"))
                .body("data.currencies[0].dailyVariableEstimated", money("0"))
                .body("data.currencies[0].dailyVariable", money("5"))
                .body("data.currencies[0].events", hasSize(0))
                .body("data.currencies[1].currency", equalTo("USD"))
                .body("data.currencies[1].opening", money("444"))
                .body("data.currencies[1].dailyVariableEstimated", money("2"))
                .body("data.currencies[1].dailyVariable", money("2")) // el daily del cliente solo aplica a la base
                .body("data.currencies[1].events", hasSize(1))
                .body("data.currencies[1].events[0].amount", money("-44"))
                .body("data.currencies[1].days[2].balance", money("396"));
    }
}
