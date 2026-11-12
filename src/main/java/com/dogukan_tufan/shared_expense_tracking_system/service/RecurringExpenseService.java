package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.RecurringExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.RecurringRunResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// Creates the next expense of every recurring chain once its date has come.
// A "chain" is a row of expenses copied month after month. Only the newest one has recurring=true,
// so the generator only looks at chain ends, and each chain is advanced one month at a time.
//
// Not @Transactional at class level on purpose: each chain runs in its own small transaction
// (see generateForChain). If one chain fails, only its own work is rolled back and the others go on.
@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringExpenseService {

    // The rule "every month on day X" is about Turkish calendar days. Using this zone (not the
    // server's clock zone) decides "is it due yet", so a server in another zone still acts on Turkish time.
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    // Catch-up after downtime is bounded: at most this many months per chain in one run. A longer
    // outage is caught up over several runs instead of creating a flood of rows at once.
    private static final int MAX_MONTHS_PER_RUN = 12;

    private final ExpenseRepository expenseRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final HouseholdAccessService accessService;
    private final ExpenseService expenseService;
    private final TransactionTemplate transactionTemplate;

    // Daily at 00:05 Turkish time. Needs @EnableScheduling on the application class.
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Istanbul")
    public void generateDueForAllHouseholds() {
        List<Long> chainEndIds = expenseRepository.findByRecurringTrue().stream().map(Expense::getId).toList();
        int generated = generateForChains(chainEndIds);
        log.info("Recurring expenses: generated {} new expense(s) across {} chain(s)", generated, chainEndIds.size());
    }

    // Also runs once when the server starts. Why: the daily job does nothing while the server is off,
    // so a restart after midnight catches up right away instead of waiting for the next day.
    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartup() {
        try {
            generateDueForAllHouseholds();
        } catch (RuntimeException e) {
            // A failure here must not stop the application from starting.
            log.error("Recurring expenses: startup run failed", e);
        }
    }

    // Runs right after a recurring expense is saved (create or update) and that transaction has
    // committed, so the copies can see the saved row. AFTER_COMMIT has no usable transaction of its
    // own any more, so this run uses a brand-new one (REQUIRES_NEW).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecurringExpenseSaved(RecurringExpenseSavedEvent event) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            Integer generated = newTransaction.execute(status -> generateForChain(event.expenseId()));
            if (generated != null && generated > 0) {
                log.info("Recurring expenses: filled in {} due month(s) for expense {}", generated, event.expenseId());
            }
        } catch (RuntimeException e) {
            // The expense itself is already saved; a failure here only delays the copies until the nightly run.
            log.error("Recurring expenses: could not fill in due months for expense {}", event.expenseId(), e);
        }
    }

    // Manual trigger for demos and tests (admin only). Same logic, limited to one household.
    public RecurringRunResponse runForHousehold(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(membership);

        List<Long> chainEndIds = expenseRepository.findByHouseholdIdAndRecurringTrue(householdId).stream()
                .map(Expense::getId)
                .toList();
        return new RecurringRunResponse(generateForChains(chainEndIds));
    }

    // Read side: every chain end of this household, with the date its next copy will get.
    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> listForHousehold(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return expenseRepository.findByHouseholdIdAndRecurringTrue(householdId).stream()
                .map(expense -> new RecurringExpenseResponse(
                        expense.getId(),
                        expense.getDescription(),
                        expense.getCategory(),
                        expense.getAmount(),
                        expense.getPaidBy().getId(),
                        expense.getPaidBy().getName(),
                        recurrenceDayOf(expense),
                        expense.getExpenseDate(),
                        nextDateAfter(expense)))
                .toList();
    }

    // ---------- Generation ----------

    // Each chain gets its own transaction. A failing chain is logged and skipped; its own rows roll back.
    private int generateForChains(List<Long> chainEndIds) {
        int total = 0;
        for (Long chainEndId : chainEndIds) {
            try {
                Integer generated = transactionTemplate.execute(status -> generateForChain(chainEndId));
                total += generated == null ? 0 : generated;
            } catch (RuntimeException e) {
                log.error("Recurring expenses: could not advance chain ending at expense {}", chainEndId, e);
            }
        }
        return total;
    }

    // Creates the missing months of one chain, up to MAX_MONTHS_PER_RUN, and returns how many it made.
    private int generateForChain(Long chainEndId) {
        // Locked, so two runs at the same time cannot both create the same month (see the repository).
        Expense last = expenseRepository.findByIdForUpdate(chainEndId).orElse(null);
        if (last == null || !last.isRecurring()) {
            return 0;
        }

        // Rows created before recurrenceDay existed get it now; the copies below then inherit it.
        if (last.getRecurrenceDay() == null) {
            last.setRecurrenceDay(recurrenceDayOf(last));
        }

        // Due is decided by the calendar DAY, not the time: the app stores expenses at 12:00, so a
        // time comparison would push "today's" rent to tomorrow's nightly run.
        LocalDate today = LocalDate.now(ZONE);
        if (nextDateAfter(last).toLocalDate().isAfter(today)) {
            return 0;
        }

        // Why: never charge a bill to someone who has already moved out. The chain is stopped
        // (recurring=false on the latest), so nothing more is created for it, and it is logged.
        Long payerId = last.getPaidBy().getId();
        boolean payerStillActive = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(payerId, last.getHousehold().getId())
                .isPresent();
        if (!payerStillActive) {
            last.setRecurring(false);
            log.info("Recurring expenses: stopped chain at expense {} because payer {} is no longer a member",
                    last.getId(), payerId);
            return 0;
        }

        int generated = 0;
        Expense previous = last;
        while (generated < MAX_MONTHS_PER_RUN) {
            LocalDateTime next = nextDateAfter(previous);
            if (next.toLocalDate().isAfter(today)) {
                break;
            }
            previous = expenseService.createRecurringCopy(previous, next);
            generated++;
        }
        return generated;
    }

    // ---------- Dates ----------

    // The day the chain repeats on. Falls back to the expense's own day for rows that have none yet.
    private static int recurrenceDayOf(Expense expense) {
        return expense.getRecurrenceDay() != null
                ? expense.getRecurrenceDay()
                : expense.getExpenseDate().getDayOfMonth();
    }

    // Same time of day, one month later, on the chain's day. A shorter month uses its last day.
    // Example: day 31 -> 28 Feb -> 31 Mar. The day comes from the chain, not from the previous copy,
    // so the 28 Feb clamp does not move the chain to the 28th for good.
    private static LocalDateTime nextDateAfter(Expense previous) {
        LocalDate nextMonth = previous.getExpenseDate().toLocalDate().plusMonths(1);
        int day = Math.min(recurrenceDayOf(previous), nextMonth.lengthOfMonth());
        return nextMonth.withDayOfMonth(day).atTime(previous.getExpenseDate().toLocalTime());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
