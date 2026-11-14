package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.event.PaymentRecordedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.PaymentUpdatedEvent;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.AttachmentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogService activityLogService;

    public PaymentResponse create(Long householdId, PaymentRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("fromUserId and toUserId must be different");
        }

        // Only the two people the money is between may record it (see requireSenderOrReceiver).
        requireSenderOrReceiver(householdId, request.fromUserId(), request.toUserId(), currentUser,
                requesterMembership, "record");

        // Former members are allowed here: someone who moved out can still owe or be owed money,
        // and that debt must be settleable. The requester above must still be an active member.
        User fromUser = requireMember(householdId, request.fromUserId());
        User toUser = requireMember(householdId, request.toUserId());

        Payment payment = new Payment(
                requesterMembership.getHousehold(),
                fromUser,
                toUser,
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.note());
        paymentRepository.save(payment);
        activityLogService.paymentAdded(payment, currentUser);
        eventPublisher.publishEvent(new PaymentRecordedEvent(payment.getId(), currentUser.getId()));

        return toResponse(payment);
    }

    public List<PaymentResponse> list(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return paymentRepository.findByHouseholdId(householdId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PaymentResponse getById(Long householdId, Long paymentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return toResponse(payment);
    }

    // Edit rules mirror create and delete: the requester must be the sender or the receiver of the
    // payment as it is now AND as it will be after the edit. Otherwise someone could use edit to move
    // a payment between two other people.
    // paidAt is not touched (Payment.update does not change it), so the original payment time is kept.
    public PaymentResponse update(Long householdId, Long paymentId, PaymentRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        requireSenderOrReceiver(householdId, payment.getFromUser().getId(), payment.getToUser().getId(),
                currentUser, requesterMembership, "edit");
        requireSenderOrReceiver(householdId, request.fromUserId(), request.toUserId(), currentUser,
                requesterMembership, "edit");

        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("fromUserId and toUserId must be different");
        }

        User fromUser = requireMember(householdId, request.fromUserId());
        User toUser = requireMember(householdId, request.toUserId());

        // Taken before update() changes the payment, so the diff can compare old and new values.
        ActivityLogService.PaymentSnapshot before = ActivityLogService.PaymentSnapshot.of(payment);
        payment.update(fromUser, toUser, request.amount().setScale(2, RoundingMode.HALF_UP), request.note());
        boolean written = activityLogService.paymentUpdated(payment, currentUser, before);
        // Only a real change sends an edit notification. NotificationService sends it after commit.
        if (written) {
            // Both the old and the new sender/receiver hear about it (a switched person must learn too).
            Set<Long> affected = new HashSet<>(List.of(before.fromUserId(), before.toUserId(),
                    fromUser.getId(), toUser.getId()));
            BigDecimal previousAmount = before.amount().compareTo(payment.getAmount()) == 0 ? null : before.amount();
            eventPublisher.publishEvent(new PaymentUpdatedEvent(payment.getId(), currentUser.getId(),
                    previousAmount, affected));
        }
        return toResponse(payment);
    }

    public void delete(Long householdId, Long paymentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        requireSenderOrReceiver(householdId, payment.getFromUser().getId(), payment.getToUser().getId(),
                currentUser, requesterMembership, "remove");

        // Recorded first, while the payment still has its amount and both people.
        activityLogService.paymentDeleted(payment, currentUser);
        // Attachments point at this payment, so their rows and files go before the payment row.
        attachmentService.deleteForPayment(payment.getId());
        paymentRepository.delete(payment);
    }

    // Builds the response including the attachment count (one count query per payment; lists are small).
    private PaymentResponse toResponse(Payment payment) {
        int attachmentCount = (int) attachmentRepository.countByPaymentId(payment.getId());
        return PaymentResponse.from(payment, attachmentCount);
    }

    // A payment is a matter between two people, so only the sender or the receiver may record, edit
    // or remove it. Being an admin is not enough: in a home where several people are admins, one of
    // them could otherwise mark someone else's debt as paid.
    // The one exception: if the sender or the receiver is no longer in the home (moved out or deleted
    // the account), an admin may step in, so an old debt can still be settled.
    private void requireSenderOrReceiver(Long householdId, Long fromUserId, Long toUserId, User currentUser,
                                         Membership requesterMembership, String action) {
        Long me = currentUser.getId();
        if (me.equals(fromUserId) || me.equals(toUserId)) {
            return;
        }
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        boolean someoneLeft = !isActiveMember(householdId, fromUserId) || !isActiveMember(householdId, toUserId);
        if (isAdmin && someoneLeft) {
            return;
        }
        throw new ForbiddenActionException("Only the sender or the receiver can " + action + " this payment");
    }

    private boolean isActiveMember(Long householdId, Long userId) {
        return membershipRepository.findByUserIdAndHouseholdIdAndLeftAtIsNull(userId, householdId).isPresent();
    }

    private User requireMember(Long householdId, Long userId) {
        if (!membershipRepository.existsByUserIdAndHouseholdId(userId, householdId)) {
            throw new BadRequestException("User " + userId + " is not a member of this household");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
