package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class ReceiptsTest {

    @Test
    void newUsersHaveHouseholdCategories() {
        as(register()).get("/api/categories").then().statusCode(200)
                .body("data.name", hasItems("Limpieza", "Cuidado personal", "Mascotas", "Alimentación"));
    }

    @Test
    void confirmingCreatesOneTransactionPerCategoryAndUndoRemovesThem() {
        String token = register();
        long account = accountId(token, "Principal");
        long food = categoryId(token, "Alimentación");
        long cleaning = categoryId(token, "Limpieza");

        Number id = as(token).body(Map.of(
                        "accountId", account,
                        "merchant", "Tottus",
                        "date", TODAY,
                        "total", "30.00",
                        "groups", List.of(
                                Map.of("categoryId", food, "amount", "18.50", "items", List.of(
                                        Map.of("description", "LCH GLORIA EVAP", "amount", "4.50"),
                                        Map.of("description", "PAN BIMBO", "amount", "14.00"))),
                                Map.of("categoryId", cleaning, "amount", "11.50", "items", List.of(
                                        Map.of("description", "DET ARIEL 2KG", "amount", "11.50"))))))
                .post("/api/receipts").then().statusCode(201)
                .body("data.merchant", equalTo("Tottus"))
                .body("data.total", money("30.00"))
                .body("data.currency", equalTo("PEN"))
                .body("data.items.size()", equalTo(3))
                .body("data.transactions.size()", equalTo(2))
                .body("data.transactions[0].amount", money("18.50"))
                .body("data.transactions[0].categoryName", equalTo("Alimentación"))
                .body("data.transactions[0].description", startsWith("Tottus · "))
                .body("data.transactions[1].categoryName", equalTo("Limpieza"))
                .extract().path("data.id");

        // Los movimientos existen, enlazados a la boleta, y descuentan de la cuenta
        as(token).get("/api/transactions").then().statusCode(200)
                .body("data.size()", equalTo(2))
                .body("data.receiptId", everyItem(equalTo(id)));
        assertBalance(token, account, "-30.00");

        as(token).get("/api/receipts/" + id).then().statusCode(200)
                .body("data.items[2].description", equalTo("DET ARIEL 2KG"));

        // Otro usuario no la ve
        as(register()).get("/api/receipts/" + id).then().statusCode(404);

        // Deshacer borra boleta y movimientos
        as(token).delete("/api/receipts/" + id).then().statusCode(204);
        as(token).get("/api/receipts/" + id).then().statusCode(404);
        as(token).get("/api/transactions").then().statusCode(200).body("data.size()", equalTo(0));
        assertBalance(token, account, "0.00");
    }

    @Test
    void rejectsGroupsThatDoNotSumTheTotal() {
        String token = register();
        as(token).body(Map.of(
                        "accountId", accountId(token, "Principal"),
                        "date", TODAY,
                        "total", "30.00",
                        "groups", List.of(Map.of("categoryId", categoryId(token, "Alimentación"), "amount", "18.50"))))
                .post("/api/receipts").then().statusCode(400)
                .body("error.code", equalTo("RECEIPT_INVALID"));
        as(token).get("/api/transactions").then().statusCode(200).body("data.size()", equalTo(0));
    }

    @Test
    void rejectsIncomeCategories() {
        String token = register();
        as(token).body(Map.of(
                        "accountId", accountId(token, "Principal"),
                        "date", TODAY,
                        "total", "10.00",
                        "groups", List.of(Map.of("categoryId", categoryId(token, "Salario"), "amount", "10.00"))))
                .post("/api/receipts").then().statusCode(400)
                .body("error.code", equalTo("TRANSACTION_TYPE_MISMATCH"));
    }

    @Test
    void parseWithoutInterpreterIsUnavailable() {
        as(register()).body(Map.of("images", List.of("aGVsbG8="), "mediaType", "image/jpeg"))
                .post("/api/receipts/parse").then().statusCode(503)
                .body("error.code", equalTo("VOICE_UNAVAILABLE"));
    }
}
