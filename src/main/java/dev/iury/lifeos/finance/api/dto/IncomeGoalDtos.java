package dev.iury.lifeos.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import dev.iury.lifeos.finance.goal.IncomeGoalProgress;
import dev.iury.lifeos.finance.model.IncomeGoal;
import dev.iury.lifeos.finance.model.ProgressStatus;
import jakarta.validation.constraints.*;

public final class IncomeGoalDtos {
    private IncomeGoalDtos() { }
    public record CreateRequest(@NotNull UUID categoryId, @Min(1) int year, @Min(1) @Max(12) int month, @NotNull @Positive BigDecimal targetAmount) { }
    public record UpdateRequest(@NotNull @Positive BigDecimal targetAmount) { }
    public record CopyRequest(@Min(1) int year, @Min(1) @Max(12) int month) { }
    public record Response(UUID id, UUID categoryId, int year, int month, BigDecimal targetAmount) {
        public static Response from(IncomeGoal value) { return new Response(value.id, value.category.id, value.year, value.month, value.targetAmount); }
    }
    public record ProgressResponse(BigDecimal received, BigDecimal target, BigDecimal progress, ProgressStatus status) {
        public static ProgressResponse from(IncomeGoalProgress value) { return new ProgressResponse(value.received(), value.target(), value.progress(), value.status()); }
    }
}
