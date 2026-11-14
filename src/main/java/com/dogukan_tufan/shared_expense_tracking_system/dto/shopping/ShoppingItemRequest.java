package com.dogukan_tufan.shared_expense_tracking_system.dto.shopping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Used for both create and update. The service trims the name and turns a blank note into null.
public record ShoppingItemRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 60, message = "Name must be at most 60 characters")
        String name,

        @Size(max = 100, message = "Note must be at most 100 characters")
        String note
) {
}
