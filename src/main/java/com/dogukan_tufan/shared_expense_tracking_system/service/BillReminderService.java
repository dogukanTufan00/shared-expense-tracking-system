package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Sends "your bill is due soon" pushes for recurring expenses (for example rent 3 days before it is due).
//
// Works on chain ends only, like RecurringExpenseService: the newest expense of each chain holds the
// reminder setting and remembers the last due date it reminded for (billReminderSentFor).
// Not @Transactional at class level on purpose: each chain gets its own transaction (see remindChain),
// so one failing chain does not stop the others.
@Service
@Slf4j
@RequiredArgsConstructor
public class BillReminderService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationService notificationService;
    private final PushService pushService;
    private final TransactionTemplate transactionTemplate;

    // Every hour from 09:00 to 21:00 Turkish time. Why hourly and not once a day: a server that was
    // off in the morning still reminds people later the same day. Why not at night: nobody wants a push
    // at 3 a.m. Each due date is reminded only once (billReminderSentFor), so the extra runs do nothing.
    @Scheduled(cron = "0 0 9-21 * * *", zone = "Europe/Istanbul")
    public void sendDueReminders() {
        List<Long> chainEndIds = expenseRepository.findByRecurringTrue().stream().map(Expense::getId).toList();
        int sent = sendForChains(chainEndIds);
        log.info("Bill reminders: sent {} notification(s) across {} chain(s)", sent, chainEndIds.size());
    }

    // Manual trigger (called by RecurringExpenseService.runForHousehold, which already checked admin rights).
    public int sendDueRemindersForHousehold(Long householdId) {
        List<Long> chainEndIds = expenseRepository.findByHouseholdIdAndRecurringTrue(householdId).stream()
                .map(Expense::getId)
                .toList();
        return sendForChains(chainEndIds);
    }

    // Returns how many notifications were created. Pushes go out only after each chain's transaction
    // has committed, so a rolled-back reminder is never pushed.
    private int sendForChains(List<Long> chainEndIds) {
        int total = 0;
        for (Long chainEndId : chainEndIds) {
            try {
                List<Notification> created = transactionTemplate.execute(status -> remindChain(chainEndId));
                pushService.send(created);
                total += created.size();
            } catch (RuntimeException e) {
                log.error("Bill reminders: could not check chain ending at expense {}", chainEndId, e);
            }
        }
        return total;
    }

    // Decides and saves the reminders for one chain. Returns the notifications created (maybe none).
    private List<Notification> remindChain(Long chainEndId) {
        // Locked, so the hourly job and a manual run cannot both remind for the same due date.
        Expense chainEnd = expenseRepository.findByIdForUpdate(chainEndId).orElse(null);
        if (chainEnd == null || !chainEnd.isRecurring()) {
            return List.of();
        }

        int daysBefore = chainEnd.effectiveReminderDaysBefore();
        if (daysBefore == 0) {
            return List.of();
        }

        // The due date is the same one the generator will use for the next copy (no second copy of the rule).
        LocalDate due = RecurringExpenseService.nextDateAfter(chainEnd).toLocalDate();
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(RecurringExpenseService.ZONE), due);
        // Only the window before the due date. Today or past (daysLeft <= 0) is left to the generator.
        if (daysLeft < 1 || daysLeft > daysBefore) {
            return List.of();
        }
        if (due.equals(chainEnd.getBillReminderSentFor())) {
            return List.of();
        }

        Long householdId = chainEnd.getHousehold().getId();
        Set<Long> activeUserIds = new HashSet<>();
        for (Membership membership : membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId)) {
            activeUserIds.add(membership.getUser().getId());
        }

        // Why skip without marking: the generator stops this chain soon anyway, so no reminder is needed.
        User payer = chainEnd.getPaidBy();
        if (!activeUserIds.contains(payer.getId())) {
            return List.of();
        }

        // Payer first with a null share ("you pay it"). Then everyone with a share who is still a member.
        // The payer is skipped in the loop, so they get exactly one notification.
        Map<User, BigDecimal> recipients = new LinkedHashMap<>();
        recipients.put(payer, null);
        for (ExpenseShare share : expenseShareRepository.findByExpenseId(chainEnd.getId())) {
            User user = share.getUser();
            boolean owes = share.getOwedAmount().signum() > 0;
            if (owes && activeUserIds.contains(user.getId()) && !user.getId().equals(payer.getId())) {
                recipients.put(user, share.getOwedAmount());
            }
        }

        List<Notification> created = notificationService.saveBillDueSoon(chainEnd, (int) daysLeft, recipients);
        // Remembered in the same transaction as the notifications: both are saved, or neither is.
        chainEnd.setBillReminderSentFor(due);
        return created;
    }
}
