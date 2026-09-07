package com.google.payments.diple.api.dto;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class AccountDtos {

    public record CreateAccountRequest(
            @NotBlank(message = "Account ID must not be blank") String id,
            @NotBlank(message = "Owner ID must not be blank") String ownerId,
            @NotNull(message = "Account type is required") AccountType accountType,
            @NotBlank(message = "Currency code is required") String currency,
            @PositiveOrZero(message = "Initial balance must be zero or positive") long initialBalanceMinorUnits
    ) {}

    public record AccountResponse(
            String id,
            String ownerId,
            AccountType accountType,
            String currency,
            long postedBalanceMinorUnits,
            long pendingDebitsMinorUnits,
            long availableBalanceMinorUnits,
            AccountStatus status,
            String formattedAvailableBalance
    ) {}
}
