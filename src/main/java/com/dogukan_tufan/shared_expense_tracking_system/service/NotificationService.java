package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.notification.NotificationResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.notification.UnreadCountResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Invitation;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import com.dogukan_tufan.shared_expense_tracking_system.entity.NotificationType;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.PaymentReminder;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.event.ExpenseAddedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.InvitationAcceptedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.InvitationSentEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.PaymentRecordedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.PaymentReminderSentEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.RecurringExpenseGeneratedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.InvitationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.NotificationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentReminderRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Creates and serves the in-app notifications ("Ayse added an expense").
//
// How it works: ExpenseService, PaymentService and InvitationService only publish a small event
// after the action. The listeners below run AFTER the action's transaction has committed, so a
// notification exists only for something that really happened. Each listener writes its rows in a
// new transaction (same pattern as RecurringExpenseService) and catches every error, because a
// broken notification must never make the action itself fail.
//
// No class-level @Transactional on purpose: the listeners must not join the finished transaction.
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    // Notifications older than this are deleted by the nightly job below.
    private static final int RETENTION_DAYS = 90;
    // Same limit as the detail column in Notification.
    private static final int MAX_DETAIL_LENGTH = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReminderRepository paymentReminderRepository;
    private final InvitationRepository invitationRepository;
    private final MembershipRepository membershipRepository;
    private final TransactionTemplate transactionTemplate;
    private final PushService pushService;

    // ---------- Listeners (run after the action has committed) ----------

    // Each listener saves its notifications, then hands the saved rows to PushService. The push is
    // sent after the commit, so it never announces something that was rolled back.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpenseAdded(ExpenseAddedEvent event) {
        pushService.send(runInNewTransaction("EXPENSE_ADDED", created -> notifyExpenseAdded(event, created)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        pushService.send(runInNewTransaction("PAYMENT_RECORDED", created -> notifyPaymentRecorded(event, created)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationSent(InvitationSentEvent event) {
        pushService.send(runInNewTransaction("INVITATION_RECEIVED", created -> notifyInvitationSent(event, created)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        pushService.send(runInNewTransaction("INVITATION_ACCEPTED", created -> notifyInvitationAccepted(event, created)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecurringExpenseGenerated(RecurringExpenseGeneratedEvent event) {
        pushService.send(runInNewTransaction("RECURRING_EXPENSE_ADDED", created -> notifyRecurringExpenseGenerated(event, created)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentReminderSent(PaymentReminderSentEvent event) {
        pushService.send(runInNewTransaction("PAYMENT_REMINDER", created -> notifyPaymentReminderSent(event, created)));
    }

    // ---------- Building the notifications ----------

    // Everyone in the household except the person who added it. Only active members: someone who
    // moved out should not hear about new expenses.
    private void notifyExpenseAdded(ExpenseAddedEvent event, List<Notification> created) {
        Expense expense = expenseRepository.findById(event.expenseId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + event.expenseId()));
        User actor = userById(event.actorUserId());
        Household household = expense.getHousehold();

        for (Membership membership : membershipRepository.findByHouseholdIdAndLeftAtIsNull(household.getId())) {
            User recipient = membership.getUser();
            if (recipient.getId().equals(actor.getId())) {
                continue;
            }
            created.add(notificationRepository.save(Notification.builder()
                    .recipient(recipient)
                    .household(household)
                    .type(NotificationType.EXPENSE_ADDED)
                    .householdName(household.getName())
                    .actorName(actor.getName())
                    .amount(expense.getAmount())
                    .detail(truncate(expense.getDescription()))
                    .expenseId(expense.getId())
                    .build()));
        }
    }

    // Both people in the payment hear about it, unless the person who logged it is one of them
    // (then they already know). An admin can log a payment between two other people; both get it.
    private void notifyPaymentRecorded(PaymentRecordedEvent event, List<Notification> created) {
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + event.paymentId()));
        User actor = userById(event.actorUserId());
        Household household = payment.getHousehold();
        User payer = payment.getFromUser();
        User receiver = payment.getToUser();

        for (User recipient : List.of(payer, receiver)) {
            if (recipient.getId().equals(actor.getId())) {
                continue;
            }
            created.add(notificationRepository.save(Notification.builder()
                    .recipient(recipient)
                    .household(household)
                    .type(NotificationType.PAYMENT_RECORDED)
                    .householdName(household.getName())
                    .actorName(payer.getName())
                    .targetName(receiver.getName())
                    .amount(payment.getAmount())
                    .detail(truncate(payment.getNote()))
                    .paymentId(payment.getId())
                    .build()));
        }
    }

    // The invited user is the only recipient. The inviter is the actor.
    private void notifyInvitationSent(InvitationSentEvent event, List<Notification> created) {
        Invitation invitation = invitationById(event.invitationId());

        created.add(notificationRepository.save(Notification.builder()
                .recipient(invitation.getInvitedUser())
                .household(invitation.getHousehold())
                .type(NotificationType.INVITATION_RECEIVED)
                .householdName(invitation.getHousehold().getName())
                .actorName(invitation.getInvitedBy().getName())
                .invitationId(invitation.getId())
                .build()));
    }

    // The admins hear that someone joined. The new member is left out (they are not an admin anyway,
    // but the check keeps the rule explicit if roles change later).
    private void notifyInvitationAccepted(InvitationAcceptedEvent event, List<Notification> created) {
        Invitation invitation = invitationById(event.invitationId());
        User newMember = invitation.getInvitedUser();
        Household household = invitation.getHousehold();

        for (Membership membership : membershipRepository.findByHouseholdIdAndLeftAtIsNull(household.getId())) {
            if (membership.getRole() != MembershipRole.ADMIN) {
                continue;
            }
            User admin = membership.getUser();
            if (admin.getId().equals(newMember.getId())) {
                continue;
            }
            created.add(notificationRepository.save(Notification.builder()
                    .recipient(admin)
                    .household(household)
                    .type(NotificationType.INVITATION_ACCEPTED)
                    .householdName(household.getName())
                    .actorName(newMember.getName())
                    .invitationId(invitation.getId())
                    .build()));
        }
    }

    // Everyone who owes money on the new copy, plus the payer (who paid it up front). A Map keyed by
    // user id removes duplicates, e.g. when the payer also has a share. The copy is made on the
    // payer's behalf, so the payer is the actor.
    private void notifyRecurringExpenseGenerated(RecurringExpenseGeneratedEvent event, List<Notification> created) {
        Expense copy = expenseRepository.findById(event.expenseId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + event.expenseId()));
        Household household = copy.getHousehold();
        User payer = copy.getPaidBy();

        Map<Long, User> recipients = new LinkedHashMap<>();
        recipients.put(payer.getId(), payer);
        for (ExpenseShare share : expenseShareRepository.findByExpenseId(copy.getId())) {
            recipients.putIfAbsent(share.getUser().getId(), share.getUser());
        }

        for (User recipient : recipients.values()) {
            created.add(notificationRepository.save(Notification.builder()
                    .recipient(recipient)
                    .household(household)
                    .type(NotificationType.RECURRING_EXPENSE_ADDED)
                    .householdName(household.getName())
                    .actorName(payer.getName())
                    .amount(copy.getAmount())
                    .detail(truncate(copy.getDescription()))
                    .expenseId(copy.getId())
                    .build()));
        }
    }

    // Only the debtor is notified. The creditor sent it, so they already know. The amount is the one
    // stored on the reminder (the amount owed when it was sent), not the current balance.
    private void notifyPaymentReminderSent(PaymentReminderSentEvent event, List<Notification> created) {
        PaymentReminder reminder = paymentReminderRepository.findById(event.reminderId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found: " + event.reminderId()));
        Household household = reminder.getHousehold();
        User creditor = reminder.getCreditor();

        created.add(notificationRepository.save(Notification.builder()
                .recipient(reminder.getDebtor())
                .household(household)
                .type(NotificationType.PAYMENT_REMINDER)
                .householdName(household.getName())
                .actorName(creditor.getName())
                .amount(reminder.getAmount())
                .detail(truncate(reminder.getNote()))
                .build()));
    }

    // ---------- Reading and marking (the inbox endpoints) ----------

    // Latest first. limit is clamped to 1..100 instead of rejected, so any page size still gives a sane answer.
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String currentUserEmail, int limit) {
        User currentUser = getUser(currentUserEmail);
        int pageSize = Math.clamp(limit, 1, MAX_PAGE_SIZE);

        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(currentUser.getId(), PageRequest.of(0, pageSize))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(currentUser.getId()));
    }

    // Marking an already-read notification again is fine (no error).
    @Transactional
    public void markRead(Long notificationId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.markAsRead();
    }

    // Loads only the unread rows and flips them; the changes are written when the method returns.
    @Transactional
    public void markAllRead(String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        notificationRepository.findByRecipientIdAndReadFalse(currentUser.getId())
                .forEach(Notification::markAsRead);
    }

    // ---------- Nightly cleanup ----------

    // Daily at 03:00 Turkish time. Why 03:00: the inbox table would grow forever otherwise, and the
    // middle of the night is quiet (the recurring job runs at 00:05, so this does not overlap it).
    // Why 90 days: old notices are history, not something anyone acts on.
    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void deleteOldNotifications() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        log.info("Notifications: deleted {} notification(s) older than {} days", deleted, RETENTION_DAYS);
    }

    // ---------- Helpers ----------

    // Runs the work in its own transaction, after the action's transaction has committed. Any failure
    // is logged and swallowed: the expense, payment or invitation is already saved, and a missing
    // notification must not turn a successful request into an error for the user.
    // Returns the notifications that were saved, so the caller can push them. If the transaction fails,
    // the list is cleared: rolled-back rows must never be pushed.
    private List<Notification> runInNewTransaction(String what, Consumer<List<Notification>> work) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Notification> created = new ArrayList<>();
        try {
            newTransaction.executeWithoutResult(status -> work.accept(created));
        } catch (RuntimeException e) {
            log.error("Notifications: could not create {} notifications", what, e);
            created.clear();
        }
        return created;
    }

    // The detail column holds 200 characters. Cutting here keeps a long description from failing the insert.
    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_DETAIL_LENGTH ? text.substring(0, MAX_DETAIL_LENGTH) : text;
    }

    private Invitation invitationById(Long invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));
    }

    private User userById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
