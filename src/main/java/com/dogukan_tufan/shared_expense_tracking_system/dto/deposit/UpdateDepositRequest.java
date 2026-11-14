package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

// Sets the deposit by hand (first setup or correction). The shares must add up to totalAmount exactly;
// the service checks that, because it needs the sum of all items.
public record UpdateDepositRequest(

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "totalAmount can have at most 2 decimal places")
        BigDecimal totalAmount,

        @NotNull(message = "shares is required")
        @Valid
        List<DepositShareInput> shares
) {

    // One person's share. Users not listed get 0.
    public record DepositShareInput(

            @NotNull(message = "userId is required in each share")
            Long userId,

            @NotNull(message = "amount is required in each share")
            @DecimalMin(value = "0.00", message = "A share cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "A share can have at most 2 decimal places")
            BigDecimal amount
    ) {
    }
}
