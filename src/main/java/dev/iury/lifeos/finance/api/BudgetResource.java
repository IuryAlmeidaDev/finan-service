package dev.iury.lifeos.finance.api;

import java.util.List;
import java.util.UUID;
import dev.iury.lifeos.finance.api.dto.BudgetDtos;
import dev.iury.lifeos.finance.budget.BudgetService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/api/finance/budgets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BudgetResource {
    @Inject BudgetService budgets;
    @GET public List<BudgetDtos.Response> list() { return budgets.list().stream().map(BudgetDtos.Response::from).toList(); }
    @GET @Path("/{id}") public BudgetDtos.Response get(@PathParam("id") UUID id) { return BudgetDtos.Response.from(budgets.findById(id)); }
    @POST public Response create(@Valid BudgetDtos.CreateRequest request) { return Response.status(Response.Status.CREATED).entity(BudgetDtos.Response.from(budgets.create(request.categoryId(), request.year(), request.month(), request.limitAmount(), request.rolloverType(), request.includePending()))).build(); }
    @PUT @Path("/{id}") public BudgetDtos.Response update(@PathParam("id") UUID id, @Valid BudgetDtos.UpdateRequest request) { return BudgetDtos.Response.from(budgets.update(id, request.limitAmount(), request.rolloverType(), request.includePending())); }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id") UUID id) { budgets.delete(id); return Response.noContent().build(); }
    @POST @Path("/{id}/copy") public Response copy(@PathParam("id") UUID id, @Valid BudgetDtos.CopyRequest request) { return Response.status(Response.Status.CREATED).entity(BudgetDtos.Response.from(budgets.copy(id, request.year(), request.month()))).build(); }
    @GET @Path("/{id}/progress") public BudgetDtos.ProgressResponse progress(@PathParam("id") UUID id) { return BudgetDtos.ProgressResponse.from(budgets.progress(id)); }
}
