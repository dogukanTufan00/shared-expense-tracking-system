package com.dogukan_tufan.shared_expense_tracking_system.exception;

public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
