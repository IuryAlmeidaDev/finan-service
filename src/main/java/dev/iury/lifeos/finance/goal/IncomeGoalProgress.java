package dev.iury.lifeos.finance.goal;

import java.math.BigDecimal;

import dev.iury.lifeos.finance.model.ProgressStatus;

public record IncomeGoalProgress(BigDecimal received, BigDecimal target, BigDecimal progress, ProgressStatus status) {
}
