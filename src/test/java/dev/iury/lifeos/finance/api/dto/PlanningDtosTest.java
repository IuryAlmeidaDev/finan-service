package dev.iury.lifeos.finance.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Budget;
import dev.iury.lifeos.finance.model.IncomeGoal;
import dev.iury.lifeos.finance.model.Category;

class PlanningDtosTest {
    @Test
    void mapsPlanningEntitiesToApiResponses() {
        Budget budget = new Budget(); budget.id = UUID.randomUUID(); budget.limitAmount = new BigDecimal("100.00");
        IncomeGoal goal = new IncomeGoal(); goal.id = UUID.randomUUID(); goal.targetAmount = new BigDecimal("500.00");
        Category category = new Category(); category.id = UUID.randomUUID();
        budget.category = category; goal.category = category;
        assertThat(BudgetDtos.Response.from(budget).limitAmount()).isEqualByComparingTo("100.00");
        assertThat(IncomeGoalDtos.Response.from(goal).targetAmount()).isEqualByComparingTo("500.00");
    }
}
