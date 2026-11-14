package com.dogukan_tufan.shared_expense_tracking_system.dto.chore;

import java.time.Instant;
import java.util.List;

// One chore as the app shows it. The rotation list is in turn order.
// reminderAvailableAt is for the CALLER: the first moment they may remind the current person again.
// It is null when they can remind now, and also when it is their own turn (a reminder is not possible then).
public record ChoreResponse(
        Long id,
        String name,
        List<MemberItem> rotation,
        Long currentUserId,
        String currentUserName,
        List<CompletionItem> recentCompletions,
        Instant reminderAvailableAt
) {

    // One person in the rotation. Names are the users' current names.
    public record MemberItem(Long userId, String name) {
    }

    // One "done" entry. Newest first in the list.
    public record CompletionItem(String userName, Instant doneAt) {
    }
}
