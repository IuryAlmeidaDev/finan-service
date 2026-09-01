package dev.iury.lifeos.finance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import dev.iury.lifeos.finance.api.dto.TransactionDtos;
import dev.iury.lifeos.finance.transaction.TransactionFilter;
import dev.iury.lifeos.finance.transaction.TransactionService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/finance/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransactionResource {
    @Inject TransactionService transactions;
    @GET public List<TransactionDtos.Response> list(@QueryParam("accountId") UUID accountId, @QueryParam("categoryId") UUID categoryId,
            @QueryParam("paid") Boolean paid, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate,
            @DefaultValue("0") @QueryParam("page") int page, @DefaultValue("20") @QueryParam("size") int size) {
        TransactionFilter filter = new TransactionFilter(); filter.accountId = accountId; filter.categoryId = categoryId;
        filter.paid = paid; filter.startDate = startDate; filter.endDate = endDate;
        return transactions.search(filter, page, size).stream().map(TransactionDtos.Response::from).toList();
    }
    @GET @Path("/{id}") public TransactionDtos.Response get(@PathParam("id") UUID id) { return TransactionDtos.Response.from(transactions.findById(id)); }
    @POST public Response create(@Valid TransactionDtos.Request request) {
        return Response.status(Response.Status.CREATED).entity(TransactionDtos.Response.from(transactions.create(request.accountId(), request.type(), request.amount(), request.date(), request.categoryId(), request.description(), request.paid(), request.ignoredFromBudget()))).build();
    }
    @POST @Path("/transfers") public Response transfer(@Valid TransactionDtos.TransferRequest request) {
        return Response.status(Response.Status.CREATED).entity(TransactionDtos.Response.from(transactions.createTransfer(request.accountId(), request.destinationAccountId(), request.amount(), request.date(), request.description(), request.paid()))).build();
    }
    @PUT @Path("/{id}") public TransactionDtos.Response update(@PathParam("id") UUID id, @Valid TransactionDtos.Request request) {
        return TransactionDtos.Response.from(transactions.update(id, request.amount(), request.date(), request.categoryId(), request.description(), request.paid(), request.ignoredFromBudget()));
    }
    @POST @Path("/{id}/pay") public Response pay(@PathParam("id") UUID id) { transactions.pay(id); return Response.noContent().build(); }
    @POST @Path("/{id}/unpay") public Response unpay(@PathParam("id") UUID id) { transactions.unpay(id); return Response.noContent().build(); }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id") UUID id) { transactions.delete(id); return Response.noContent().build(); }
}
