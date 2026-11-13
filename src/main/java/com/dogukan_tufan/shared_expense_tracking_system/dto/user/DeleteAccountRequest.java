package com.dogukan_tufan.shared_expense_tracking_system.dto.user;

import jakarta.validation.constraints.NotBlank;

// The password is asked again on purpose: deleting an account is irreversible, so a stolen
// token alone must not be enough to do it.
public record DeleteAccountRequest(

        @NotBlank(message = "Password is required")
        String password
) {
}
