package dev.iury.lifeos.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import dev.iury.lifeos.finance.budget.BudgetProgress;
import dev.iury.lifeos.finance.model.Budget;
import dev.iury.lifeos.finance.model.ProgressStatus;
import dev.iury.lifeos.finance.model.RolloverType;
import jakarta.validation.constraints.*;

public final class BudgetDtos {
    private BudgetDtos() { }
    public record CreateRequest(@NotNull UUID categoryId, @Min(1) int year, @Min(1) @Max(12) int month,
            @NotNull @Positive BigDecimal limitAmount, @NotNull RolloverType rolloverType, boolean includePending) { }
    public record UpdateRequest(@NotNull @Positive BigDecimal limitAmount, @NotNull RolloverType rolloverType, boolean includePending) { }
    public record CopyRequest(@Min(1) int year, @Min(1) @Max(12) int month) { }
    public record Response(UUID id, UUID categoryId, int year, int month, BigDecimal limitAmount, RolloverType rolloverType,
            BigDecimal rolloverAmount, boolean includePending) {
        public static Response from(Budget value) { return new Response(value.id, value.category.id, value.year, value.month, value.limitAmount, value.rolloverType, value.rolloverAmount, value.includePending); }
    }
    public record ProgressResponse(BigDecimal spent, BigDecimal available, BigDecimal progress, ProgressStatus status) {
        public static ProgressResponse from(BudgetProgress value) { return new ProgressResponse(value.spent(), value.available(), value.progress(), value.status()); }
    }
}
