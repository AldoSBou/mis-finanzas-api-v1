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
                .body("data.opening", money("620"))
                .body("data.dailyVariableEstimated", money("10"))
                .body("data.accounts.find { it.name == 'Principal' }.balanceBase", money("620"))
                .body("data.accounts.find { it.name == 'Visa' }", nullValue())
                .body("data.events", hasSize(1))
                .body("data.events[0].kind", equalTo("COMMITMENT"))
                .body("data.events[0].amount", money("-200"))
                .body("data.days[0].date", equalTo(TODAY.toString()))
                .body("data.days[0].balance", money("620"))
                .body("data.days[1].balance", money("610"))
                .body("data.days[3].balance", money("390"));

        // Sin gasto variable nunca baja de cero
        as(token).get("/api/liquidity?daily=0").then().statusCode(200)
                .body("data.dailyVariable", money("0"))
                .body("data.days[3].balance", money("420"))
                .body("data.firstNegative", nullValue())
                .body("data.endBalance", money("420"));

        // Con un gasto diario propio
        as(token).get("/api/liquidity?daily=100").then().statusCode(200)
                .body("data.dailyVariable", money("100"))
                .body("data.days[3].balance", money("120"))
                .body("data.firstNegative", notNullValue())
                .body("data.lowest.balance", lessThan(0f));
    }
}
