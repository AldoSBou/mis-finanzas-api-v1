package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static pe.suarez.finanzas.resource.TestApi.as;

@QuarkusTest
class RefreshTokenTest {

    private static final String PASSWORD = "Una-clave-larga-2026";

    @Test
    void webLoginDoesNotGetRefreshToken() {
        String email = newUser();
        login(email, false).then().statusCode(200).body("data.refreshToken", nullValue());
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSessionAlive() {
        String email = newUser();
        String first = login(email, true).then().statusCode(200)
                .body("data.refreshToken", notNullValue())
                .extract().path("data.refreshToken");

        String second = refresh(first).then().statusCode(200)
                .body("data.token", notNullValue())
                .body("data.user.email", equalTo(email))
                .extract().path("data.refreshToken");
        String access = refresh(second).then().statusCode(200).extract().path("data.token");
        as(access).get("/api/auth/me").then().statusCode(200).body("data.email", equalTo(email));
    }

    @Test
    void reusingARotatedTokenClosesEverySession() {
        String email = newUser();
        String first = login(email, true).then().extract().path("data.refreshToken");
        String second = refresh(first).then().statusCode(200).extract().path("data.refreshToken");

        // Alguien reusa el token viejo (posible robo): se rechaza y cae también el vigente
        refresh(first).then().statusCode(401).body("error.code", equalTo("AUTH_TOKEN_REVOKED"));
        refresh(second).then().statusCode(401);
    }

    @Test
    void logoutWithRefreshTokenClosesTheDevice() {
        String email = newUser();
        var resp = login(email, true).then().extract();
        String access = resp.path("data.token");
        String refreshToken = resp.path("data.refreshToken");

        as(access).body(Map.of("refreshToken", refreshToken)).post("/api/auth/logout").then().statusCode(204);
        refresh(refreshToken).then().statusCode(401);
        refresh("no-existe").then().statusCode(401).body("error.code", equalTo("AUTH_TOKEN_INVALID"));

        // El logout de la web (sin cuerpo) sigue funcionando
        String web = login(email, false).then().extract().path("data.token");
        as(web).post("/api/auth/logout").then().statusCode(204);
    }

    // ---------------------------------------------------------------

    private static String newUser() {
        String email = "u" + UUID.randomUUID() + "@test.pe";
        given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "displayName", "Test"))
                .post("/api/auth/register").then().statusCode(201);
        return email;
    }

    private static io.restassured.response.Response login(String email, boolean remember) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "rememberDevice", remember))
                .post("/api/auth/login");
    }

    private static io.restassured.response.Response refresh(String token) {
        return given().contentType("application/json")
                .body(Map.of("refreshToken", token))
                .post("/api/auth/refresh");
    }
}
