package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.domain.User;
import pe.suarez.finanzas.dto.AuthDtos.*;
import pe.suarez.finanzas.exception.NotFoundException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.security.RateLimited;
import pe.suarez.finanzas.security.UserContext;
import pe.suarez.finanzas.service.AuthService;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticación")
public class AuthResource {

    @Inject AuthService authService;
    @Inject UserContext userContext;
    @Inject JsonWebToken jwt;

    @POST
    @Path("/register")
    @PermitAll
    @RateLimited  // MITIGACIÓN MF-01
    public Response register(@Valid RegisterRequest req) {
        TokenResponse resp = authService.register(req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @RateLimited  // MITIGACIÓN MF-01
    public TokenResponse login(@Valid LoginRequest req) {
        return authService.login(req);
    }

    @GET
    @Path("/me")
    @Authenticated
    public UserResponse me() {
        User u = User.findById(userContext.userId());
        if (u == null) throw new NotFoundException("Usuario no encontrado");
        return Mappers.toUserResponse(u);
    }

    /**
     * Cierra la sesión del token actual. El token se agrega al denylist
     * y deja de ser válido aunque no haya expirado.
     *
     * <p>El cliente debe descartar el token tras llamar a este endpoint.
     *
     * <p><b>MITIGACIÓN MF-03</b>
     */
    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        authService.logout(jwt);
        return Response.noContent().build();
    }
}
