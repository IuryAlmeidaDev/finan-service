package dev.iury.lifeos.finance.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void scalesWholeAmountsToTwoDecimalPlaces() {
        assertThat(Money.scale(new BigDecimal("10"))).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void preservesExactCents() {
        assertThat(Money.scale(new BigDecimal("0.01"))).isEqualTo(new BigDecimal("0.01"));
        assertThat(Money.cents(new BigDecimal("10.01"))).isEqualTo(1001);
        assertThat(Money.fromCents(1001)).isEqualTo(new BigDecimal("10.01"));
    }

    @Test
    void rejectsFractionsSmallerThanOneCent() {
        assertThatThrownBy(() -> Money.scale(new BigDecimal("1.001")))
                .isInstanceOf(ArithmeticException.class);
    }
}
