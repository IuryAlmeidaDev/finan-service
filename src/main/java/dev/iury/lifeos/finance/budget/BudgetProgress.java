package dev.iury.lifeos.finance.budget;

import java.math.BigDecimal;

import dev.iury.lifeos.finance.model.ProgressStatus;

public record BudgetProgress(BigDecimal spent, BigDecimal available, BigDecimal progress, ProgressStatus status) {
}
