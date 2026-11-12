package com.dogukan_tufan.shared_expense_tracking_system.entity;

// Life cycle of an invitation. PENDING is the only state that can still change; the other three
// are final answers (accepted, declined by the invitee, cancelled by an admin).
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
