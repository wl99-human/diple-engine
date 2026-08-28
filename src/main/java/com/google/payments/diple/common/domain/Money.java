package com.google.payments.diple.common.domain;

import com.google.payments.diple.common.exception.CurrencyMismatchException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable Money Value Object representing financial amounts in minor units (e.g. cents)
 * with strict zero-float math and currency safety.
 */
public record Money(long minorUnits, Currency currency) implements Serializable, Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "Currency must not be null");
    }

    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode.toUpperCase()));
    }

    public static Money of(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money ofMajor(BigDecimal majorAmount, String currencyCode) {
        Currency curr = Currency.getInstance(currencyCode.toUpperCase());
        int fractionDigits = Math.max(0, curr.getDefaultFractionDigits());
        long minor = majorAmount.setScale(fractionDigits, RoundingMode.UNNECESSARY)
                .movePointRight(fractionDigits)
                .longValueExact();
        return new Money(minor, curr);
    }

    public static Money zero(String currencyCode) {
        return of(0, currencyCode);
    }

    public static Money zero(Currency currency) {
        return of(0, currency);
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(Math.addExact(this.minorUnits, other.minorUnits), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(Math.subtractExact(this.minorUnits, other.minorUnits), this.currency);
    }

    public boolean isPositive() {
        return this.minorUnits > 0;
    }

    public boolean isZero() {
        return this.minorUnits == 0;
    }

    public boolean isNegative() {
        return this.minorUnits < 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        validateSameCurrency(other);
        return this.minorUnits >= other.minorUnits;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public BigDecimal toMajor() {
        int fractionDigits = Math.max(0, currency.getDefaultFractionDigits());
        return BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits);
    }

    public String toFormattedString() {
        return String.format("%s %.2f", currency.getCurrencyCode(), toMajor().doubleValue());
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other money must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: " + this.currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return Long.compare(this.minorUnits, other.minorUnits);
    }

    @Override
    public String toString() {
        return toFormattedString() + " (" + minorUnits + " minor units)";
    }
}
