package com.dogukan_tufan.shared_expense_tracking_system.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A Java record: an immutable, final class whose fields, constructor, getters (name() instead of
// getName()) and equals/hashCode/toString are all generated from this one declaration. It fits
// request/response DTOs well because they are just "a fixed bag of data" with no behavior to add.
// jakarta.validation annotations go directly on the record components; @Valid in the controller
// triggers them.
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        String iban
) {
}
