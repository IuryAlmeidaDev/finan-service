package dev.iury.lifeos.finance.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.iury.lifeos.finance.model.ProgressStatus;
import dev.iury.lifeos.finance.model.RolloverType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BudgetCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal EIGHTY = new BigDecimal("80");

    public BigDecimal progress(BigDecimal limitAmount, BigDecimal rolloverAmount, BigDecimal spent) {
        BigDecimal available = limitAmount.add(rolloverAmount);
        if (available.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return spent.multiply(HUNDRED).divide(available, 2, RoundingMode.HALF_UP);
    }

    public ProgressStatus status(BigDecimal progress) {
        if (progress.compareTo(EIGHTY) < 0) {
            return ProgressStatus.GREEN;
        }
        if (progress.compareTo(HUNDRED) < 0) {
            return ProgressStatus.YELLOW;
        }
        return ProgressStatus.RED;
    }

    public BigDecimal rollover(BigDecimal limitAmount, BigDecimal rolloverAmount, BigDecimal spent,
            RolloverType rolloverType) {
        BigDecimal remaining = limitAmount.add(rolloverAmount).subtract(spent).setScale(2);
        return switch (rolloverType) {
            case NO_ROLLOVER -> BigDecimal.ZERO.setScale(2);
            case FULL_ROLLOVER -> remaining;
            case POSITIVE_ONLY -> remaining.max(BigDecimal.ZERO.setScale(2));
        };
    }
}
