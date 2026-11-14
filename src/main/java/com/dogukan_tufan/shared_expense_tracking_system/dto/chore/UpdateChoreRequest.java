package com.dogukan_tufan.shared_expense_tracking_system.dto.chore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// Replaces the whole chore: name, rotation order and (optionally) whose turn it is now.
// currentUserId can be left out. Then the current person is kept if still in the rotation,
// otherwise the first person in the new rotation takes over.
public record UpdateChoreRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 60, message = "Name must be at most 60 characters")
        String name,

        @NotEmpty(message = "rotationUserIds must contain at least one person")
        List<Long> rotationUserIds,

        Long currentUserId
) {
}
