package pe.suarez.finanzas.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.suarez.finanzas.domain.User;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class JwtIssuer {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "app.jwt.duration-seconds", defaultValue = "28800")
    long durationSeconds;

    public String issueFor(User user) {
        return Jwt.issuer(issuer)
                .upn(user.email)
                .subject(user.email)
                .claim("uid", user.id)
                .claim("name", user.displayName != null ? user.displayName : user.email)
                .groups(Set.of("user"))
                .expiresIn(Duration.ofSeconds(durationSeconds))
                .sign();
    }

    public long durationSeconds() {
        return durationSeconds;
    }
}
