package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.suarez.finanzas.service.OverviewService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class OverviewTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static Map<String, Object> commitment(String name, String amount, LocalDate due) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("amount", amount);
        m.put("dueDate", due.toString());
        return m;
    }

    @Test
    void remainingSubtractsSpentAndPendingCommitments() {
        String token = register();
        long principal = accountId(token, "Principal");
        long vivienda = categoryId(token, "Vivienda");

        createTx(token, tx("INCOME", principal, "3000").with("categoryId", categoryId(token, "Salario")));
        createTx(token, tx("EXPENSE", principal, "400").with("categoryId", categoryId(token, "Alimentación")));

        // Compromiso del periodo: alquiler
        LocalDate due = TODAY.isBefore(YearMonth.now().atEndOfMonth()) ? TODAY.plusDays(1) : TODAY;
        Map<String, Object> rent = commitment("Alquiler", "1000", due);
        rent.put("categoryId", vivienda);
        Number rentId = as(token).body(rent).post("/api/commitments").then().statusCode(201)
                .body("data.status", equalTo("PENDING"))
                .body("data.categoryName", equalTo("Vivienda"))
                .extract().path("data.id");

        // Uno del mes que viene no entra todavía
        Map<String, Object> later = commitment("Matrícula", "500", YearMonth.now().plusMonths(1).atDay(15));
        later.put("categoryId", categoryId(token, "Educación"));
        as(token).body(later).post("/api/commitments").then().statusCode(201);

        int daysLeft = YearMonth.now().atEndOfMonth().getDayOfMonth() - TODAY.getDayOfMonth() + 1;
        as(token).get("/api/overview").then().statusCode(200)
                .body("data.periodStart", equalTo(YearMonth.now().atDay(1).toString()))
                .body("data.periodEnd", equalTo(YearMonth.now().atEndOfMonth().toString()))
                .body("data.daysLeft", equalTo(daysLeft))
                .body("data.income", money("3000"))
                .body("data.expectedIncome", money("3000"))
                .body("data.spent", money("400"))
                .body("data.pendingTotal", money("1000"))
                .body("data.remaining", money("1600"))
                .body("data.pending", hasSize(1))
                .body("data.pending[0].kind", equalTo("COMMITMENT"))
                .body("data.pending[0].name", equalTo("Alquiler"))
                .body("data.categories.find { it.categoryName == 'Alimentación' }.spent", money("400"));

        // Pagar: registra el gasto y el número no cambia (pasa de pendiente a gastado)
        as(token).body(Map.of("accountId", principal)).post("/api/commitments/" + rentId + "/pay")
                .then().statusCode(200)
                .body("data.status", equalTo("PAID"))
                .body("data.paidTransactionId", notNullValue());

        as(token).get("/api/overview").then().statusCode(200)
                .body("data.spent", money("1400"))
                .body("data.pendingTotal", money("0"))
                .body("data.commitmentsPaid", money("0"))
                .body("data.remaining", money("1600"))
                .body("data.pending", hasSize(0));
        assertBalance(token, principal, "1600");

        as(token).get("/api/transactions?period=" + YearMonth.now()).then().statusCode(200)
                .body("data.find { it.description == 'Alquiler' }.categoryName", equalTo("Vivienda"));
    }

    @Test
    void cardDebtCommitmentIsPaidByMovingMoney() {
        String token = register();
        long principal = accountId(token, "Principal");
        long card = createAccount(token, "Visa", "CREDIT_CARD", "PEN", "-800");

        createTx(token, tx("INCOME", principal, "2000").with("categoryId", categoryId(token, "Salario")));

        Map<String, Object> debt = commitment("Deuda Visa", "800", TODAY);
        debt.put("toAccountId", card);
        debt.put("accountId", principal);
        Number id = as(token).body(debt).post("/api/commitments").then().statusCode(201)
                .body("data.toAccountName", equalTo("Visa"))
                .extract().path("data.id");

        as(token).get("/api/overview").then().statusCode(200)
                .body("data.remaining", money("1200"))
                .body("data.pending[0].transfer", equalTo(true));

        as(token).post("/api/commitments/" + id + "/pay").then().statusCode(200)
                .body("data.status", equalTo("PAID"));

        // El pago fue "mover dinero": no es gasto, pero sigue restando de lo que queda
        as(token).get("/api/overview").then().statusCode(200)
                .body("data.spent", money("0"))
                .body("data.commitmentsPaid", money("800"))
                .body("data.remaining", money("1200"));
        assertBalance(token, principal, "1200");
        assertBalance(token, card, "0");

        // Sin categoría ni cuenta destino no se puede crear
        as(token).body(commitment("Nada", "10", TODAY)).post("/api/commitments").then().statusCode(400)
                .body("error.code", equalTo("COMMITMENT_INVALID"));
    }

    @Test
    void skipAndReopen() {
        String token = register();
        Map<String, Object> c = commitment("Colegio", "300", TODAY);
        c.put("categoryId", categoryId(token, "Educación"));
        Number id = as(token).body(c).post("/api/commitments").then().statusCode(201).extract().path("data.id");

        as(token).post("/api/commitments/" + id + "/skip").then().statusCode(200).body("data.status", equalTo("SKIPPED"));
        as(token).get("/api/overview").then().statusCode(200).body("data.pendingTotal", money("0"));
        as(token).post("/api/commitments/" + id + "/skip").then().statusCode(400);
        as(token).post("/api/commitments/" + id + "/reopen").then().statusCode(200).body("data.status", equalTo("PENDING"));
        as(token).get("/api/commitments?status=PENDING").then().statusCode(200).body("data", hasSize(1));

        // Otro usuario no lo ve ni lo toca
        String other = register();
        as(other).get("/api/commitments").then().statusCode(200).body("data", hasSize(0));
        as(other).delete("/api/commitments/" + id).then().statusCode(404);
    }

    @Test
    void paydayPeriodAndRecurringIncome() {
        String token = register();
        long principal = accountId(token, "Principal");

        // Periodo del 25 al 24
        as(token).body(Map.of("periodStartDay", 25)).put("/api/auth/me").then().statusCode(200)
                .body("data.periodStartDay", equalTo(25));
        as(token).get("/api/auth/me").then().statusCode(200).body("data.periodStartDay", equalTo(25));

        LocalDate[] p = OverviewService.periodFor(TODAY, 25);
        as(token).get("/api/overview").then().statusCode(200)
                .body("data.periodStart", equalTo(p[0].toString()))
                .body("data.periodEnd", equalTo(p[1].toString()))
                .body("data.periodStartDay", equalTo(25));

        // Volver al mes calendario
        as(token).body(Map.of("periodStartDay", 0)).put("/api/auth/me").then().statusCode(200)
                .body("data.periodStartDay", nullValue());

        // Un sueldo recurrente que todavía no cae cuenta como ingreso esperado
        LocalDate payday = YearMonth.now().atEndOfMonth();
        if (!payday.isAfter(TODAY)) return; // último día del mes: no hay "por venir"
        as(token).body(Map.of("type", "INCOME", "accountId", principal, "categoryId", categoryId(token, "Salario"),
                        "amount", "2500", "frequency", "MONTHLY", "startDate", payday.toString(), "autoCreate", true))
                .post("/api/recurring").then().statusCode(201);
        as(token).get("/api/overview").then().statusCode(200)
                .body("data.income", money("0"))
                .body("data.expectedIncome", money("2500"))
                .body("data.remaining", money("2500"));
    }

    @Test
    void periodMath() {
        assertEquals(LocalDate.of(2026, 10, 25), OverviewService.periodFor(LocalDate.of(2026, 11, 3), 25)[0]);
        assertEquals(LocalDate.of(2026, 11, 24), OverviewService.periodFor(LocalDate.of(2026, 11, 3), 25)[1]);
        assertEquals(LocalDate.of(2026, 11, 25), OverviewService.periodFor(LocalDate.of(2026, 11, 25), 25)[0]);
        assertEquals(LocalDate.of(2026, 12, 24), OverviewService.periodFor(LocalDate.of(2026, 11, 25), 25)[1]);
        assertEquals(LocalDate.of(2026, 2, 1), OverviewService.periodFor(LocalDate.of(2026, 2, 10), null)[0]);
        assertEquals(LocalDate.of(2026, 2, 28), OverviewService.periodFor(LocalDate.of(2026, 2, 10), null)[1]);
    }
}
