package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// The newcomer buys into the deposit. amount is optional: when it is missing the service
// uses an equal split (totalAmount / (number of current owners + 1)).
public record JoinDepositRequest(

        @NotNull(message = "userId is required")
        Long userId,

        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "amount can have at most 2 decimal places")
        BigDecimal amount
) {
}
