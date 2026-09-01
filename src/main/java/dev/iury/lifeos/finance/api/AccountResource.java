package dev.iury.lifeos.finance.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.account.AccountService;
import dev.iury.lifeos.finance.api.dto.AccountDtos;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

@Path("/api/finance/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject AccountService accounts;

    @GET
    public List<AccountDtos.Response> list(@QueryParam("includeArchived") boolean includeArchived) {
        return accounts.list(includeArchived).stream().map(AccountDtos.Response::from).toList();
    }

    @POST
    public Response create(@Valid AccountDtos.Request request, @Context UriInfo uriInfo) {
        var account = accounts.create(request.name(), request.type(), request.initialBalance(), request.initialBalanceDate(),
                request.color(), request.iconSlug(), request.includeInTotal());
        URI location = uriInfo.getAbsolutePathBuilder().path(account.id.toString()).build();
        return Response.created(location).entity(AccountDtos.Response.from(account)).build();
    }

    @GET
    @Path("/{id}")
    public AccountDtos.Response get(@PathParam("id") UUID id) {
        return AccountDtos.Response.from(accounts.findById(id));
    }

    @PUT
    @Path("/{id}")
    public AccountDtos.Response update(@PathParam("id") UUID id, @Valid AccountDtos.Request request) {
        return AccountDtos.Response.from(accounts.update(id, request.name(), request.type(), request.initialBalance(),
                request.initialBalanceDate(), request.color(), request.iconSlug(), request.includeInTotal()));
    }

    @GET
    @Path("/{id}/balance")
    public AccountDtos.BalanceResponse balance(@PathParam("id") UUID id) {
        return AccountDtos.BalanceResponse.from(accounts.balance(id));
    }

    @POST
    @Path("/{id}/archive")
    public Response archive(@PathParam("id") UUID id) {
        accounts.archive(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/unarchive")
    public Response unarchive(@PathParam("id") UUID id) {
        accounts.unarchive(id);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id, @Valid AccountDtos.DeleteRequest request) {
        accounts.delete(id, request.confirmation());
        return Response.noContent().build();
    }
}
