package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.balance.SettlementResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.ReminderCooldownResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.ReminderResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.SendReminderRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.PaymentReminder;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.event.PaymentReminderSentEvent;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.TooManyRequestsException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentReminderRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Lets a creditor send a polite "please pay me back" reminder to someone who owes them money.
//
// Why a cooldown: without it one person could spam a roommate every few minutes. One reminder per
// (creditor, debtor) pair per household per day is enough to nudge someone without being annoying.
// Why we reuse BalanceService.getSettlements: it already checks that the caller is an active member
// and computes who owes what. Reminders are only allowed for the amounts the app actually shows.
@Service
@RequiredArgsConstructor
public class ReminderService {

    // Length of the cooldown between two reminders from the same creditor to the same debtor.
    static final Duration REMINDER_COOLDOWN = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PaymentReminderRepository paymentReminderRepository;
    private final BalanceService balanceService;
    private final HouseholdAccessService accessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReminderResponse send(Long householdId, SendReminderRequest request, String currentUserEmail) {
        User creditor = getUser(currentUserEmail);

        // Also enforces that the caller is an active member of this household.
        List<SettlementResponse> settlements = balanceService.getSettlements(householdId, currentUserEmail);

        Long debtorId = request.debtorUserId();
        if (debtorId.equals(creditor.getId())) {
            throw new BadRequestException("You cannot remind yourself");
        }

        // The reminder is only for a debt that really exists right now: debtor -> current user.
        SettlementResponse debt = settlements.stream()
                .filter(s -> s.fromUserId().equals(debtorId) && s.toUserId().equals(creditor.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("This person does not owe you anything"));

        // Empty or spaces-only notes are stored as "no note", so the debtor never sees an empty quote.
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        Instant now = Instant.now();
        Instant cooldownStart = now.minus(REMINDER_COOLDOWN);
        if (paymentReminderRepository
                .findFirstByHouseholdIdAndCreditorIdAndDebtorIdAndSentAtAfterOrderBySentAtDesc(
                        householdId, creditor.getId(), debtorId, cooldownStart)
                .isPresent()) {
            throw new TooManyRequestsException("You already reminded this person in the last 24 hours");
        }

        Household household = accessService.requireActiveMembership(creditor.getId(), householdId).getHousehold();
        User debtor = userRepository.findById(debtorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + debtorId));

        PaymentReminder reminder = new PaymentReminder(
                household, creditor, debtor, debt.amount().setScale(2), note);
        paymentReminderRepository.save(reminder);
        // Sent after the save, in the same transaction. NotificationService listens after the commit.
        eventPublisher.publishEvent(new PaymentReminderSentEvent(reminder.getId()));

        return toResponse(reminder, debt.fromName());
    }

    // Who the current user may remind again, and when. Only reminders still inside the 24-hour window
    // are returned. The list is newest first, so the first row per debtor is the latest one.
    @Transactional(readOnly = true)
    public List<ReminderCooldownResponse> cooldowns(Long householdId, String currentUserEmail) {
        User creditor = getUser(currentUserEmail);
        accessService.requireActiveMembership(creditor.getId(), householdId);

        Instant cooldownStart = Instant.now().minus(REMINDER_COOLDOWN);
        Map<Long, Instant> latestByDebtor = new LinkedHashMap<>();
        for (PaymentReminder reminder : paymentReminderRepository
                .findByHouseholdIdAndCreditorIdAndSentAtAfterOrderBySentAtDesc(
                        householdId, creditor.getId(), cooldownStart)) {
            latestByDebtor.putIfAbsent(reminder.getDebtor().getId(), reminder.getSentAt().plus(REMINDER_COOLDOWN));
        }

        List<ReminderCooldownResponse> result = new ArrayList<>();
        latestByDebtor.forEach((debtorId, nextAllowedAt) ->
                result.add(new ReminderCooldownResponse(debtorId, nextAllowedAt)));
        return result;
    }

    private ReminderResponse toResponse(PaymentReminder reminder, String debtorName) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getDebtor().getId(),
                debtorName,
                reminder.getAmount(),
                reminder.getNote(),
                reminder.getSentAt(),
                reminder.getSentAt().plus(REMINDER_COOLDOWN));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
