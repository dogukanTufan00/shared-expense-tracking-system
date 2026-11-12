package com.dogukan_tufan.shared_expense_tracking_system.dto.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email
) {
}
