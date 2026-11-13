package com.dogukan_tufan.shared_expense_tracking_system.dto.activity;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ActivityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// One row of a household's history. The app builds the sentence from type + data.
// The *Name fields are the users' CURRENT names, looked up when reading. For a deleted account the
// name is "Silinmis kullanici", and an id that no longer exists gives a null name.
public record ActivityResponse(
        Long id,
        ActivityType type,
        Long actorUserId,
        String actorName,
        Long subjectUserId,
        String subjectName,
        Long otherUserId,
        String otherName,
        BigDecimal amount,
        String label,
        Long expenseId,
        Long paymentId,
        // Never null: an action without field changes (e.g. member joined) has an empty list.
        List<FieldChangeResponse> changes,
        Instant createdAt
) {
}
