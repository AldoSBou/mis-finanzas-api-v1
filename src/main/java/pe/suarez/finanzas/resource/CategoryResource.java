package pe.suarez.finanzas.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.suarez.finanzas.dto.CategoryDtos.*;
import pe.suarez.finanzas.service.CategoryService;

import java.util.List;

@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Categorías")
public class CategoryResource {

    @Inject CategoryService service;

    @GET
    public List<CategoryResponse> list(@QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
        return service.listAll(includeArchived);
    }

    @GET
    @Path("/{id}")
    public CategoryResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    @POST
    public Response create(@Valid CategoryRequest req) {
        CategoryResponse resp = service.create(req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @PUT
    @Path("/{id}")
    public CategoryResponse update(@PathParam("id") Long id, @Valid CategoryRequest req) {
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    public Response archive(@PathParam("id") Long id) {
        service.archive(id);
        return Response.noContent().build();
    }
}
