package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class CashflowTest {

    @Test
    void gridByCategoryAndMonth() {
        String token = register();
        long principal = accountId(token, "Principal");
        long ahorros = savingsAccount(token);
        YearMonth now = YearMonth.now();
        YearMonth prev = now.minusMonths(1);
        String lastMonth = prev.atDay(10).toString();

        createTx(token, tx("INCOME", principal, "3000").with("categoryId", categoryId(token, "Salario")));
        createTx(token, tx("EXPENSE", principal, "400").with("categoryId", categoryId(token, "Alimentación")));
        createTx(token, tx("EXPENSE", principal, "100").with("categoryId", categoryId(token, "Transporte")));
        createTx(token, tx("TRANSFER", principal, "500").with("toAccountId", ahorros));
        createTx(token, tx("EXPENSE", principal, "250").with("categoryId", categoryId(token, "Alimentación"))
                .with("transactionDate", lastMonth));
        createTx(token, tx("INCOME", principal, "2800").with("categoryId", categoryId(token, "Salario"))
                .with("transactionDate", lastMonth));

        as(token).get("/api/cashflow?months=2&until=" + now).then().statusCode(200)
                .body("data.periods", contains(prev.toString(), now.toString()))
                .body("data.income", hasSize(1))
                .body("data.income[0].name", equalTo("Salario"))
                .body("data.income[0].monthly[0]", money("2800"))
                .body("data.income[0].monthly[1]", money("3000"))
                .body("data.income[0].total", money("5800"))
                .body("data.expenses[0].name", equalTo("Alimentación"))
                .body("data.expenses[0].monthly[0]", money("250"))
                .body("data.expenses[0].monthly[1]", money("400"))
                .body("data.expenses[1].name", equalTo("Transporte"))
                .body("data.expenses[1].monthly[0]", money("0"))
                .body("data.savings[0].name", equalTo("Ahorros"))
                .body("data.savings[0].monthly[1]", money("500"))
                .body("data.totalExpenses[1]", money("500"))
                .body("data.net[0]", money("2550"))
                .body("data.net[1]", money("2000"))
                .body("data.cumulative[1]", money("4550"));

        // Un solo mes: solo lo de ese mes
        as(token).get("/api/cashflow?months=1&until=" + prev).then().statusCode(200)
                .body("data.periods", hasSize(1))
                .body("data.totalIncome[0]", money("2800"))
                .body("data.savings", hasSize(0));

        as(token).get("/api/cashflow?months=7").then().statusCode(400);
    }
}
