package com.dogukan_tufan.shared_expense_tracking_system.dto.invitation;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Invitation;
import com.dogukan_tufan.shared_expense_tracking_system.entity.InvitationStatus;

import java.time.Instant;

public record InvitationResponse(
        Long id,
        Long householdId,
        String householdName,
        Long invitedUserId,
        String invitedUserName,
        String invitedUserEmail,
        Long invitedById,
        String invitedByName,
        InvitationStatus status,
        Instant createdAt
) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getHousehold().getId(),
                invitation.getHousehold().getName(),
                invitation.getInvitedUser().getId(),
                invitation.getInvitedUser().getName(),
                invitation.getInvitedUser().getEmail(),
                invitation.getInvitedBy().getId(),
                invitation.getInvitedBy().getName(),
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
