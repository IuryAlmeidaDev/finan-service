package dev.iury.lifeos.finance.installment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Pure calculation logic for splitting a total amount into N installments.
 * <p>
 * Rules:
 * <ul>
 *   <li>Total must be &gt; 0</li>
 *   <li>Number of installments must be &ge; 2</li>
 *   <li>All slices except the first have equal value (floor division to 2 decimal places)</li>
 *   <li>The first slice absorbs any rounding remainder so that the sum equals the total exactly</li>
 *   <li>Dates are monthly, starting from {@code firstDate} with {@code plusMonths}</li>
 *   <li>Labels follow the pattern "1/N", "2/N", etc.</li>
 * </ul>
 */
@ApplicationScoped
public class InstallmentCalculator {

    /**
     * Splits a total amount into N monthly installments.
     *
     * @param totalAmount the total to be split (must be &gt; 0)
     * @param count       number of installments (must be &ge; 2)
     * @param firstDate   due date of the first installment
     * @param description ignored here — kept for caller convenience (service layer uses it)
     * @return ordered list of slices, 1-indexed
     * @throws IllegalArgumentException if totalAmount &le; 0 or count &lt; 2
     */
    public List<InstallmentSlice> split(BigDecimal totalAmount, int count, LocalDate firstDate, String description) {
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }
        if (count < 2) {
            throw new IllegalArgumentException("Installment count must be at least 2");
        }

        BigDecimal regularSlice = totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal firstSlice = totalAmount.subtract(regularSlice.multiply(BigDecimal.valueOf(count - 1)));

        List<InstallmentSlice> slices = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            BigDecimal amount = (i == 1) ? firstSlice : regularSlice;
            LocalDate date = firstDate.plusMonths(i - 1);
            String label = i + "/" + count;
            slices.add(new InstallmentSlice(i, count, amount, date, label));
        }
        return slices;
    }
}
