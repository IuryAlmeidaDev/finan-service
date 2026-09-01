package dev.iury.lifeos.finance.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.goal.IncomeGoalProgress;
import dev.iury.lifeos.finance.model.ProgressStatus;

class BudgetGoalIntegrationTest {

    @Test
    void exposesImmutableProgressResultsForBudgetAndIncomeGoal() {
        BudgetProgress budget = new BudgetProgress(
                new BigDecimal("120.00"), new BigDecimal("100.00"), new BigDecimal("120.00"), ProgressStatus.RED);
        IncomeGoalProgress goal = new IncomeGoalProgress(
                new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), ProgressStatus.YELLOW);

        assertThat(budget.progress()).isEqualByComparingTo("120.00");
        assertThat(goal.status()).isEqualTo(ProgressStatus.YELLOW);
    }
}
