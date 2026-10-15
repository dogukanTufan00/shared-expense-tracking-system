package com.dogukan_tufan.shared_expense_tracking_system.exception;

// 400 for a business-rule check on the request body that isn't a plain field-format check (those
// go through @Valid/MethodArgumentNotValidException instead) -- e.g. "paidBy must be an active
// member of this household" needs a database lookup, not just an annotation on the field.
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
