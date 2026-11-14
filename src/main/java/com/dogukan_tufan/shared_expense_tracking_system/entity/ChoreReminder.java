package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// A "it is your turn for this chore" nudge sent by one member to the person whose turn it is.
//
// Why a table (same reason as PaymentReminder): the 24-hour limit must be counted against rows that
// stay put. Notifications are cleaned up after 90 days and the target can read them.
// Account deletion removes these rows, because they link two people.
@Entity
@Table(name = "chore_reminders",
        indexes = @Index(name = "idx_chore_reminders_lookup", columnList = "chore_id, sender_id, sent_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChoreReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chore_id", nullable = false)
    private Chore chore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // The person whose turn it was when the reminder was sent.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public ChoreReminder(Chore chore, User sender, User target) {
        this.chore = chore;
        this.sender = sender;
        this.target = target;
        this.sentAt = Instant.now();
    }
}
