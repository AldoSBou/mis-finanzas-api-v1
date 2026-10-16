package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class MetricsTest {

    private static Map<String, Object> entry(int ms, String source) {
        return Map.of("kind", "ENTRY", "durationMs", ms, "source", source, "platform", "android",
                "appVersion", "1.1.0", "occurredAt", Instant.now().toString());
    }

    @Test
    void entriesAreSummarizedPerUser() {
        String token = register();

        as(token).get("/api/metrics/summary").then().statusCode(200)
                .body("data.entries", equalTo(0))
                .body("data.medianMs", nullValue());

        as(token).body(List.of(
                        entry(4000, "app"),
                        entry(8000, "notification"),
                        entry(12000, "app"),
                        entry(500_000, "app"), // la app quedó abierta: no cuenta
                        Map.of("kind", "ERROR", "source", "save", "message", "Sin conexión",
                                "occurredAt", Instant.now().toString())))
                .post("/api/metrics/events").then().statusCode(201)
                .body("data.accepted", equalTo(5));

        as(token).get("/api/metrics/summary?days=7").then().statusCode(200)
                .body("data.entries", equalTo(3))
                .body("data.medianMs", equalTo(8000))
                .body("data.under10Pct", equalTo(67))
                .body("data.fromNotification", equalTo(1))
                .body("data.errors", equalTo(1));

        // Otro usuario no ve nada
        as(register()).get("/api/metrics/summary").then().statusCode(200)
                .body("data.entries", equalTo(0))
                .body("data.errors", equalTo(0));
    }

    @Test
    void rejectsInvalidEvents() {
        String token = register();
        as(token).body(List.of(Map.of("kind", "ENTRY", "durationMs", -1, "occurredAt", Instant.now().toString())))
                .post("/api/metrics/events").then().statusCode(400);
        as(token).body(List.of(Map.of("kind", "NOPE", "occurredAt", Instant.now().toString())))
                .post("/api/metrics/events").then().statusCode(400);
    }

    @Test
    void newUsersStartWithOneGeneralAccount() {
        String token = register();
        as(token).get("/api/accounts").then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].name", equalTo("Principal"))
                .body("data[0].type", equalTo("GENERAL"));
    }
}
