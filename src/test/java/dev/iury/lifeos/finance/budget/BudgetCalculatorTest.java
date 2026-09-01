package dev.iury.lifeos.finance.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.ProgressStatus;
import dev.iury.lifeos.finance.model.RolloverType;

class BudgetCalculatorTest {

    private final BudgetCalculator calculator = new BudgetCalculator();

    @Test
    void calculatesProgressAndStatusAtTheThresholds() {
        assertThat(calculator.progress(new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.status(new BigDecimal("79.99"))).isEqualTo(ProgressStatus.GREEN);
        assertThat(calculator.status(new BigDecimal("80.00"))).isEqualTo(ProgressStatus.YELLOW);
        assertThat(calculator.status(new BigDecimal("99.99"))).isEqualTo(ProgressStatus.YELLOW);
        assertThat(calculator.status(new BigDecimal("100.00"))).isEqualTo(ProgressStatus.RED);
    }

    @Test
    void calculatesRemainingBudgetAccordingToRolloverPolicy() {
        BigDecimal limit = new BigDecimal("100.00");
        BigDecimal rollover = new BigDecimal("20.00");

        assertThat(calculator.rollover(limit, rollover, new BigDecimal("50.00"), RolloverType.FULL_ROLLOVER))
                .isEqualByComparingTo("70.00");
        assertThat(calculator.rollover(limit, rollover, new BigDecimal("150.00"), RolloverType.FULL_ROLLOVER))
                .isEqualByComparingTo("-30.00");
        assertThat(calculator.rollover(limit, rollover, new BigDecimal("150.00"), RolloverType.POSITIVE_ONLY))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.rollover(limit, rollover, new BigDecimal("50.00"), RolloverType.NO_ROLLOVER))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void returnsZeroProgressWhenTheAvailableAmountIsZero() {
        assertThat(calculator.progress(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00")))
                .isEqualByComparingTo("0.00");
    }
}
