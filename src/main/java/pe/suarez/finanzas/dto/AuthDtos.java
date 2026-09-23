package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    /**
     * NOTA sobre el password:
     * Antes usábamos @Size(min=8, max=80). Ahora la validación se delega a
     * {@link pe.suarez.finanzas.service.PasswordPolicy} que aplica la política
     * NIST modernizada (mín 12, máx 128, no comunes). Se mantiene un Size flexible
     * solo para evitar payloads gigantes que pasen el body limit.
     */
    public record RegisterRequest(
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(max = 200) String password,
            @Size(max = 80) String displayName,
            /** App móvil: pide además un refresh token para no volver a iniciar sesión */
            Boolean rememberDevice
    ) {}

    public record LoginRequest(
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(max = 200) String password,
            Boolean rememberDevice
    ) {}

    public record RefreshRequest(
            @NotBlank @Size(max = 100) String refreshToken
    ) {}

    /** {@code refreshToken} solo viene si se pidió {@code rememberDevice}. */
    public record TokenResponse(
            String token,
            long expiresInSeconds,
            UserResponse user,
            String refreshToken
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String displayName,
            String currencyDefault
    ) {}
}
