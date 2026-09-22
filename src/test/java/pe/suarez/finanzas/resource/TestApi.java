package pe.suarez.finanzas.resource;

import io.restassured.RestAssured;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;

/** Ayudantes compartidos por los tests de la API: usuarios, cuentas, movimientos y montos. */
final class TestApi {

    static final String TODAY = LocalDate.now().toString();

    static {
        RestAssured.config = RestAssured.config()
                .jsonConfig(jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));
    }

    private TestApi() {}


    static final class Tx extends HashMap<String, Object> {
        Tx with(String key, Object value) {
            put(key, value);
            return this;
        }
    }

    static Tx tx(String type, long accountId, String amount) {
        Tx t = new Tx();
        t.put("type", type);
        t.put("accountId", accountId);
        t.put("amount", amount);
        t.put("transactionDate", TODAY);
        return t;
    }

    /** Compara montos numéricamente: 0, 0.0 y 0.00 son iguales, sea entero o decimal en el JSON. */
    static Matcher<Object> money(String expected) {
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

    static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType("application/json");
    }

    static String register() {
        return given().contentType("application/json")
                .body(Map.of(
                        "email", "u" + UUID.randomUUID() + "@test.pe",
                        "password", "Una-clave-larga-2026",
                        "displayName", "Test"))
                .post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("data.token");
    }

    static void createTx(String token, Tx body) {
        as(token).body(body).post("/api/transactions").then().statusCode(201);
    }

    static long createAccount(String token, String name, String type, String currency, String initial) {
        Number id = as(token)
                .body(Map.of("name", name, "type", type, "currency", currency, "initialBalance", initial))
                .post("/api/accounts").then().statusCode(201)
                .extract().path("data.id");
        return id.longValue();
    }

    static long accountId(String token, String name) {
        Number id = as(token).get("/api/accounts").then().statusCode(200)
                .extract().path("data.find { it.name == '" + name + "' }.id");
        return id.longValue();
    }

    static long categoryId(String token, String name) {
        Number id = as(token).get("/api/categories").then().statusCode(200)
                .extract().path("data.find { it.name == '" + name + "' }.id");
        return id.longValue();
    }

    static void assertBalance(String token, long accountId, String expected) {
        as(token).get("/api/accounts/" + accountId).then().statusCode(200)
                .body("data.balance", money(expected));
    }
}
