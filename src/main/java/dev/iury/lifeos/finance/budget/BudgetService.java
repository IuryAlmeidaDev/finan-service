package dev.iury.lifeos.finance.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.List;

import dev.iury.lifeos.finance.common.Money;
import dev.iury.lifeos.finance.model.Budget;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.RolloverType;
import dev.iury.lifeos.finance.repository.BudgetRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BudgetService {

    @Inject BudgetRepository budgets;
    @Inject CategoryRepository categories;
    @Inject TransactionRepository transactions;
    @Inject BudgetCalculator calculator;

    public List<Budget> list() { return budgets.list("year desc, month desc"); }

    @Transactional
    public Budget create(UUID categoryId, int year, int month, BigDecimal limitAmount,
            RolloverType rolloverType, boolean includePending) {
        validatePeriod(year, month);
        Category category = expenseCategory(categoryId);
        requireMissing(categoryId, year, month);

        Budget budget = new Budget();
        budget.category = category;
        budget.year = year;
        budget.month = month;
        budget.limitAmount = positive(limitAmount, "Budget limit");
        budget.rolloverType = requireRolloverType(rolloverType);
        budget.rolloverAmount = BigDecimal.ZERO.setScale(2);
        budget.includePending = includePending;
        budgets.persist(budget);
        return budget;
    }

    public Budget findById(UUID id) {
        return budgets.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
    }

    @Transactional
    public Budget update(UUID id, BigDecimal limitAmount, RolloverType rolloverType, boolean includePending) {
        Budget budget = findById(id);
        budget.limitAmount = positive(limitAmount, "Budget limit");
        budget.rolloverType = requireRolloverType(rolloverType);
        budget.includePending = includePending;
        return budget;
    }

    @Transactional
    public void delete(UUID id) {
        budgets.delete(findById(id));
    }

    @Transactional
    public Budget copy(UUID id, int targetYear, int targetMonth) {
        validatePeriod(targetYear, targetMonth);
        Budget source = findById(id);
        requireMissing(source.category.id, targetYear, targetMonth);
        Budget copy = new Budget();
        copy.category = source.category;
        copy.year = targetYear;
        copy.month = targetMonth;
        copy.limitAmount = source.limitAmount;
        copy.rolloverType = source.rolloverType;
        copy.rolloverAmount = calculator.rollover(source.limitAmount, source.rolloverAmount,
                spent(source), source.rolloverType);
        copy.includePending = source.includePending;
        budgets.persist(copy);
        return copy;
    }

    public BudgetProgress progress(UUID id) {
        Budget budget = findById(id);
        BigDecimal spent = spent(budget);
        BigDecimal available = budget.limitAmount.add(budget.rolloverAmount).setScale(2);
        BigDecimal progress = calculator.progress(budget.limitAmount, budget.rolloverAmount, spent);
        return new BudgetProgress(spent, available, progress, calculator.status(progress));
    }

    private BigDecimal spent(Budget budget) {
        YearMonth period = YearMonth.of(budget.year, budget.month);
        return transactions.sumBudgetExpenses(budget.category.id, budget.includePending,
                period.atDay(1), period.atEndOfMonth()).setScale(2);
    }

    private Category expenseCategory(UUID id) {
        Category category = categories.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        if (category.archived || category.type != CategoryType.EXPENSE) {
            throw new IllegalArgumentException("Budget category must be an active expense category");
        }
        return category;
    }

    private void requireMissing(UUID categoryId, int year, int month) {
        if (budgets.findByCategoryAndPeriod(categoryId, year, month).isPresent()) {
            throw new IllegalArgumentException("Budget already exists for category and period");
        }
    }

    private static void validatePeriod(int year, int month) {
        if (year < 1 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid budget period");
        }
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return Money.scale(value);
    }

    private static RolloverType requireRolloverType(RolloverType value) {
        if (value == null) {
            throw new IllegalArgumentException("Rollover type is required");
        }
        return value;
    }
}
