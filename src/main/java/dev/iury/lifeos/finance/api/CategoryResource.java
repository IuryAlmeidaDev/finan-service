package dev.iury.lifeos.finance.api;

import java.util.List;
import java.util.UUID;
import dev.iury.lifeos.finance.api.dto.CategoryDtos;
import dev.iury.lifeos.finance.category.CategoryService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/finance/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CategoryResource {
    @Inject CategoryService categories;
    @GET public List<CategoryDtos.Response> list(@QueryParam("includeArchived") boolean includeArchived) {
        return categories.list(includeArchived).stream().map(CategoryDtos.Response::from).toList();
    }
    @GET @Path("/{id}") public CategoryDtos.Response get(@PathParam("id") UUID id) {
        return CategoryDtos.Response.from(categories.findById(id));
    }
    @POST public Response create(@Valid CategoryDtos.CreateRequest request) {
        var category = categories.create(request.name(), request.type(), request.parentId(), request.iconSlug(), request.color());
        return Response.status(Response.Status.CREATED).entity(CategoryDtos.Response.from(category)).build();
    }
    @PUT @Path("/{id}") public CategoryDtos.Response update(@PathParam("id") UUID id, @Valid CategoryDtos.UpdateRequest request) {
        categories.update(id, request.name(), request.iconSlug(), request.color(), request.sortOrder());
        return CategoryDtos.Response.from(categories.findById(id));
    }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id") UUID id) { categories.delete(id); return Response.noContent().build(); }
    @POST @Path("/{id}/migrate") public Response migrate(@PathParam("id") UUID id, @Valid CategoryDtos.MigrateRequest request) {
        return Response.ok(java.util.Map.of("migrated", categories.migrate(id, request.targetCategoryId()))).build();
    }
}
