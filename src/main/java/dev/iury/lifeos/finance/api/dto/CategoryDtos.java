package dev.iury.lifeos.finance.api.dto;

import java.util.UUID;

import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() { }
    public record CreateRequest(@NotBlank @Size(max = 80) String name, @NotNull CategoryType type, UUID parentId,
            @Size(max = 80) String iconSlug, @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) { }
    public record UpdateRequest(@Size(max = 80) String name, @Size(max = 80) String iconSlug,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color, Integer sortOrder) { }
    public record Response(UUID id, String name, CategoryType type, UUID parentId, String iconSlug, String color,
            boolean system, boolean archived, int sortOrder) {
        public static Response from(Category value) {
            return new Response(value.id, value.name, value.type,
                    value.parentCategory == null ? null : value.parentCategory.id, value.iconSlug, value.color,
                    value.system, value.archived, value.sortOrder);
        }
    }
    public record MigrateRequest(@NotNull UUID targetCategoryId) { }
}
