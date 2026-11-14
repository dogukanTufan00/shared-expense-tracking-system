package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by ChoreService.markDone when the turn moved to someone else. The listener sends that person a
// CHORE_TURN notification after the commit. Only ids are carried; the listener reads fresh data.
public record ChoreTurnEvent(Long choreId, Long actorUserId, Long nextUserId) {
}
