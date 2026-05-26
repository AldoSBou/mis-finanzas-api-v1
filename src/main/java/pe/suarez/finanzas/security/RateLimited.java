package pe.suarez.finanzas.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint para que sea rate-limited por IP.
 * El filter {@code RateLimitFilter} aplica el límite configurado en
 * {@code app.security.rate-limit.ip.*}.
 *
 * <p>Usar en endpoints sensibles como login y register.
 *
 * <p><b>MITIGACIÓN MF-01</b>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RateLimited {
}
