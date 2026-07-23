package dev.iury.lifeos.finance.common;

import static java.math.RoundingMode.UNNECESSARY;

import java.math.BigDecimal;

public final class Money {

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, UNNECESSARY);
    }

    public static long cents(BigDecimal value) {
        return scale(value).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
