package pe.suarez.finanzas.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {

    @Test
    void registerAndLoginRoundtrip() {
        // Registro
        given()
                .contentType("application/json")
                .body("""
                        {
                          "email": "test@suarez.pe",
                          "password": "Una-clave-larga-2026",
                          "displayName": "Test User"
                        }
                        """)
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("data.token", notNullValue())
                .body("data.user.email", equalTo("test@suarez.pe"))
                .body("data.user.currencyDefault", equalTo("PEN"));

        // Login
        given()
                .contentType("application/json")
                .body("""
                        {
                          "email": "test@suarez.pe",
                          "password": "Una-clave-larga-2026"
                        }
                        """)
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("data.token", notNullValue());
    }

    @Test
    void loginWithBadCredentialsReturns401() {
        given()
                .contentType("application/json")
                .body("""
                        {"email":"noexiste@suarez.pe","password":"xxxxxxxx"}
                        """)
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice@suarez.pe")
    @JwtSecurity(claims = { @Claim(key = "uid", value = "1") })
    void meEndpointRequiresAuth() {
        // Solo verifica que el endpoint pase autenticación; el 404 esperable si no existe el user 1
        given()
                .when().get("/api/auth/me")
                .then().statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void categoriesEndpointWithoutTokenReturns401() {
        given()
                .when().get("/api/categories")
                .then().statusCode(401);
    }
}
