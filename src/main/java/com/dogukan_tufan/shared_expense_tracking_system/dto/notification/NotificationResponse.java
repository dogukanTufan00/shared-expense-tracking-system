package com.dogukan_tufan.shared_expense_tracking_system.dto.notification;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import com.dogukan_tufan.shared_expense_tracking_system.entity.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;

// householdId is null when the household was deleted. householdName is the name at the time the
// notification was made, so it still shows after a rename or delete.
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long householdId,
        String householdName,
        String actorName,
        String targetName,
        BigDecimal amount,
        String detail,
        Long expenseId,
        Long paymentId,
        Long invitationId,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getHousehold() == null ? null : notification.getHousehold().getId(),
                notification.getHouseholdName(),
                notification.getActorName(),
                notification.getTargetName(),
                notification.getAmount(),
                notification.getDetail(),
                notification.getExpenseId(),
                notification.getPaymentId(),
                notification.getInvitationId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
