package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.suarez.finanzas.resource.TestApi.*;

@QuarkusTest
class SearchAndExportTest {

    private static final String ALL = "from=2000-01-01&to=2100-12-31";

    @Test
    void filtersCombineAcrossHistory() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long transporte = categoryId(token, "Transporte");
        long comida = categoryId(token, "Alimentación");
        String lastYear = LocalDate.now().minusYears(1).toString();

        createTx(token, tx("EXPENSE", efectivo, "480").with("categoryId", transporte)
                .with("description", "SOAT Rimac").with("transactionDate", lastYear));
        createTx(token, tx("EXPENSE", efectivo, "35").with("categoryId", comida).with("description", "Tambo"));
        createTx(token, tx("EXPENSE", efectivo, "22").with("categoryId", comida).with("description", "tambo+"));
        createTx(token, tx("INCOME", efectivo, "100").with("categoryId", categoryId(token, "Freelance")));

        // Texto sin distinguir mayúsculas, en todo el historial
        as(token).get("/api/transactions?" + ALL + "&q=soat").then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].description", equalTo("SOAT Rimac"));
        // Por defecto solo el mes actual: el SOAT del año pasado no aparece
        as(token).get("/api/transactions?q=soat").then().statusCode(200).body("data", empty());

        as(token).get("/api/transactions?" + ALL + "&q=TAMBO").then().statusCode(200)
                .body("data", hasSize(2));
        as(token).get("/api/transactions?" + ALL + "&categoryId=" + comida + "&minAmount=30").then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].description", equalTo("Tambo"));
        as(token).get("/api/transactions?" + ALL + "&type=INCOME").then().statusCode(200)
                .body("data", hasSize(1));

        as(token).get("/api/transactions?from=2026-05-01&to=2026-04-01").then().statusCode(400);
    }

    @Test
    void exportsFilteredCsvForExcel() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long comida = categoryId(token, "Alimentación");
        createTx(token, tx("EXPENSE", efectivo, "35.50").with("categoryId", comida)
                .with("description", "Tambo, San Isidro"));
        createTx(token, tx("EXPENSE", efectivo, "10").with("categoryId", comida)
                .with("description", "=HYPERLINK(\"x\")"));
        createTx(token, tx("TRANSFER", efectivo, "100").with("toAccountId", accountId(token, "Ahorros")));

        byte[] body = as(token).get("/api/transactions/export?" + ALL + "&type=EXPENSE")
                .then().statusCode(200)
                .contentType(startsWith("text/csv"))
                .header("Content-Disposition", containsString("attachment"))
                .extract().asByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertTrue(csv.startsWith("﻿fecha,tipo,cuenta"), "BOM y encabezado");
        assertEquals(3, lines.length, "encabezado + 2 gastos (la transferencia se filtra)");
        assertTrue(csv.contains("\"Tambo, San Isidro\""), "texto con coma entre comillas");
        assertTrue(csv.contains(",35.50,PEN,"), "monto con punto decimal");
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""), "fórmula neutralizada para Excel");
        assertTrue(csv.contains(",Alimentación,"), "tildes en UTF-8");
    }

    @Test
    void exportRequiresAuthAndOnlyReturnsOwnData() {
        given().get("/api/transactions/export").then().statusCode(401);

        String alice = register();
        createTx(alice, tx("EXPENSE", accountId(alice, "Efectivo"), "50")
                .with("categoryId", categoryId(alice, "Otros")).with("description", "privado"));
        String bob = register();
        String csv = as(bob).get("/api/transactions/export?" + ALL).then().statusCode(200)
                .extract().asString();
        assertFalse(csv.contains("privado"));
    }
}
