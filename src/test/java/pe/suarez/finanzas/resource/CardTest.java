package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class CardTest {

    private static final LocalDate TODAY_DATE = LocalDate.now();

    @Test
    void cardShowsDebtAvailableCreditAndUtilization() {
        String token = register();
        long visa = createCard(token, "Visa Signature", "5000", 20, 15);
        createTx(token, tx("EXPENSE", visa, "1250").with("categoryId", categoryId(token, "Alimentación")));

        as(token).get("/api/cards").then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].debt", money("1250"))
                .body("data[0].creditLimit", money("5000"))
                .body("data[0].available", money("3750"))
                .body("data[0].utilization", money("25"))
                .body("data[0].statementDay", is(20))
                .body("data[0].nextDueDate", notNullValue());

        // Los datos de tarjeta viajan con la cuenta y se limpian si deja de ser tarjeta
        as(token).get("/api/accounts/" + visa).then().statusCode(200)
                .body("data.creditLimit", money("5000"))
                .body("data.dueDay", is(15));
        as(token).body(Map.of("name", "Efectivo 2", "type", "CASH", "currency", "PEN",
                        "creditLimit", "100", "statementDay", 1, "dueDay", 2))
                .post("/api/accounts").then().statusCode(201)
                .body("data.creditLimit", nullValue())
                .body("data.statementDay", nullValue());
    }

    @Test
    void statementTracksPaymentsMadeAfterClosing() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long visa = createCard(token, "Visa", "5000", 20, 15);
        createTx(token, tx("INCOME", efectivo, "3000").with("categoryId", categoryId(token, "Salario")));
        createTx(token, tx("EXPENSE", visa, "1200").with("categoryId", categoryId(token, "Alimentación"))
                .with("transactionDate", TODAY_DATE.minusDays(15).toString()));

        LocalDate closing = TODAY_DATE.minusDays(10);
        LocalDate due = TODAY_DATE.plusDays(10);
        saveStatement(token, visa, closing, due, "1200", "120").then().statusCode(200)
                .body("data.paid", money("0"))
                .body("data.remaining", money("1200"))
                .body("data.status", equalTo("PENDING"))
                .body("data.daysLeft", is(10));

        // Pago parcial desde efectivo
        createTx(token, tx("TRANSFER", efectivo, "500").with("toAccountId", visa));
        as(token).get("/api/cards").then().statusCode(200)
                .body("data[0].latestStatement.paid", money("500"))
                .body("data[0].latestStatement.remaining", money("700"))
                .body("data[0].nextDueDate", equalTo(due.toString()));

        // Actualizar el mismo ciclo (mismo cierre) no duplica
        saveStatement(token, visa, closing, due, "1000", "100").then().statusCode(200)
                .body("data.remaining", money("500"));
        createTx(token, tx("TRANSFER", efectivo, "500").with("toAccountId", visa));
        as(token).get("/api/cards/" + visa).then().statusCode(200)
                .body("data.statements", hasSize(1))
                .body("data.statements[0].status", equalTo("PAID"))
                .body("data.statements[0].remaining", money("0"));

        // El vencimiento no puede ser antes del cierre
        saveStatement(token, visa, closing, closing.minusDays(1), "10", null).then().statusCode(400)
                .body("error.code", equalTo("CARD_INVALID"));
    }

    @Test
    void unpaidStatementPastDueIsOverdueOrMinimumPaid() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long visa = createCard(token, "Visa", null, null, null);
        long amex = createCard(token, "Amex", null, null, null);
        createTx(token, tx("INCOME", efectivo, "3000").with("categoryId", categoryId(token, "Salario")));

        LocalDate closing = TODAY_DATE.minusDays(30);
        LocalDate due = TODAY_DATE.minusDays(5);
        saveStatement(token, visa, closing, due, "800", "80").then().statusCode(200)
                .body("data.status", equalTo("OVERDUE"));
        saveStatement(token, amex, closing, due, "800", "80");
        createTx(token, tx("TRANSFER", efectivo, "100").with("toAccountId", amex)
                .with("transactionDate", due.minusDays(1).toString()));
        as(token).get("/api/cards/" + amex).then().statusCode(200)
                .body("data.statements[0].status", equalTo("MINIMUM_PAID"))
                .body("data.statements[0].remaining", money("700"));
    }

    @Test
    void installmentPurchaseShowsChargedAndRemaining() {
        String token = register();
        long visa = createCard(token, "Visa", "5000", null, null);
        long compra = createExpense(token, visa, "1200", "Laptop");

        // Por defecto: monto / cuotas, desde el mes siguiente
        as(token).body(Map.of("installments", 6)).put("/api/transactions/" + compra + "/installments")
                .then().statusCode(200)
                .body("data.installmentAmount", money("200"))
                .body("data.firstPeriod", equalTo(YearMonth.now().plusMonths(1).toString()))
                .body("data.lastPeriod", equalTo(YearMonth.now().plusMonths(6).toString()))
                .body("data.charged", is(0))
                .body("data.remainingAmount", money("1200"));

        // Compra antigua: primera cuota hace dos meses; sin día de cierre, la de este mes ya se facturó
        String first = YearMonth.now().minusMonths(2).toString();
        as(token).body(Map.of("installments", 6, "installmentAmount", "210", "firstPeriod", first))
                .put("/api/transactions/" + compra + "/installments").then().statusCode(200)
                .body("data.charged", is(3))
                .body("data.remainingAmount", money("630"));

        as(token).get("/api/cards").then().statusCode(200)
                .body("data[0].activeInstallments", is(1))
                .body("data[0].installmentsRemaining", money("630"))
                .body("data[0].installmentsThisMonth", money("210"));
        as(token).get("/api/cards/" + visa).then().statusCode(200)
                .body("data.installments[0].description", equalTo("Laptop"));
        as(token).get("/api/transactions/" + compra + "/installments").then().statusCode(200)
                .body("data.installments", is(6));

        // Cuotas que no cubren la compra
        as(token).body(Map.of("installments", 6, "installmentAmount", "100"))
                .put("/api/transactions/" + compra + "/installments").then().statusCode(400)
                .body("error.code", equalTo("CARD_INVALID"));

        as(token).delete("/api/transactions/" + compra + "/installments").then().statusCode(204);
        as(token).get("/api/transactions/" + compra + "/installments").then().statusCode(204);
    }

    @Test
    void onlyCardExpensesCanHaveInstallments() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long visa = createCard(token, "Visa", null, null, null);
        long enEfectivo = createExpense(token, efectivo, "300", "Zapatillas");
        as(token).body(Map.of("installments", 3)).put("/api/transactions/" + enEfectivo + "/installments")
                .then().statusCode(400).body("error.code", equalTo("CARD_INVALID"));

        // Si la compra se mueve a otra cuenta que no es tarjeta, el plan se elimina
        long compra = createExpense(token, visa, "300", "Zapatillas");
        as(token).body(Map.of("installments", 3)).put("/api/transactions/" + compra + "/installments")
                .then().statusCode(200);
        as(token).body(tx("EXPENSE", efectivo, "300").with("categoryId", categoryId(token, "Ropa"))
                        .with("description", "Zapatillas"))
                .put("/api/transactions/" + compra).then().statusCode(200);
        as(token).get("/api/transactions/" + compra + "/installments").then().statusCode(204);

        // Otro usuario no ve ni toca las tarjetas ajenas
        String other = register();
        as(other).get("/api/cards/" + visa).then().statusCode(404);
        as(other).body(Map.of("installments", 3)).put("/api/transactions/" + compra + "/installments")
                .then().statusCode(404);
    }

    // ---------------------------------------------------------------

    private static long createCard(String token, String name, String limit, Integer statementDay, Integer dueDay) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "CREDIT_CARD");
        body.put("currency", "PEN");
        body.put("creditLimit", limit);
        body.put("statementDay", statementDay);
        body.put("dueDay", dueDay);
        Number id = as(token).body(body).post("/api/accounts").then().statusCode(201)
                .extract().path("data.id");
        return id.longValue();
    }

    private static long createExpense(String token, long accountId, String amount, String description) {
        Number id = as(token).body(tx("EXPENSE", accountId, amount)
                        .with("categoryId", categoryId(token, "Ropa"))
                        .with("description", description))
                .post("/api/transactions").then().statusCode(201)
                .extract().path("data.id");
        return id.longValue();
    }

    private static io.restassured.response.Response saveStatement(String token, long card, LocalDate closing,
                                                                  LocalDate due, String total, String minimum) {
        Map<String, Object> body = new HashMap<>();
        body.put("closingDate", closing.toString());
        body.put("dueDate", due.toString());
        body.put("totalDue", total);
        body.put("minimumDue", minimum);
        return as(token).body(body).post("/api/cards/" + card + "/statements");
    }
}
