# Financial Planning Design

## Scope

Implement the domain layer for monthly expense budgets and income goals. This
delivery does not add REST resources, schedules, Kafka consumers, or schema
changes: the `budget` and `income_goal` tables and their entities already
exist.

## Rules

- A budget belongs to one expense category and a year/month. It has a positive
  limit, a rollover policy, and may include unpaid expenses.
- A goal belongs to one income category and a year/month and has a positive
  target.
- Category aggregates include descendants; budget aggregates ignore deleted
  transactions and `ignoredFromBudget`; goal aggregates use paid income and
  ignore `ignoredFromReports`.
- Budget status is GREEN below 80%, YELLOW below 100%, and RED at or above
  100%. Percentages use scale two and HALF_UP; a zero denominator yields zero.
- The next budget receives the previous remaining value (`limit + rollover -
  spent`) according to `FULL_ROLLOVER`, `POSITIVE_ONLY`, or `NO_ROLLOVER`.
- Copying to another month is atomic and fails if either a budget or goal for
  the destination category/period already exists.

## Design

`BudgetCalculator` is a pure Java class that exposes progress/status and
rollover calculations. `BudgetService` and `IncomeGoalService` validate
periods, category type, value, and uniqueness, then delegate persistence and
aggregates to existing repositories. Value objects expose calculated results
without putting business logic in JPA entities.

## Verification

Unit tests cover calculation thresholds, all rollover modes, and zero targets.
Integration tests cover category descendants, paid/pending handling, validation
and atomic copying when Docker-backed Quarkus tests are available.
