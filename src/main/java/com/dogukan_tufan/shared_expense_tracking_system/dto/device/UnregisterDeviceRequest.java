package com.dogukan_tufan.shared_expense_tracking_system.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sent on logout. The token travels in the JSON body, not in the URL, so it does not end up in server or proxy logs.
public record UnregisterDeviceRequest(

        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token must be at most 512 characters")
        String token
) {
}
