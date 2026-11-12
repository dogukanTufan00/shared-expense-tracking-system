package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;

    public PaymentResponse create(Long householdId, PaymentRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("fromUserId and toUserId must be different");
        }

        // Same rule as delete below: you can only record a payment you made yourself, unless you
        // are an admin. Otherwise any member could log "A paid B" for two other people.
        boolean isSender = request.fromUserId().equals(currentUser.getId());
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        if (!isSender && !isAdmin) {
            throw new ForbiddenActionException("Only the sender or a household admin can record this payment");
        }

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

        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> list(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return paymentRepository.findByHouseholdId(householdId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public PaymentResponse getById(Long householdId, Long paymentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return PaymentResponse.from(payment);
    }

    // Edit rules mirror create and delete:
    // - only the original sender or an admin may edit (a plain member cannot rewrite someone else's payment);
    // - a non-admin cannot hand the payment to someone else: the new sender must be the requester too,
    //   otherwise a member could use edit to make other people look like they paid.
    // paidAt is not touched (Payment.update does not change it), so the original payment time is kept.
    public PaymentResponse update(Long householdId, Long paymentId, PaymentRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        boolean isOriginalSender = payment.getFromUser().getId().equals(currentUser.getId());
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        if (!isOriginalSender && !isAdmin) {
            throw new ForbiddenActionException("Only the sender or a household admin can edit this payment");
        }
        if (!isAdmin && !request.fromUserId().equals(currentUser.getId())) {
            throw new ForbiddenActionException("Only a household admin can change the sender to another member");
        }

        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("fromUserId and toUserId must be different");
        }

        User fromUser = requireMember(householdId, request.fromUserId());
        User toUser = requireMember(householdId, request.toUserId());

        payment.update(fromUser, toUser, request.amount().setScale(2, RoundingMode.HALF_UP), request.note());
        return PaymentResponse.from(payment);
    }

    public void delete(Long householdId, Long paymentId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Payment payment = paymentRepository.findByIdAndHouseholdId(paymentId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Mirrors the Expense rule: whoever logged the payment (the sender) or an admin can undo it.
        boolean isSender = payment.getFromUser().getId().equals(currentUser.getId());
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        if (!isSender && !isAdmin) {
            throw new ForbiddenActionException("Only the sender or a household admin can remove this payment");
        }

        paymentRepository.delete(payment);
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
