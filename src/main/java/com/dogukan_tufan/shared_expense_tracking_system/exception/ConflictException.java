package com.dogukan_tufan.shared_expense_tracking_system.exception;

// Generic 409: used for business-rule conflicts that aren't the specific "email already
// registered" case (e.g. household already has 3 active members, user already an active member).
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
