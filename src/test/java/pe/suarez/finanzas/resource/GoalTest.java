package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class GoalTest {

    @Test
    void contributionsFromAnotherAccountTransferMoneyAndCountAsSavings() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");
        createTx(token, tx("INCOME", efectivo, "5000").with("categoryId", categoryId(token, "Salario")));

        long viaje = createGoal(token, "Viaje a Cusco", "3000", ahorros, null);
        contribute(token, viaje, "IN", "1000", efectivo).then().statusCode(201)
                .body("data.transactionId", notNullValue());

        // El dinero se movió de verdad y el panel lo cuenta como ahorro del mes
        assertBalance(token, efectivo, "4000");
        assertBalance(token, ahorros, "1000");
        as(token).get("/api/dashboard").then().statusCode(200).body("data.savings", money("1000"));

        as(token).get("/api/goals").then().statusCode(200)
                .body("data.goals[0].saved", money("1000"))
                .body("data.goals[0].remaining", money("2000"))
                .body("data.goals[0].percentage", money("33.3"))
                .body("data.goals[0].monthlyPace", money("333.33"))
                .body("data.accounts[0].unassigned", money("0"));
    }

    @Test
    void severalGoalsShareAnAccountAndOnlyUnassignedMoneyCanBeAllocated() {
        String token = register();
        long ahorros = createAccount(token, "Ahorros BCP", "SAVINGS", "PEN", "2500");
        long fondo = createGoal(token, "Fondo de emergencia", "10000", ahorros, null);
        long laptop = createGoal(token, "Laptop", "4000", ahorros, null);

        // Solo asignar (el dinero ya está en la cuenta): no mueve saldo
        contribute(token, fondo, "IN", "2000", null).then().statusCode(201)
                .body("data.transactionId", nullValue());
        // Quedan 500 sin asignar: no se pueden asignar 800
        contribute(token, laptop, "IN", "800", null).then().statusCode(400)
                .body("error.code", equalTo("GOAL_INVALID"));
        contribute(token, laptop, "IN", "500", null).then().statusCode(201);

        assertBalance(token, ahorros, "2500");
        as(token).get("/api/goals").then().statusCode(200)
                .body("data.accounts[0].assigned", money("2500"))
                .body("data.accounts[0].unassigned", money("0"));

        // No se puede retirar más de lo ahorrado en la meta
        contribute(token, laptop, "OUT", "600", null).then().statusCode(400);
        contribute(token, laptop, "OUT", "200", null).then().statusCode(201);
        as(token).get("/api/goals").then().statusCode(200)
                .body("data.goals.find { it.name == 'Laptop' }.saved", money("300"))
                .body("data.accounts[0].unassigned", money("200"));
    }

    @Test
    void monthlyNeededToReachTheTargetDate() {
        String token = register();
        long ahorros = accountId(token, "Ahorros");
        LocalDate target = YearMonth.now().plusMonths(3).atEndOfMonth();
        long months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(target)) + 1; // 4

        long id = createGoal(token, "Auto", "1200", ahorros, target.toString());
        as(token).get("/api/goals").then().statusCode(200)
                .body("data.goals.find { it.id == " + id + " }.monthsLeft", is((int) months))
                .body("data.goals.find { it.id == " + id + " }.monthlyNeeded", money("300"))
                .body("data.goals.find { it.id == " + id + " }.onTrack", is(false));
    }

    @Test
    void deletingTheTransferRemovesTheContributionAndEditingKeepsItInSync() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");
        long goal = createGoal(token, "Viaje", "1000", ahorros, null);

        Number txId = contribute(token, goal, "IN", "300", efectivo).then().statusCode(201)
                .extract().path("data.transactionId");

        // Editar el monto de la transferencia actualiza el aporte
        as(token).body(tx("TRANSFER", efectivo, "350").with("toAccountId", ahorros))
                .put("/api/transactions/" + txId.longValue()).then().statusCode(200);
        as(token).get("/api/goals").then().statusCode(200).body("data.goals[0].saved", money("350"));

        // Borrar la transferencia borra el aporte
        as(token).delete("/api/transactions/" + txId.longValue()).then().statusCode(204);
        as(token).get("/api/goals").then().statusCode(200).body("data.goals[0].saved", money("0"));
    }

    @Test
    void goalsBelongToTheirOwner() {
        String token = register();
        long goal = createGoal(token, "Privada", "100", accountId(token, "Ahorros"), null);
        String other = register();
        as(other).get("/api/goals").then().statusCode(200).body("data.goals", empty());
        as(other).get("/api/goals/" + goal + "/contributions").then().statusCode(404)
                .body("error.code", equalTo("GOAL_NOT_FOUND"));
    }

    // ------------------------------------------------------------------

    private static long createGoal(String token, String name, String target, long accountId, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("targetAmount", target);
        body.put("accountId", accountId);
        if (date != null) body.put("targetDate", date);
        Number id = as(token).body(body).post("/api/goals").then().statusCode(201).extract().path("data.id");
        return id.longValue();
    }

    private static io.restassured.response.Response contribute(String token, long goal, String direction,
                                                               String amount, Long otherAccountId) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("direction", direction);
        body.put("date", TODAY);
        if (otherAccountId != null) body.put("otherAccountId", otherAccountId);
        return as(token).body(body).post("/api/goals/" + goal + "/contributions");
    }
}
