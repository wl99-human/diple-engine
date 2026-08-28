package com.google.payments.diple.common.domain;

/**
 * Standard GAAP / IFRS Chart of Accounts Category.
 * Defines the fundamental normal balance behavior.
 */
public enum AccountType {
    ASSET,       // Normal Balance: DEBIT (Debit increases, Credit decreases)
    LIABILITY,   // Normal Balance: CREDIT (Credit increases, Debit decreases)
    EQUITY,      // Normal Balance: CREDIT
    REVENUE,     // Normal Balance: CREDIT
    EXPENSE;     // Normal Balance: DEBIT

    public boolean isNormalDebit() {
        return this == ASSET || this == EXPENSE;
    }

    public boolean isNormalCredit() {
        return this == LIABILITY || this == EQUITY || this == REVENUE;
    }
}
