package dev.iury.lifeos.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;

import dev.iury.lifeos.finance.model.RecurringFrequency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pure unit tests for {@link RecurringDateCalculator}.
 */
class RecurringDateCalculatorTest {

    private final RecurringDateCalculator calc = new RecurringDateCalculator();

    // ────────────────────────────────────────────────────────────────
    // DAILY
    // ────────────────────────────────────────────────────────────────

    @Test
    void dailyReturnsNextDay() {
        LocalDate ref = LocalDate.of(2026, 3, 15);
        assertThat(calc.nextDate(ref, RecurringFrequency.DAILY, null, null))
                .isEqualTo(LocalDate.of(2026, 3, 16));
    }

    // ────────────────────────────────────────────────────────────────
    // WEEKLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void weeklyReturnsNextSpecifiedDayOfWeek() {
        // 2026-03-15 is a Sunday
        LocalDate ref = LocalDate.of(2026, 3, 15);
        LocalDate next = calc.nextDate(ref, RecurringFrequency.WEEKLY, null, DayOfWeek.WEDNESDAY);
        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 18)); // next Wednesday
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    void weeklyRequiresDayOfWeek() {
        assertThatThrownBy(() -> calc.nextDate(LocalDate.now(), RecurringFrequency.WEEKLY, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayOfWeek");
    }

    // ────────────────────────────────────────────────────────────────
    // BIWEEKLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void biweeklyReturnsTwoWeeksFromNextOccurrence() {
        LocalDate ref = LocalDate.of(2026, 3, 15); // Sunday
        LocalDate next = calc.nextDate(ref, RecurringFrequency.BIWEEKLY, null, DayOfWeek.WEDNESDAY);
        // next Wednesday is 18th, plus 1 week = 25th
        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 25));
    }

    // ────────────────────────────────────────────────────────────────
    // MONTHLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void monthlyReturnsNextMonthOnSpecifiedDay() {
        LocalDate ref = LocalDate.of(2026, 1, 10);
        assertThat(calc.nextDate(ref, RecurringFrequency.MONTHLY, 15, null))
                .isEqualTo(LocalDate.of(2026, 2, 15));
    }

    @Test
    void monthlyCapsToMonthEnd() {
        // Feb 2026 has 28 days, requesting day 28 in a month after Jan
        LocalDate ref = LocalDate.of(2026, 1, 28);
        assertThat(calc.nextDate(ref, RecurringFrequency.MONTHLY, 28, null))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void monthlyRequiresDayOfMonth() {
        assertThatThrownBy(() -> calc.nextDate(LocalDate.now(), RecurringFrequency.MONTHLY, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayOfMonth");
    }

    @Test
    void monthlyRejectsDayOfMonthAbove28() {
        assertThatThrownBy(() -> calc.nextDate(LocalDate.now(), RecurringFrequency.MONTHLY, 29, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // BIMONTHLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void bimonthlyReturnsTwoMonthsLater() {
        LocalDate ref = LocalDate.of(2026, 1, 10);
        assertThat(calc.nextDate(ref, RecurringFrequency.BIMONTHLY, 10, null))
                .isEqualTo(LocalDate.of(2026, 3, 10));
    }

    // ────────────────────────────────────────────────────────────────
    // QUARTERLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void quarterlyReturnsThreeMonthsLater() {
        LocalDate ref = LocalDate.of(2026, 1, 5);
        assertThat(calc.nextDate(ref, RecurringFrequency.QUARTERLY, 5, null))
                .isEqualTo(LocalDate.of(2026, 4, 5));
    }

    // ────────────────────────────────────────────────────────────────
    // SEMI_ANNUALLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void semiAnnuallyReturnsSixMonthsLater() {
        LocalDate ref = LocalDate.of(2026, 1, 1);
        assertThat(calc.nextDate(ref, RecurringFrequency.SEMI_ANNUALLY, 1, null))
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    // ────────────────────────────────────────────────────────────────
    // ANNUALLY
    // ────────────────────────────────────────────────────────────────

    @Test
    void annuallyReturnsTwelveMonthsLater() {
        LocalDate ref = LocalDate.of(2026, 6, 15);
        assertThat(calc.nextDate(ref, RecurringFrequency.ANNUALLY, 15, null))
                .isEqualTo(LocalDate.of(2027, 6, 15));
    }

    // ────────────────────────────────────────────────────────────────
    // All frequencies produce a date after reference
    // ────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(RecurringFrequency.class)
    void nextDateIsAlwaysAfterReference(RecurringFrequency freq) {
        LocalDate ref = LocalDate.of(2026, 6, 1);
        Integer dayOfMonth = switch (freq) {
            case DAILY, WEEKLY, BIWEEKLY -> null;
            default -> 15;
        };
        DayOfWeek dayOfWeek = switch (freq) {
            case WEEKLY, BIWEEKLY -> DayOfWeek.FRIDAY;
            default -> null;
        };
        LocalDate next = calc.nextDate(ref, freq, dayOfMonth, dayOfWeek);
        assertThat(next).isAfter(ref);
    }
}
