package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by InvitationService.invite after a PENDING invitation is saved.
public record InvitationSentEvent(Long invitationId) {
}
