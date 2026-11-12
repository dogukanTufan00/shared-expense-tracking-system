package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by InvitationService.accept after the new Membership is saved. The person who accepted
// is the invited user of the invitation, so the listener does not need an actor id.
public record InvitationAcceptedEvent(Long invitationId) {
}
