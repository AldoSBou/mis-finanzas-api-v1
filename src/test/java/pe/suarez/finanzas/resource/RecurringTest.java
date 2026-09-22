package pe.suarez.finanzas.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.suarez.finanzas.domain.Frequency;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class RecurringTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Inject EntityManager em;

    @Test
    void autoRecurringCatchesUpMissedOccurrences() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        LocalDate start = TODAY.minusMonths(2);

        long id = createRecurring(token, recurring("EXPENSE", efectivo, "100", start, true)
                .with("categoryId", categoryId(token, "Vivienda"))
                .with("description", "Alquiler"));

        // Dos meses atrás, el mes pasado y hoy (el día de referencia es el de hoy)
        assertEquals(3L, countByRecurring(id));
        assertBalance(token, efectivo, "-300");
        as(token).get("/api/recurring").then().statusCode(200)
                .body("data.find { it.id == " + id + " }.nextDate",
                        equalTo(Frequency.MONTHLY.next(TODAY, start.getDayOfMonth()).toString()));

        // Volver a cargar no duplica
        as(token).get("/api/dashboard").then().statusCode(200);
        assertEquals(3L, countByRecurring(id));
    }

    @Test
    void manualRecurringIsPendingUntilRegistered() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long luz = createRecurring(token, recurring("EXPENSE", efectivo, "120", TODAY, false)
                .with("categoryId", categoryId(token, "Servicios"))
                .with("description", "Luz"));

        assertEquals(0L, countByRecurring(luz));
        as(token).get("/api/dashboard").then().statusCode(200)
                .body("data.upcoming.find { it.recurringId == " + luz + " }.overdue", is(true))
                .body("data.projectedBalance", money("-120"));

        // Se registra con el monto real del recibo
        as(token).body(Map.of("amount", "134.50")).post("/api/recurring/" + luz + "/register")
                .then().statusCode(204);
        assertEquals(1L, countByRecurring(luz));
        assertBalance(token, efectivo, "-134.50");
        as(token).get("/api/dashboard").then().statusCode(200)
                .body("data.upcoming.findAll { it.recurringId == " + luz + " && it.overdue }", empty());
    }

    @Test
    void skipAdvancesWithoutCreatingTransaction() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long id = createRecurring(token, recurring("INCOME", efectivo, "500", TODAY, false)
                .with("categoryId", categoryId(token, "Freelance")));

        as(token).post("/api/recurring/" + id + "/skip").then().statusCode(204);
        assertEquals(0L, countByRecurring(id));
        as(token).get("/api/recurring").then().statusCode(200)
                .body("data[0].nextDate", equalTo(Frequency.MONTHLY.next(TODAY, TODAY.getDayOfMonth()).toString()));
    }

    @Test
    void recurringTransferToSavingsCountsInProjection() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");
        // Mañana (si hoy es fin de mes, cae el mes siguiente y no entra en la proyección)
        LocalDate tomorrow = TODAY.plusDays(1);
        createRecurring(token, recurring("TRANSFER", efectivo, "300", tomorrow, true)
                .with("toAccountId", ahorros));

        String expected = YearMonth.from(tomorrow).equals(YearMonth.now()) ? "-300" : "0";
        as(token).get("/api/dashboard").then().statusCode(200)
                .body("data.projectedBalance", money(expected));
    }

    @Test
    void invalidRecurringIsRejected() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        as(token).body(recurring("EXPENSE", efectivo, "10", TODAY, true))
                .post("/api/recurring").then().statusCode(400)
                .body("error.code", equalTo("TRANSACTION_CATEGORY_REQUIRED"));
    }

    @Test
    void concurrentLoadsDoNotDuplicateOccurrences() throws Exception {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long id = createRecurring(token, recurring("EXPENSE", efectivo, "10", TODAY, true)
                .with("categoryId", categoryId(token, "Suscripciones")));
        assertEquals(1L, countByRecurring(id));

        // Simula que pasó un mes sin abrir la app: la próxima ocurrencia vuelve a estar vencida
        QuarkusTransaction.requiringNew().run(() -> em
                .createQuery("UPDATE RecurringTransaction r SET r.nextDate = :d WHERE r.id = :id")
                .setParameter("d", TODAY.minusDays(1))
                .setParameter("id", id)
                .executeUpdate());

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String path = i % 2 == 0 ? "/api/dashboard" : "/api/accounts";
            results.add(pool.submit(() -> as(token).get(path).statusCode()));
        }
        for (Future<Integer> f : results) assertEquals(200, f.get());
        pool.shutdown();

        assertEquals(2L, countByRecurring(id));
    }

    // ------------------------------------------------------------------

    private static Tx recurring(String type, long accountId, String amount, LocalDate start, boolean auto) {
        Tx t = new Tx();
        t.put("type", type);
        t.put("accountId", accountId);
        t.put("amount", amount);
        t.put("frequency", "MONTHLY");
        t.put("startDate", start.toString());
        t.put("autoCreate", auto);
        return t;
    }

    private static long createRecurring(String token, Tx body) {
        Number id = as(token).body(body).post("/api/recurring").then().statusCode(201)
                .extract().path("data.id");
        return id.longValue();
    }

    private long countByRecurring(long recurringId) {
        return QuarkusTransaction.requiringNew().call(() -> em
                .createQuery("SELECT COUNT(t) FROM Transaction t WHERE t.recurringId = :id", Long.class)
                .setParameter("id", recurringId)
                .getSingleResult());
    }
}
