package dev.iury.lifeos.finance.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import dev.iury.lifeos.finance.model.RecurringFrequency;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Pure date calculation logic for recurring transactions.
 * Given a reference date and a frequency, computes the next occurrence date.
 */
@ApplicationScoped
public class RecurringDateCalculator {

    /**
     * Calculates the next occurrence date after {@code referenceDate} for the given frequency.
     *
     * @param referenceDate the date to calculate from
     * @param frequency     the recurrence frequency
     * @param dayOfMonth    required for MONTHLY/BIMONTHLY/QUARTERLY/SEMI_ANNUALLY/ANNUALLY (1–28)
     * @param dayOfWeek     required for WEEKLY/BIWEEKLY
     * @return the next occurrence date
     * @throws IllegalArgumentException if required parameters are missing
     */
    public LocalDate nextDate(LocalDate referenceDate, RecurringFrequency frequency,
                              Integer dayOfMonth, DayOfWeek dayOfWeek) {
        return switch (frequency) {
            case DAILY -> referenceDate.plusDays(1);
            case WEEKLY -> {
                requireDayOfWeek(dayOfWeek);
                yield referenceDate.with(TemporalAdjusters.next(dayOfWeek));
            }
            case BIWEEKLY -> {
                requireDayOfWeek(dayOfWeek);
                LocalDate nextWeek = referenceDate.with(TemporalAdjusters.next(dayOfWeek));
                yield nextWeek.plusWeeks(1);
            }
            case MONTHLY -> {
                requireDayOfMonth(dayOfMonth);
                yield nextMonthlyDate(referenceDate, dayOfMonth, 1);
            }
            case BIMONTHLY -> {
                requireDayOfMonth(dayOfMonth);
                yield nextMonthlyDate(referenceDate, dayOfMonth, 2);
            }
            case QUARTERLY -> {
                requireDayOfMonth(dayOfMonth);
                yield nextMonthlyDate(referenceDate, dayOfMonth, 3);
            }
            case SEMI_ANNUALLY -> {
                requireDayOfMonth(dayOfMonth);
                yield nextMonthlyDate(referenceDate, dayOfMonth, 6);
            }
            case ANNUALLY -> {
                requireDayOfMonth(dayOfMonth);
                yield nextMonthlyDate(referenceDate, dayOfMonth, 12);
            }
        };
    }

    private LocalDate nextMonthlyDate(LocalDate reference, int dayOfMonth, int monthsToAdd) {
        LocalDate candidate = reference.plusMonths(monthsToAdd);
        int maxDay = candidate.lengthOfMonth();
        int adjustedDay = Math.min(dayOfMonth, maxDay);
        return candidate.withDayOfMonth(adjustedDay);
    }

    private void requireDayOfWeek(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("dayOfWeek is required for WEEKLY/BIWEEKLY frequency");
        }
    }

    private void requireDayOfMonth(Integer dayOfMonth) {
        if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 28");
        }
    }
}
