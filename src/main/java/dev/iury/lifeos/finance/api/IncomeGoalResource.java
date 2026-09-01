package dev.iury.lifeos.finance.api;

import java.util.List;
import java.util.UUID;
import dev.iury.lifeos.finance.api.dto.IncomeGoalDtos;
import dev.iury.lifeos.finance.goal.IncomeGoalService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/api/finance/income-goals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IncomeGoalResource {
    @Inject IncomeGoalService goals;
    @GET public List<IncomeGoalDtos.Response> list() { return goals.list().stream().map(IncomeGoalDtos.Response::from).toList(); }
    @GET @Path("/{id}") public IncomeGoalDtos.Response get(@PathParam("id") UUID id) { return IncomeGoalDtos.Response.from(goals.findById(id)); }
    @POST public Response create(@Valid IncomeGoalDtos.CreateRequest request) { return Response.status(Response.Status.CREATED).entity(IncomeGoalDtos.Response.from(goals.create(request.categoryId(), request.year(), request.month(), request.targetAmount()))).build(); }
    @PUT @Path("/{id}") public IncomeGoalDtos.Response update(@PathParam("id") UUID id, @Valid IncomeGoalDtos.UpdateRequest request) { return IncomeGoalDtos.Response.from(goals.update(id, request.targetAmount())); }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id") UUID id) { goals.delete(id); return Response.noContent().build(); }
    @POST @Path("/{id}/copy") public Response copy(@PathParam("id") UUID id, @Valid IncomeGoalDtos.CopyRequest request) { return Response.status(Response.Status.CREATED).entity(IncomeGoalDtos.Response.from(goals.copy(id, request.year(), request.month()))).build(); }
    @GET @Path("/{id}/progress") public IncomeGoalDtos.ProgressResponse progress(@PathParam("id") UUID id) { return IncomeGoalDtos.ProgressResponse.from(goals.progress(id)); }
}
