package com.dogukan_tufan.shared_expense_tracking_system.dto.attachment;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Attachment;

import java.time.Instant;

// Metadata only. The file itself is fetched from GET .../attachments/{id}/file.
// expenseId and paymentId: exactly one is non-null, telling the app what the file belongs to.
public record AttachmentResponse(
        Long id,
        Long expenseId,
        Long paymentId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Long uploadedById,
        String uploadedByName,
        Instant createdAt
) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getExpense() == null ? null : attachment.getExpense().getId(),
                attachment.getPayment() == null ? null : attachment.getPayment().getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedBy().getName(),
                attachment.getCreatedAt());
    }
}
