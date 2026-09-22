package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AccountsAndTransfersTest {

    private static final String TODAY = LocalDate.now().toString();
    private static final String PERIOD = YearMonth.now().toString();

    @BeforeAll
    static void bigDecimals() {
        RestAssured.config = RestAssured.config()
                .jsonConfig(jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));
    }

    @Test
    void transferToSavingsCountsAsSavingsNotExpense() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");

        createTx(token, tx("INCOME", efectivo, "3000").with("categoryId", categoryId(token, "Salario")));
        createTx(token, tx("EXPENSE", efectivo, "500").with("categoryId", categoryId(token, "Alimentación")));
        createTx(token, tx("TRANSFER", efectivo, "1000").with("toAccountId", ahorros));

        as(token).get("/api/dashboard?period=" + PERIOD).then().statusCode(200)
                .body("data.income", money("3000"))
                .body("data.expenses", money("500"))
                .body("data.savings", money("1000"))
                .body("data.balance", money("1500"))
                .body("data.savingsYearToDate", money("1000"))
                .body("data.bucketSummaries.find { it.bucket == 'SAVINGS' }.spent",
                        money("1000"));

        assertBalance(token, efectivo, "1500");
        assertBalance(token, ahorros, "1000");

        // El filtro por cuenta incluye la transferencia entrante
        as(token).get("/api/transactions?period=" + PERIOD + "&accountId=" + ahorros).then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].type", equalTo("TRANSFER"))
                .body("data[0].accountName", equalTo("Efectivo"))
                .body("data[0].toAccountName", equalTo("Ahorros"));
    }

    @Test
    void foreignCurrencyExpenseIsConvertedToBaseCurrency() {
        String token = register();
        long usd = createAccount(token, "BCP Dólares", "BANK", "USD", "500");
        long comida = categoryId(token, "Alimentación");

        as(token).body(tx("EXPENSE", usd, "100").with("categoryId", comida))
                .post("/api/transactions").then().statusCode(400)
                .body("error.code", equalTo("EXCHANGE_RATE_REQUIRED"));

        as(token).body(tx("EXPENSE", usd, "100").with("categoryId", comida).with("exchangeRate", "3.75"))
                .post("/api/transactions").then().statusCode(201)
                .body("data.currency", equalTo("USD"))
                .body("data.amountBase", money("375"));

        as(token).get("/api/dashboard?period=" + PERIOD).then().statusCode(200)
                .body("data.expenses", money("375"));

        as(token).get("/api/transactions/exchange-rate?currency=USD").then().statusCode(200)
                .body("data.rate", money("3.75"))
                .body("data.baseCurrency", equalTo("PEN"));

        assertBalance(token, usd, "400");
    }

    @Test
    void crossCurrencyTransferUsesImpliedRate() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long usd = createAccount(token, "Dólares", "CASH", "USD", "1000");

        as(token).body(tx("TRANSFER", usd, "100").with("toAccountId", efectivo).with("toAmount", "372"))
                .post("/api/transactions").then().statusCode(201)
                .body("data.exchangeRate", money("3.72"))
                .body("data.amountBase", money("372"))
                .body("data.toCurrency", equalTo("PEN"));

        assertBalance(token, usd, "900");
        assertBalance(token, efectivo, "372");

        // Monedas distintas sin monto recibido
        as(token).body(tx("TRANSFER", efectivo, "50").with("toAccountId", usd))
                .post("/api/transactions").then().statusCode(400)
                .body("error.code", equalTo("TRANSFER_INVALID"));

        // Mover entre cuentas corrientes no es ingreso, gasto ni ahorro
        as(token).get("/api/dashboard?period=" + PERIOD).then().statusCode(200)
                .body("data.income", money("0"))
                .body("data.expenses", money("0"))
                .body("data.savings", money("0"));

        // Comprar dólares también sugiere tipo de cambio: 380 PEN → 100 USD = 3.80
        createTx(token, tx("TRANSFER", efectivo, "380").with("toAccountId", usd).with("toAmount", "100"));
        as(token).get("/api/transactions/exchange-rate?currency=USD").then().statusCode(200)
                .body("data.rate", money("3.8"));
    }

    @Test
    void legacySavingsCategoryAndWithdrawals() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long ahorros = accountId(token, "Ahorros");

        as(token).body(tx("TRANSFER", efectivo, "10").with("toAccountId", efectivo))
                .post("/api/transactions").then().statusCode(400)
                .body("error.code", equalTo("TRANSFER_INVALID"));
        as(token).body(tx("EXPENSE", efectivo, "10"))
                .post("/api/transactions").then().statusCode(400)
                .body("error.code", equalTo("TRANSACTION_CATEGORY_REQUIRED"));

        // Categoría de gasto con bucket SAVINGS (como la antigua "Ahorro"): cuenta como ahorro
        long ahorroViejo = ((Number) as(token)
                .body(Map.of("name", "Ahorro viejo", "type", "EXPENSE", "defaultBucket", "SAVINGS"))
                .post("/api/categories").then().statusCode(201)
                .extract().path("data.id")).longValue();
        createTx(token, tx("EXPENSE", efectivo, "200").with("categoryId", ahorroViejo));
        // Retiro desde la cuenta de ahorros: resta al ahorro del mes
        createTx(token, tx("TRANSFER", ahorros, "50").with("toAccountId", efectivo));

        as(token).get("/api/dashboard?period=" + PERIOD).then().statusCode(200)
                .body("data.expenses", money("0"))
                .body("data.savings", money("150"))
                .body("data.topCategories", empty());
    }

    @Test
    void archivedAccountsAndCurrencyLock() {
        String token = register();
        long efectivo = accountId(token, "Efectivo");
        long cuenta = createAccount(token, "Interbank", "BANK", "PEN", "0");
        long otros = categoryId(token, "Otros");

        createTx(token, tx("EXPENSE", cuenta, "20").with("categoryId", otros));

        as(token).body(Map.of("name", "Interbank", "type", "BANK", "currency", "USD", "initialBalance", 0))
                .put("/api/accounts/" + cuenta).then().statusCode(400)
                .body("error.code", equalTo("ACCOUNT_CURRENCY_LOCKED"));

        as(token).delete("/api/accounts/" + cuenta).then().statusCode(204);
        as(token).get("/api/accounts").then().statusCode(200)
                .body("data.find { it.name == 'Interbank' }", nullValue());
        as(token).body(tx("EXPENSE", cuenta, "5").with("categoryId", otros))
                .post("/api/transactions").then().statusCode(400)
                .body("error.code", equalTo("ACCOUNT_ARCHIVED"));

        // Otro usuario no ve ni usa mis cuentas
        String intruso = register();
        as(intruso).body(tx("EXPENSE", efectivo, "5").with("categoryId", categoryId(intruso, "Otros")))
                .post("/api/transactions").then().statusCode(404)
                .body("error.code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    // ------------------------------------------------------------------

    private static final class Tx extends HashMap<String, Object> {
        Tx with(String key, Object value) {
            put(key, value);
            return this;
        }
    }

    private static Tx tx(String type, long accountId, String amount) {
        Tx t = new Tx();
        t.put("type", type);
        t.put("accountId", accountId);
        t.put("amount", amount);
        t.put("transactionDate", TODAY);
        return t;
    }

    /** Compara montos numéricamente: 0, 0.0 y 0.00 son iguales, sea entero o decimal en el JSON. */
    private static Matcher<Object> money(String expected) {
        BigDecimal want = new BigDecimal(expected);
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Object actual) {
                return actual instanceof Number && new BigDecimal(actual.toString()).compareTo(want) == 0;
            }

            @Override
            public void describeTo(Description d) {
                d.appendText("monto ").appendValue(want);
            }
        };
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType("application/json");
    }

    private static String register() {
        return given().contentType("application/json")
                .body(Map.of(
                        "email", "u" + UUID.randomUUID() + "@test.pe",
                        "password", "Una-clave-larga-2026",
                        "displayName", "Test"))
                .post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("data.token");
    }

    private static void createTx(String token, Tx body) {
        as(token).body(body).post("/api/transactions").then().statusCode(201);
    }

    private static long createAccount(String token, String name, String type, String currency, String initial) {
        Number id = as(token)
                .body(Map.of("name", name, "type", type, "currency", currency, "initialBalance", initial))
                .post("/api/accounts").then().statusCode(201)
                .extract().path("data.id");
        return id.longValue();
    }

    private static long accountId(String token, String name) {
        Number id = as(token).get("/api/accounts").then().statusCode(200)
                .extract().path("data.find { it.name == '" + name + "' }.id");
        return id.longValue();
    }

    private static long categoryId(String token, String name) {
        Number id = as(token).get("/api/categories").then().statusCode(200)
                .extract().path("data.find { it.name == '" + name + "' }.id");
        return id.longValue();
    }

    private static void assertBalance(String token, long accountId, String expected) {
        as(token).get("/api/accounts/" + accountId).then().statusCode(200)
                .body("data.balance", comparesEqualTo(new BigDecimal(expected)));
    }
}
