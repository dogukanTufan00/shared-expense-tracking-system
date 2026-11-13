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

import java.math.BigDecimal;
import java.time.Instant;

// One "please pay me back" reminder sent by a creditor to a debtor inside a household.
//
// Why a table and not just a notification: the 24-hour limit has to be checked against something
// that stays put. Notifications get cleaned up after 90 days and can be read by the debtor, so
// they are a poor place to count reminders from. This row is the record of what was sent.
// Why the amount is copied: the reminder says "you owe X" as it was at send time. It must not change
// when balances change later.
// Why the note is personal data: it is written by the creditor about the debtor, so account deletion
// removes it (see AccountDeletionService).
@Entity
@Table(name = "payment_reminders",
        indexes = @Index(name = "idx_payment_reminders_lookup",
                columnList = "household_id, creditor_id, debtor_id, sent_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    // The person who is owed the money and sent the reminder.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", nullable = false)
    private User creditor;

    // The person who owes the money and received the reminder.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debtor_id", nullable = false)
    private User debtor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Optional short message from the creditor. Capped at 200 characters, same as other notes.
    @Column(length = 200)
    private String note;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public PaymentReminder(Household household, User creditor, User debtor, BigDecimal amount, String note) {
        this.household = household;
        this.creditor = creditor;
        this.debtor = debtor;
        this.amount = amount;
        this.note = note;
        this.sentAt = Instant.now();
    }
}
