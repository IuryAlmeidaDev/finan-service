package dev.iury.lifeos.finance.api.dto;

import java.util.UUID;
import dev.iury.lifeos.finance.model.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TagDtos {
    private TagDtos() { }
    public record Request(@NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) { }
    public record Response(UUID id, String name, String color) {
        public static Response from(Tag value) { return new Response(value.id, value.name, value.color); }
    }
    public record TransactionTagsRequest(java.util.List<UUID> tagIds) { }
}
