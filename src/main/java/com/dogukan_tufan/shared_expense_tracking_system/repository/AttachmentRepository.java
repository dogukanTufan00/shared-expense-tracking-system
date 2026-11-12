package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByExpenseIdOrderByCreatedAtAsc(Long expenseId);

    List<Attachment> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);

    // Used for the 5-per-item limit and for the attachmentCount field on expense/payment responses.
    long countByExpenseId(Long expenseId);

    long countByPaymentId(Long paymentId);

    // Scoped by householdId, so an attachment id from another household gives 404 through this path.
    Optional<Attachment> findByIdAndHouseholdId(Long id, Long householdId);

    // Used when deleting a whole household: its files must be removed from disk too.
    List<Attachment> findByHouseholdId(Long householdId);

    // Used when deleting an expense or payment: its files must be removed from disk too.
    List<Attachment> findByExpenseId(Long expenseId);

    List<Attachment> findByPaymentId(Long paymentId);
}
