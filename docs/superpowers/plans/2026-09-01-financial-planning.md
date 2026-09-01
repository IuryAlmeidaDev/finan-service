# Financial Planning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tested domain services for budgets, rollovers, and income goals.

**Architecture:** A pure calculator owns percentages and rollover math. Budget
and goal services validate commands, use existing repositories for category and
transaction data, and return small immutable result records. Existing entities
and Flyway schema remain unchanged.

**Tech Stack:** Java 21, Quarkus 3.37.3, Panache, JUnit 5, AssertJ.

## Global Constraints

- Monetary results use `BigDecimal` at scale two; percentages use HALF_UP.
- Budget categories are EXPENSE and income-goal categories are INCOME.
- No REST, Kafka, scheduler, or migration belongs to this delivery.
- Run with Java 21; Docker-backed tests require a running Docker daemon.

---

### Task 1: Budget calculator

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/budget/BudgetCalculator.java`
- Create: `src/test/java/dev/iury/lifeos/finance/budget/BudgetCalculatorTest.java`

**Interfaces:** Produces `progress(limit, rollover, spent)`, `status(percent)`,
and `rollover(limit, rollover, spent, type)`.

- [ ] Write threshold and rollover tests, run them RED, implement the minimal
  pure calculator, then run them GREEN.

### Task 2: Budget and goal services

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/budget/BudgetService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/goal/IncomeGoalService.java`
- Create: `src/test/java/dev/iury/lifeos/finance/budget/BudgetGoalIntegrationTest.java`
- Modify: `src/main/java/dev/iury/lifeos/finance/repository/BudgetRepository.java`
- Modify: `src/main/java/dev/iury/lifeos/finance/repository/IncomeGoalRepository.java`

**Interfaces:** Services create, find, update, delete, copy and calculate
progress for their respective entities.

- [ ] Write service behaviour tests, run RED, implement validation and
  transactional copy, then run focused tests GREEN.
