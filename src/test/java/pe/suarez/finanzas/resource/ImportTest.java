package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class ImportTest {

    private static final String D1 = LocalDate.now().minusDays(3).toString();
    private static final String D2 = LocalDate.now().minusDays(2).toString();

    @Test
    void previewSuggestsFromRulesAndHistoryAndFlagsDuplicates() {
        String token = register();
        long bcp = createAccount(token, "BCP Sueldo", "BANK", "PEN", "0");
        long transporte = categoryId(token, "Transporte");
        long restaurantes = categoryId(token, "Restaurantes");

        // Regla explícita e historial ("uber trip" ya categorizado a mano)
        as(token).body(Map.of("pattern", "Rappi", "categoryId", restaurantes))
                .post("/api/categorization-rules").then().statusCode(201);
        createTx(token, tx("EXPENSE", bcp, "18.50").with("categoryId", transporte)
                .with("description", "UBER *TRIP 4821").with("transactionDate", D1));

        var rows = List.of(
                row(D1, "UBER *TRIP 4821", "-18.50"),   // ya registrado a mano → duplicado
                row(D2, "Uber Trip 9913 LIMA", "-22.00"), // historial (sin números)
                row(D2, "RAPPI*PERU PEDIDO", "-45.90"),   // regla
                row(D2, "ABONO SUELDO", "3500.00"),       // ingreso sin sugerencia
                row(D2, "COMPRA DESCONOCIDA", "-10"));
        as(token).body(Map.of("accountId", bcp, "rows", rows)).post("/api/imports/preview")
                .then().statusCode(200)
                .body("data.rows[0].duplicate", is(true))
                .body("data.rows[1].duplicate", is(false))
                .body("data.rows[1].suggestedCategoryId", is((int) transporte))
                .body("data.rows[1].source", equalTo("HISTORY"))
                .body("data.rows[2].suggestedCategoryId", is((int) restaurantes))
                .body("data.rows[2].source", equalTo("RULE"))
                .body("data.rows[3].type", equalTo("INCOME"))
                .body("data.rows[3].source", equalTo("NONE"))
                .body("data.rows[4].type", equalTo("EXPENSE"));
    }

    @Test
    void commitCreatesTransactionsAndTransfersAndCanBeUndone() {
        String token = register();
        long bcp = createAccount(token, "BCP", "BANK", "PEN", "1000");
        long visa = createAccount(token, "Visa", "CREDIT_CARD", "PEN", "-300");
        long comida = categoryId(token, "Alimentación");
        long sueldo = categoryId(token, "Salario");

        var rows = List.of(
                Map.of("date", D1, "description", "PLAZA VEA", "amount", "-120.40", "categoryId", comida),
                Map.of("date", D1, "description", "ABONO SUELDO", "amount", "3000", "categoryId", sueldo),
                Map.of("date", D2, "description", "PAGO TARJETA VISA", "amount", "-300", "transferAccountId", visa));
        Number batchId = as(token).body(Map.of("accountId", bcp, "fileName", "bcp.csv", "rows", rows))
                .post("/api/imports").then().statusCode(201)
                .body("data.rowCount", is(3))
                .extract().path("data.id");

        assertBalance(token, bcp, "3579.60");   // 1000 - 120.40 + 3000 - 300
        assertBalance(token, visa, "0");        // deuda pagada: la transferencia no es gasto

        as(token).get("/api/imports").then().statusCode(200)
                .body("data[0].remaining", is(3))
                .body("data[0].accountName", equalTo("BCP"));

        as(token).delete("/api/imports/" + batchId.longValue()).then().statusCode(204);
        assertBalance(token, bcp, "1000");
        assertBalance(token, visa, "-300");
    }

    @Test
    void invalidRowRollsBackWholeImport() {
        String token = register();
        long bcp = createAccount(token, "BCP", "BANK", "PEN", "0");
        var rows = List.of(
                Map.of("date", D1, "description", "OK", "amount", "-10", "categoryId", categoryId(token, "Otros")),
                Map.of("date", D1, "description", "SIN CATEGORIA", "amount", "-20"));
        as(token).body(Map.of("accountId", bcp, "rows", rows)).post("/api/imports")
                .then().statusCode(400)
                .body("error.code", equalTo("TRANSACTION_CATEGORY_REQUIRED"))
                .body("error.message", startsWith("Fila 2:"));
        assertBalance(token, bcp, "0");
        as(token).get("/api/imports").then().statusCode(200).body("data", empty());
    }

    @Test
    void rulesCrudAndIsolation() {
        String token = register();
        long transporte = categoryId(token, "Transporte");
        Number id = as(token).body(Map.of("pattern", "Cabify", "categoryId", transporte))
                .post("/api/categorization-rules").then().statusCode(201)
                .extract().path("data.id");
        as(token).body(Map.of("pattern", "12345", "categoryId", transporte))
                .post("/api/categorization-rules").then().statusCode(400);

        String other = register();
        as(other).get("/api/categorization-rules").then().statusCode(200).body("data", empty());
        as(other).delete("/api/categorization-rules/" + id.longValue()).then().statusCode(404);
        as(other).body(Map.of("accountId", accountId(token, "Efectivo"), "rows", List.of(row(D1, "x", "-1"))))
                .post("/api/imports/preview").then().statusCode(404);

        as(token).delete("/api/categorization-rules/" + id.longValue()).then().statusCode(204);
        as(token).get("/api/categorization-rules").then().statusCode(200).body("data", empty());
    }

    private static Map<String, Object> row(String date, String description, String amount) {
        return Map.of("date", date, "description", description, "amount", amount);
    }
}
