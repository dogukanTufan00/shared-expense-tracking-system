package com.dogukan_tufan.shared_expense_tracking_system.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Email is not in this request on purpose: it is the login name and the JWT subject, so changing
// it would break existing tokens. iban is optional: blank or null removes it.
public record UpdateProfileRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        String iban
) {
}
