package com.dogukan_tufan.shared_expense_tracking_system.exception;

import java.time.Instant;

// The one JSON error shape every error response uses (CLAUDE.md section 9), e.g.:
// { "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/..." }
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
