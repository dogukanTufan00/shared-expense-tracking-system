package com.dogukan_tufan.shared_expense_tracking_system.dto.activity;

// A change as the app shows it. For person fields (paidBy, fromUser, toUser) the values are the
// people's current names instead of ids, so the app can write "Ayse -> Mehmet" directly.
// For "share" changes the values are the owed amounts, and userId and personName say whose share it is
// (personName is the person's current name). Both are null for every other field.
public record FieldChangeResponse(
        String field,
        String oldValue,
        String newValue,
        Long userId,
        String personName
) {
}
