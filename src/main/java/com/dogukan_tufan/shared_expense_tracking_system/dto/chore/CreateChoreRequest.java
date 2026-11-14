package com.dogukan_tufan.shared_expense_tracking_system.dto.chore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// The service trims the name, so "  Bulasik  " is stored as "Bulasik".
// rotationUserIds is the turn order: the first id is the first person whose turn it is.
public record CreateChoreRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 60, message = "Name must be at most 60 characters")
        String name,

        @NotEmpty(message = "rotationUserIds must contain at least one person")
        List<Long> rotationUserIds
) {
}
