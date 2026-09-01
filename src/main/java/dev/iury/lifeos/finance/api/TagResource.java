package dev.iury.lifeos.finance.api;

import java.util.List;
import java.util.UUID;
import dev.iury.lifeos.finance.api.dto.TagDtos;
import dev.iury.lifeos.finance.tag.TagService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/finance/tags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TagResource {
    @Inject TagService tags;
    @GET public List<TagDtos.Response> list() { return tags.list().stream().map(TagDtos.Response::from).toList(); }
    @POST public Response create(@Valid TagDtos.Request request) {
        return Response.status(Response.Status.CREATED).entity(TagDtos.Response.from(tags.create(request.name(), request.color()))).build();
    }
    @PUT @Path("/{id}") public TagDtos.Response update(@PathParam("id") UUID id, @Valid TagDtos.Request request) {
        return TagDtos.Response.from(tags.update(id, request.name(), request.color()));
    }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id") UUID id) { tags.delete(id); return Response.noContent().build(); }
    @GET @Path("/transactions/{transactionId}") public List<TagDtos.Response> transactionTags(@PathParam("transactionId") UUID transactionId) {
        return tags.getTransactionTags(transactionId).stream().map(TagDtos.Response::from).toList();
    }
    @PUT @Path("/transactions/{transactionId}") public Response setTransactionTags(@PathParam("transactionId") UUID transactionId,
            @Valid TagDtos.TransactionTagsRequest request) { tags.setTransactionTags(transactionId, request.tagIds()); return Response.noContent().build(); }
}
