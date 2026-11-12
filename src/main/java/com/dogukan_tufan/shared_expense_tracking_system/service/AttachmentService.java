package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.attachment.AttachmentResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Attachment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.AttachmentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

// Receipts, bills and transfer slips ("dekont") attached to expenses and payments.
// Same access pattern as the other household services: the caller must be an ACTIVE member
// (non-members get 404), and the expense/payment/attachment must belong to that household.
@Service
@RequiredArgsConstructor
@Transactional
public class AttachmentService {

    private static final int MAX_ATTACHMENTS_PER_ITEM = 5;

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final AttachmentRepository attachmentRepository;
    private final HouseholdAccessService accessService;
    private final AttachmentStorage storage;

    // What the download endpoint returns: the bytes as a Resource, plus the metadata for the headers.
    public record DownloadedFile(Resource resource, AttachmentResponse info) {
    }

    public AttachmentResponse uploadToExpense(Long householdId, Long expenseId, MultipartFile file,
                                              String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);
        Expense expense = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));

        CheckedFile checked = checkFile(file);
        if (attachmentRepository.countByExpenseId(expenseId) >= MAX_ATTACHMENTS_PER_ITEM) {
            throw new ConflictException("An expense can have at most " + MAX_ATTACHMENTS_PER_ITEM + " attachments");
        }

        Attachment attachment = new Attachment(expense.getHousehold(), expense, null, currentUser,
                originalNameOf(file), checked.type().contentType(), checked.data().length,
                storage.save(checked.data(), checked.type().extension()));
        attachmentRepository.save(attachment);
        return AttachmentResponse.from(attachment);
    }

    public AttachmentResponse uploadToPayment(Long householdId, Long paymentId, MultipartFile file,
                                              String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);
        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        CheckedFile checked = checkFile(file);
        if (attachmentRepository.countByPaymentId(paymentId) >= MAX_ATTACHMENTS_PER_ITEM) {
            throw new ConflictException("A payment can have at most " + MAX_ATTACHMENTS_PER_ITEM + " attachments");
        }

        Attachment attachment = new Attachment(payment.getHousehold(), null, payment, currentUser,
                originalNameOf(file), checked.type().contentType(), checked.data().length,
                storage.save(checked.data(), checked.type().extension()));
        attachmentRepository.save(attachment);
        return AttachmentResponse.from(attachment);
    }

    public List<AttachmentResponse> listForExpense(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);
        expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));

        return attachmentRepository.findByExpenseIdOrderByCreatedAtAsc(expenseId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    public List<AttachmentResponse> listForPayment(Long householdId, Long paymentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);
        paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        return attachmentRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    public DownloadedFile download(Long householdId, Long attachmentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);
        Attachment attachment = findAttachment(householdId, attachmentId);

        Resource resource = storage.load(attachment.getStoredName());
        // The row can exist while the file is gone (e.g. someone deleted it from the disk by hand).
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Attachment file is missing on the server: " + attachmentId);
        }
        return new DownloadedFile(resource, AttachmentResponse.from(attachment));
    }

    // Who may delete an attachment: whoever uploaded it, the payer of the expense (or the sender of
    // the payment), or any household admin. Everyone else gets 403.
    public void delete(Long householdId, Long attachmentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        Attachment attachment = findAttachment(householdId, attachmentId);

        boolean isUploader = attachment.getUploadedBy().getId().equals(currentUser.getId());
        boolean isPayerOrSender = isPayerOrSender(attachment, currentUser);
        boolean isAdmin = membership.getRole() == MembershipRole.ADMIN;
        if (!isUploader && !isPayerOrSender && !isAdmin) {
            throw new ForbiddenActionException(
                    "Only the uploader, the payer or sender, or a household admin can delete this attachment");
        }

        deleteAttachments(List.of(attachment));
    }

    // Called by ExpenseService / PaymentService / HouseholdService before they delete their parent row.
    // The rows must go first (they point at the parent), and then the files.
    public void deleteForExpense(Long expenseId) {
        deleteAttachments(attachmentRepository.findByExpenseId(expenseId));
    }

    public void deleteForPayment(Long paymentId) {
        deleteAttachments(attachmentRepository.findByPaymentId(paymentId));
    }

    public void deleteForHousehold(Long householdId) {
        deleteAttachments(attachmentRepository.findByHouseholdId(householdId));
    }

    // ---------- Rows first, then files ----------

    private void deleteAttachments(List<Attachment> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        // Remember the file names before the rows are gone, then delete the rows.
        List<String> storedNames = attachments.stream().map(Attachment::getStoredName).toList();
        attachmentRepository.deleteAll(attachments);

        // The files are removed only AFTER the database transaction commits. If something later
        // in the same request fails and the transaction rolls back, the rows come back -- and so
        // must their files, so we must not have deleted them yet. A file cannot be "rolled back".
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storedNames.forEach(storage::delete);
                }
            });
        } else {
            storedNames.forEach(storage::delete);
        }
    }

    // ---------- File checks ----------

    private record FileType(String extension, String contentType) {
    }

    private record CheckedFile(byte[] data, FileType type) {
    }

    // Reads the upload and decides its type. Empty, missing or unsupported files are rejected with 400.
    private CheckedFile checkFile(MultipartFile file) {
        if (file == null) {
            throw new BadRequestException("A file part named 'file' is required");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }

        FileType type = detectType(data);
        if (type == null) {
            throw new BadRequestException("Only JPEG, PNG, WEBP images and PDF files are allowed");
        }
        return new CheckedFile(data, type);
    }

    // We look at the first bytes ("magic bytes") of the file, NOT at the Content-Type header or the
    // file name. Both are typed by the client and can be faked: an .exe renamed to "receipt.pdf"
    // would pass a name check. The first bytes are fixed by the file format itself.
    private static FileType detectType(byte[] data) {
        if (hasBytesAt(data, 0, 0xFF, 0xD8, 0xFF)) {
            return new FileType("jpg", "image/jpeg");
        }
        if (hasBytesAt(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new FileType("png", "image/png");
        }
        // WEBP is a RIFF container: "RIFF", 4 size bytes, then "WEBP". Checking only "RIFF" would
        // also accept WAV audio files.
        if (hasAsciiAt(data, 0, "RIFF") && hasAsciiAt(data, 8, "WEBP")) {
            return new FileType("webp", "image/webp");
        }
        if (hasAsciiAt(data, 0, "%PDF")) {
            return new FileType("pdf", "application/pdf");
        }
        return null;
    }

    private static boolean hasBytesAt(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            // "& 0xFF" because Java bytes are signed; 0xFF must compare equal to the raw byte 0xFF.
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAsciiAt(byte[] data, int offset, String text) {
        return hasBytesAt(data, offset, text.chars().toArray());
    }

    // The name the user had on their device. Only for display, so we keep just the last part
    // (some browsers send "C:\Users\...\receipt.pdf") and cap the length to the column size.
    private static String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "attachment";
        }
        String base = name.replaceAll(".*[\\\\/]", "");
        if (base.isBlank()) {
            return "attachment";
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    // ---------- Small helpers ----------

    private boolean isPayerOrSender(Attachment attachment, User currentUser) {
        if (attachment.getExpense() != null) {
            return attachment.getExpense().getPaidBy().getId().equals(currentUser.getId());
        }
        if (attachment.getPayment() != null) {
            return attachment.getPayment().getFromUser().getId().equals(currentUser.getId());
        }
        return false;
    }

    private Attachment findAttachment(Long householdId, Long attachmentId) {
        return attachmentRepository.findByIdAndHouseholdId(attachmentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
