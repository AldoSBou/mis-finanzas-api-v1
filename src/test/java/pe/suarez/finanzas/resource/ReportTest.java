package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class ReportTest {

    @Test
    void monthlyTotalsAndNetWorthOverTime() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");
        LocalDate lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);

        // Mes pasado: sueldo 3000, comida 800, ahorro 500
        createTx(token, tx("INCOME", efectivo, "3000").with("categoryId", categoryId(token, "Salario"))
                .with("transactionDate", lastMonth.toString()));
        createTx(token, tx("EXPENSE", efectivo, "800").with("categoryId", categoryId(token, "Alimentación"))
                .with("transactionDate", lastMonth.toString()));
        createTx(token, tx("TRANSFER", efectivo, "500").with("toAccountId", ahorros)
                .with("transactionDate", lastMonth.toString()));
        // Este mes: comida 200
        createTx(token, tx("EXPENSE", efectivo, "200").with("categoryId", categoryId(token, "Alimentación")));

        String prev = YearMonth.now().minusMonths(1).toString();
        String curr = YearMonth.now().toString();
        as(token).get("/api/reports?months=3").then().statusCode(200)
                .body("data.months", hasSize(3))
                .body("data.months[2].period", equalTo(curr))
                .body("data.months.find { it.period == '" + prev + "' }.income", money("3000"))
                .body("data.months.find { it.period == '" + prev + "' }.expenses", money("800"))
                .body("data.months.find { it.period == '" + prev + "' }.savings", money("500"))
                .body("data.months.find { it.period == '" + prev + "' }.net", money("1700"))
                // Patrimonio = efectivo + ahorros: 2200 al cierre del mes pasado, 2000 hoy
                .body("data.months.find { it.period == '" + prev + "' }.netWorth", money("2200"))
                .body("data.months[2].netWorth", money("2000"))
                .body("data.categories[0].name", equalTo("Alimentación"))
                .body("data.categories[0].total", money("1000"))
                .body("data.categories[0].monthly", hasSize(3))
                .body("data.netWorthApproximate", is(false));
    }

    @Test
    void rangeIsValidated() {
        String token = register();
        as(token).get("/api/reports?months=0").then().statusCode(400);
        as(token).get("/api/reports?months=37").then().statusCode(400);
    }
}
