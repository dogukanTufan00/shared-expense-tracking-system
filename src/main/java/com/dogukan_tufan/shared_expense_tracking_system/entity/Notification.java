package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// One entry in a user's in-app inbox ("Ayse added an expense").
//
// Why data and not finished sentences: the app builds the text in the user's language, so the same
// row reads correctly in TR or EN and the server does not need to know the locale.
// Why copied names (householdName, actorName, targetName): the text must keep making sense after
// the household is renamed or deleted, or after a user changes their name.
// Why plain ids (expenseId, paymentId, invitationId) and not foreign keys: the target row may be
// deleted later, and the notification is just history. A foreign key would block that delete.
// Why the household link is nullable: a household delete could clear it, but HouseholdService
// deletes the notifications instead (see there for the reason).
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "household_name", nullable = false)
    private String householdName;

    // Who did it (the payer for a payment, the inviter for an invitation). Nullable by design.
    @Column(name = "actor_name")
    private String actorName;

    // The other person, when there is one: the receiver of a payment.
    @Column(name = "target_name")
    private String targetName;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    // Expense description or payment note. Capped at 200 characters; longer text is cut in the service.
    @Column(length = 200)
    private String detail;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "invitation_id")
    private Long invitationId;

    // Named is_read because READ is a keyword in some databases and SQL tools.
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Notification(User recipient, Household household, NotificationType type, String householdName,
                        String actorName, String targetName, BigDecimal amount, String detail,
                        Long expenseId, Long paymentId, Long invitationId) {
        this.recipient = recipient;
        this.household = household;
        this.type = type;
        this.householdName = householdName;
        this.actorName = actorName;
        this.targetName = targetName;
        this.amount = amount;
        this.detail = detail;
        this.expenseId = expenseId;
        this.paymentId = paymentId;
        this.invitationId = invitationId;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public void markAsRead() {
        this.read = true;
    }
}
