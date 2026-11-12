package com.dogukan_tufan.shared_expense_tracking_system.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Sent by the app after login. language tells the server which text to use for push messages.
public record RegisterDeviceRequest(

        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token must be at most 512 characters")
        String token,

        @NotBlank(message = "Language is required")
        @Pattern(regexp = "tr|en", message = "Language must be tr or en")
        String language
) {
}
