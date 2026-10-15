package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import jakarta.validation.constraints.NotBlank;

public record CreateHouseholdRequest(

        @NotBlank(message = "Name is required")
        String name
) {
}
