package com.google.payments.diple;

import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyValueObjectTest {

    @Test
    @DisplayName("Should create Money from minor units and perform exact integer arithmetic")
    void testMoneyArithmetic() {
        Money m1 = Money.of(1050L, "USD"); // $10.50
        Money m2 = Money.of(450L, "USD");  // $4.50

        Money sum = m1.add(m2);
        assertThat(sum.minorUnits()).isEqualTo(1500L);
        assertThat(sum.toMajor()).isEqualByComparingTo(new BigDecimal("15.00"));

        Money diff = m1.subtract(m2);
        assertThat(diff.minorUnits()).isEqualTo(600L);
        assertThat(diff.toMajor()).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    @DisplayName("Should safely parse major currency amounts without floating-point errors")
    void testMoneyOfMajor() {
        Money money = Money.ofMajor(new BigDecimal("99.99"), "USD");
        assertThat(money.minorUnits()).isEqualTo(9999L);
        assertThat(money.toFormattedString()).isEqualTo("USD 99.99");
    }

    @Test
    @DisplayName("Should prevent cross-currency addition or subtraction")
    void testCrossCurrencyMismatch() {
        Money usd = Money.of(1000L, "USD");
        Money eur = Money.of(1000L, "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch");

        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
