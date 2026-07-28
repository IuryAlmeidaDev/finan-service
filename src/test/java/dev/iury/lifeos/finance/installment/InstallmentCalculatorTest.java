package dev.iury.lifeos.finance.installment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pure unit tests for {@link InstallmentCalculator}.
 * No Quarkus context needed — this is plain math.
 */
class InstallmentCalculatorTest {

    private final InstallmentCalculator calculator = new InstallmentCalculator();
    private final LocalDate BASE_DATE = LocalDate.of(2026, 1, 15);

    // ────────────────────────────────────────────────────────────────
    // Parametrized: sum must equal total, first absorbs remainder
    // ────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "total={0} count={1}")
    @CsvSource({
            "0.01, 2",
            "1.00, 3",
            "10.00, 3",
            "100.00, 7",
            "999.99, 12",
            "1000.00, 60",
            "0.03, 2",
            "1.01, 4",
    })
    void sumOfSlicesMustEqualTotal(String total, int count) {
        BigDecimal totalAmount = new BigDecimal(total);
        List<InstallmentSlice> slices = calculator.split(totalAmount, count, BASE_DATE, "Test");

        assertThat(slices).hasSize(count);

        BigDecimal sum = slices.stream()
                .map(InstallmentSlice::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(totalAmount);

        // All slices except the first must be equal
        if (count > 1) {
            BigDecimal regularAmount = slices.get(1).amount();
            for (int i = 2; i < slices.size(); i++) {
                assertThat(slices.get(i).amount())
                        .as("Slice %d should equal regular amount", i + 1)
                        .isEqualByComparingTo(regularAmount);
            }
        }

        // First slice absorbs remainder
        BigDecimal regularAmount = slices.get(1).amount();
        BigDecimal expectedFirst = totalAmount.subtract(regularAmount.multiply(BigDecimal.valueOf(count - 1)));
        assertThat(slices.get(0).amount()).isEqualByComparingTo(expectedFirst);
    }

    // ────────────────────────────────────────────────────────────────
    // 12x end-to-end: dates and labels
    // ────────────────────────────────────────────────────────────────

    @Test
    void twelveInstallmentsProduceCorrectDatesAndLabels() {
        List<InstallmentSlice> slices = calculator.split(
                new BigDecimal("1200.00"), 12, BASE_DATE, "Notebook");

        assertThat(slices).hasSize(12);

        for (int i = 0; i < 12; i++) {
            InstallmentSlice slice = slices.get(i);
            assertThat(slice.number()).isEqualTo(i + 1);
            assertThat(slice.total()).isEqualTo(12);
            assertThat(slice.date()).isEqualTo(BASE_DATE.plusMonths(i));
            assertThat(slice.label()).isEqualTo((i + 1) + "/12");
        }

        // 1200 / 12 = 100.00 exact, no remainder
        for (InstallmentSlice slice : slices) {
            assertThat(slice.amount()).isEqualByComparingTo("100.00");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Validation
    // ────────────────────────────────────────────────────────────────

    @Test
    void rejectsZeroTotal() {
        assertThatThrownBy(() -> calculator.split(BigDecimal.ZERO, 2, BASE_DATE, "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsNegativeTotal() {
        assertThatThrownBy(() -> calculator.split(new BigDecimal("-10"), 2, BASE_DATE, "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsOneInstallment() {
        assertThatThrownBy(() -> calculator.split(new BigDecimal("100"), 1, BASE_DATE, "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    // ────────────────────────────────────────────────────────────────
    // Edge case: uneven division with small amounts
    // ────────────────────────────────────────────────────────────────

    @Test
    void unevenDivisionFirstSliceAbsorbsRemainder() {
        // 10.00 / 3 = 3.33 per regular slice, first = 10.00 - 3.33*2 = 3.34
        List<InstallmentSlice> slices = calculator.split(
                new BigDecimal("10.00"), 3, BASE_DATE, "Uneven");

        assertThat(slices.get(0).amount()).isEqualByComparingTo("3.34");
        assertThat(slices.get(1).amount()).isEqualByComparingTo("3.33");
        assertThat(slices.get(2).amount()).isEqualByComparingTo("3.33");
    }
}
