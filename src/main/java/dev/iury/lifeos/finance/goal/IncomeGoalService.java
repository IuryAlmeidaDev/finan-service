package dev.iury.lifeos.finance.goal;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import java.util.List;

import dev.iury.lifeos.finance.budget.BudgetCalculator;
import dev.iury.lifeos.finance.common.Money;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.IncomeGoal;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.IncomeGoalRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IncomeGoalService {

    @Inject IncomeGoalRepository goals;
    @Inject CategoryRepository categories;
    @Inject TransactionRepository transactions;
    @Inject BudgetCalculator calculator;

    public List<IncomeGoal> list() {
        return goals.findAll(Sort.by("year", Sort.Direction.Descending)
                .and("month", Sort.Direction.Descending)).list();
    }

    @Transactional
    public IncomeGoal create(UUID categoryId, int year, int month, BigDecimal targetAmount) {
        validatePeriod(year, month);
        Category category = incomeCategory(categoryId);
        requireMissing(categoryId, year, month);

        IncomeGoal goal = new IncomeGoal();
        goal.category = category;
        goal.year = year;
        goal.month = month;
        goal.targetAmount = positive(targetAmount);
        goals.persist(goal);
        return goal;
    }

    public IncomeGoal findById(UUID id) {
        return goals.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Income goal not found: " + id));
    }

    @Transactional
    public IncomeGoal update(UUID id, BigDecimal targetAmount) {
        IncomeGoal goal = findById(id);
        goal.targetAmount = positive(targetAmount);
        return goal;
    }

    @Transactional
    public void delete(UUID id) {
        goals.delete(findById(id));
    }

    @Transactional
    public IncomeGoal copy(UUID id, int targetYear, int targetMonth) {
        validatePeriod(targetYear, targetMonth);
        IncomeGoal source = findById(id);
        requireMissing(source.category.id, targetYear, targetMonth);
        IncomeGoal copy = new IncomeGoal();
        copy.category = source.category;
        copy.year = targetYear;
        copy.month = targetMonth;
        copy.targetAmount = source.targetAmount;
        goals.persist(copy);
        return copy;
    }

    public IncomeGoalProgress progress(UUID id) {
        IncomeGoal goal = findById(id);
        YearMonth period = YearMonth.of(goal.year, goal.month);
        BigDecimal received = transactions.sumIncomeGoal(goal.category.id, period.atDay(1), period.atEndOfMonth())
                .setScale(2);
        BigDecimal progress = calculator.progress(goal.targetAmount, BigDecimal.ZERO, received);
        return new IncomeGoalProgress(received, goal.targetAmount, progress, calculator.status(progress));
    }

    private Category incomeCategory(UUID id) {
        Category category = categories.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        if (category.archived || category.type != CategoryType.INCOME) {
            throw new IllegalArgumentException("Income goal category must be an active income category");
        }
        return category;
    }

    private void requireMissing(UUID categoryId, int year, int month) {
        if (goals.findByCategoryAndPeriod(categoryId, year, month).isPresent()) {
            throw new IllegalArgumentException("Income goal already exists for category and period");
        }
    }

    private static void validatePeriod(int year, int month) {
        if (year < 1 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid income goal period");
        }
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Income goal target must be greater than zero");
        }
        return Money.scale(value);
    }
}
