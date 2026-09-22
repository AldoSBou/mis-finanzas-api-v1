package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.AccountDtos.*;
import pe.suarez.finanzas.service.AccountService;

import java.util.List;

@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Cuentas")
public class AccountResource {

    @Inject AccountService service;

    @GET
    public List<AccountResponse> list(@QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
        return service.listAll(includeArchived);
    }

    @GET
    @Path("/{id}")
    public AccountResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    @POST
    public Response create(@Valid AccountRequest req) {
        AccountResponse resp = service.create(req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @PUT
    @Path("/{id}")
    public AccountResponse update(@PathParam("id") Long id, @Valid AccountRequest req) {
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response archive(@PathParam("id") Long id) {
        service.archive(id);
        return Response.noContent().build();
    }
}
