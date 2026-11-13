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

// One entry in a household's activity history ("Bulent changed Rent: amount 30000.00 -> 31000.00").
//
// Why data and not finished sentences: the app builds the text in the user's language, so the same
// row reads correctly in TR or EN and the server does not need to know the locale.
// Why user ids (actorUserId, subjectUserId, otherUserId) and not copied names: when an account is
// deleted, its users row is anonymized to "Silinmis kullanici". Because the history only stores ids,
// it anonymizes itself too. Names are looked up when the history is read (ActivityLogService.list).
// Why plain ids (expenseId, paymentId) and not foreign keys: the expense or payment may be deleted
// later, and the history must stay. A foreign key would block that delete.
// Why the household link is a real foreign key: it is deleted together with the household
// (HouseholdService.purgeHousehold removes these rows first), so no orphan rows are left behind.
// Why changes is a JSON text: it is a list of {field, oldValue, newValue}. Only fields that really
// changed are stored, so the app can show "amount 30000.00 -> 31000.00" for an edit.
// Rows are written in the same transaction as the action itself (see ActivityLogService), so an
// action that is rolled back leaves no history entry.
@Entity
@Table(name = "activity_logs",
        indexes = @Index(name = "idx_activity_logs_household_created", columnList = "household_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ActivityType type;

    // Who did it. Null for actions nobody started (the recurring job creating the next month).
    @Column(name = "actor_user_id")
    private Long actorUserId;

    // The person the action is about (the new member, the removed member, the payer of an expense).
    @Column(name = "subject_user_id")
    private Long subjectUserId;

    // A second person when there is one (the receiver of a payment).
    @Column(name = "other_user_id")
    private Long otherUserId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    // Expense description at that moment, or the household name. Capped at 200 characters in the service.
    @Column(length = 200)
    private String label;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "payment_id")
    private Long paymentId;

    // JSON array of {"field", "oldValue", "newValue"}. Stored as text; the service reads and writes it.
    @Column(columnDefinition = "text")
    private String changes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public ActivityLog(Household household, ActivityType type, Long actorUserId, Long subjectUserId,
                       Long otherUserId, BigDecimal amount, String label, Long expenseId, Long paymentId,
                       String changes) {
        this.household = household;
        this.type = type;
        this.actorUserId = actorUserId;
        this.subjectUserId = subjectUserId;
        this.otherUserId = otherUserId;
        this.amount = amount;
        this.label = label;
        this.expenseId = expenseId;
        this.paymentId = paymentId;
        this.changes = changes;
        this.createdAt = Instant.now();
    }
}
