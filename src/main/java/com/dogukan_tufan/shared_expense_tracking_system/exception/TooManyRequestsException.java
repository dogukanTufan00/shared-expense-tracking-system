package com.dogukan_tufan.shared_expense_tracking_system.exception;

// 429 for "you are doing this too often", e.g. sending the same reminder again before the cooldown ends.
// Separate from BadRequestException because the request itself is fine; it is just too soon.
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
