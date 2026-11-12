package com.dogukan_tufan.shared_expense_tracking_system.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Used for both create (POST) and edit (PUT), so both endpoints accept exactly the same fields.
public record PaymentRequest(

        @NotNull(message = "fromUserId is required")
        Long fromUserId,

        @NotNull(message = "toUserId is required")
        Long toUserId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 200, message = "Note must be at most 200 characters")
        String note
) {
}
