package dev.iury.lifeos.finance.installment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single installment slice produced by {@link InstallmentCalculator}.
 *
 * @param number    1-based installment number
 * @param total     total number of installments
 * @param amount    value of this slice (first slice absorbs the rounding remainder)
 * @param date      due date for this slice
 * @param label     formatted label, e.g. "1/12"
 */
public record InstallmentSlice(
        int number,
        int total,
        BigDecimal amount,
        LocalDate date,
        String label) {
}
