package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// A photo or PDF (receipt, bill, bank transfer slip) attached to either an expense or a payment.
// The row holds the metadata only; the bytes live on disk under storedName (see AttachmentStorage).
// Exactly one of expense / payment is set. household is always set, so household-level access
// checks and the household delete cleanup can work without walking through expense or payment.
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    // The name the user had on their device. Shown in the app only, never used as a file path.
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Our own generated name on disk, e.g. "3f2a...c1.jpg". Unique so two files can never overwrite each other.
    @Column(name = "stored_name", nullable = false, unique = true, length = 100)
    private String storedName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Attachment(Household household, Expense expense, Payment payment, User uploadedBy,
                      String originalFilename, String contentType, long sizeBytes, String storedName) {
        this.household = household;
        this.expense = expense;
        this.payment = payment;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storedName = storedName;
        this.createdAt = Instant.now();
    }
}
