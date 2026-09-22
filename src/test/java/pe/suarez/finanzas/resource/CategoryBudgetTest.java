package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class CategoryBudgetTest {

    private static final String PERIOD = YearMonth.now().toString();

    @Test
    void statusFollowsSpentVersusLimit() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long restaurantes = categoryId(token, "Restaurantes");
        long ropa = categoryId(token, "Ropa");
        long entretenimiento = categoryId(token, "Entretenimiento");

        setLimit(token, restaurantes, "400");
        setLimit(token, ropa, "200");
        setLimit(token, entretenimiento, "100");

        createTx(token, tx("EXPENSE", efectivo, "340").with("categoryId", restaurantes)); // 85%
        createTx(token, tx("EXPENSE", efectivo, "250").with("categoryId", ropa));         // 125%
        createTx(token, tx("EXPENSE", efectivo, "30").with("categoryId", entretenimiento)); // 30%

        String base = "data.items.find { it.categoryId == %d }.";
        as(token).get("/api/category-budgets?period=" + PERIOD).then().statusCode(200)
                .body(base.formatted(restaurantes) + "status", equalTo("WARNING"))
                .body(base.formatted(restaurantes) + "percentage", money("85"))
                .body(base.formatted(ropa) + "status", equalTo("OVER"))
                .body(base.formatted(entretenimiento) + "status", equalTo("OK"))
                .body("data.items.find { it.categoryName == 'Vivienda' }.status", equalTo("NONE"))
                .body("data.totalLimit", money("700"))
                .body("data.totalSpent", money("620"));

        // El panel solo alerta las que pasan el 80%
        as(token).get("/api/dashboard").then().statusCode(200)
                .body("data.budgetAlerts.categoryId", containsInAnyOrder(
                        (int) restaurantes, (int) ropa));
    }

    @Test
    void scheduledRecurringExpenseWarnsBeforeSpending() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long vivienda = categoryId(token, "Vivienda");
        setLimit(token, vivienda, "1000");

        // Alquiler programado para mañana: aún no gastado, pero ya supera el límite
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Tx rec = new Tx();
        rec.put("type", "EXPENSE");
        rec.put("accountId", efectivo);
        rec.put("categoryId", vivienda);
        rec.put("amount", "1200");
        rec.put("frequency", "MONTHLY");
        rec.put("startDate", tomorrow.toString());
        rec.put("autoCreate", true);
        as(token).body(rec).post("/api/recurring").then().statusCode(201);

        boolean sameMonth = YearMonth.from(tomorrow).equals(YearMonth.now());
        as(token).get("/api/category-budgets").then().statusCode(200)
                .body("data.items.find { it.categoryId == " + vivienda + " }.status", equalTo("OK"))
                .body("data.items.find { it.categoryId == " + vivienda + " }.scheduled",
                        money(sameMonth ? "1200" : "0"))
                .body("data.items.find { it.categoryId == " + vivienda + " }.willExceed", is(sameMonth));
    }

    @Test
    void limitCanBeUpdatedAndRemoved() {
        String token = register();
        long ropa = categoryId(token, "Ropa");
        setLimit(token, ropa, "200");
        setLimit(token, ropa, "350");
        as(token).get("/api/category-budgets").then().statusCode(200)
                .body("data.items.find { it.categoryId == " + ropa + " }.limit", money("350"));

        as(token).delete("/api/category-budgets/" + ropa).then().statusCode(204);
        as(token).get("/api/category-budgets").then().statusCode(200)
                .body("data.items.find { it.categoryId == " + ropa + " }.status", equalTo("NONE"));
    }

    @Test
    void incomeCategoriesAndOtherUsersAreRejected() {
        String token = register();
        as(token).body(Map.of("amount", "100"))
                .put("/api/category-budgets/" + categoryId(token, "Salario")).then().statusCode(400);

        String other = register();
        as(other).body(Map.of("amount", "100"))
                .put("/api/category-budgets/" + categoryId(token, "Ropa")).then().statusCode(404)
                .body("error.code", equalTo("CATEGORY_NOT_FOUND"));
    }

    private static void setLimit(String token, long categoryId, String amount) {
        as(token).body(Map.of("amount", amount))
                .put("/api/category-budgets/" + categoryId).then().statusCode(204);
    }
}
