package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.activity.ActivityResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.activity.FieldChange;
import com.dogukan_tufan.shared_expense_tracking_system.dto.activity.FieldChangeResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ActivityLog;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ActivityType;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.SplitType;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ActivityLogRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

// Writes and reads the household activity history ("Bulent changed Rent: amount ...").
//
// Why the writes run inside the caller's transaction: every caller (HouseholdService, ExpenseService,
// ...) is already @Transactional. This class is @Transactional too, with the default setting, so it
// joins that same transaction. The history row and the real change are saved together, or not at
// all: an action that is rolled back leaves no history entry.
//
// Why the callers pass entities and this class decides the values: the "before" values must be
// captured BEFORE the entity is changed. The snapshot records below do that, and the diff code here
// compares them. Callers only take the snapshot and call the matching method.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityLogService {

    static final int DEFAULT_LIMIT = 30;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 100;
    // Matches the label column length in ActivityLog.
    private static final int LABEL_MAX_LENGTH = 200;
    // Field names whose stored values are user ids, and which the read endpoint turns into names.
    private static final Set<String> PERSON_FIELDS = Set.of("paidBy", "fromUser", "toUser");
    // How far a share may differ from its proportional value before it counts as changed (5 kurus).
    // Why: the proportional value is rounded to cents, so a tiny difference must not count as a change.
    private static final BigDecimal SHARE_TOLERANCE = new BigDecimal("0.05");

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final HouseholdAccessService accessService;
    private final ObjectMapper objectMapper;

    // ---------- Household and membership ----------

    public void householdCreated(Household household, User actor) {
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.HOUSEHOLD_CREATED)
                .actorUserId(actor.getId())
                .label(cut(household.getName()))
                .build());
    }

    public void householdRenamed(Household household, User actor, String oldName) {
        if (Objects.equals(oldName, household.getName())) {
            return;
        }
        List<FieldChange> changes = List.of(new FieldChange("name", oldName, household.getName(), null));
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.HOUSEHOLD_RENAMED)
                .actorUserId(actor.getId())
                .label(cut(household.getName()))
                .changes(toJson(changes))
                .build());
    }

    public void ownershipTransferred(Household household, User oldFounder, User newFounder) {
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.OWNERSHIP_TRANSFERRED)
                .actorUserId(oldFounder.getId())
                .subjectUserId(newFounder.getId())
                .label(cut(household.getName()))
                .build());
    }

    public void memberJoined(Household household, User member) {
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.MEMBER_JOINED)
                .actorUserId(member.getId())
                .subjectUserId(member.getId())
                .build());
    }

    // The member left on their own (leaving the household or deleting their account).
    public void memberLeft(Household household, User member) {
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.MEMBER_LEFT)
                .actorUserId(member.getId())
                .subjectUserId(member.getId())
                .build());
    }

    // An admin removed someone else.
    public void memberRemoved(Household household, User admin, User removed) {
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.MEMBER_REMOVED)
                .actorUserId(admin.getId())
                .subjectUserId(removed.getId())
                .build());
    }

    public void memberRoleChanged(Household household, User actor, User target,
                                  MembershipRole oldRole, MembershipRole newRole) {
        if (oldRole == newRole) {
            return;
        }
        List<FieldChange> changes = List.of(new FieldChange("role", oldRole.name(), newRole.name(), null));
        activityLogRepository.save(ActivityLog.builder()
                .household(household)
                .type(ActivityType.MEMBER_ROLE_CHANGED)
                .actorUserId(actor.getId())
                .subjectUserId(target.getId())
                .changes(toJson(changes))
                .build());
    }

    // ---------- Expenses ----------

    public void expenseAdded(Expense expense, User actor) {
        activityLogRepository.save(expenseRow(ActivityType.EXPENSE_ADDED, expense)
                .actorUserId(actor.getId())
                .build());
    }

    // The snapshot "before" must have been taken before the expense was changed (see ExpenseSnapshot.of).
    // Nothing is written when no field really changed. Returns true when a row was written, so the
    // caller knows it should send the edit notification too.
    public boolean expenseUpdated(Expense expense, User actor, ExpenseSnapshot before, List<ExpenseShare> sharesAfter) {
        List<FieldChange> changes = expenseChanges(before, ExpenseSnapshot.of(expense, sharesAfter));
        if (changes.isEmpty()) {
            return false;
        }
        activityLogRepository.save(expenseRow(ActivityType.EXPENSE_UPDATED, expense)
                .actorUserId(actor.getId())
                .changes(toJson(changes))
                .build());
        return true;
    }

    public void expenseDeleted(Expense expense, User actor) {
        activityLogRepository.save(expenseRow(ActivityType.EXPENSE_DELETED, expense)
                .actorUserId(actor.getId())
                .build());
    }

    // Created by the recurring job, so there is no logged-in user: the actor stays null.
    public void recurringExpenseGenerated(Expense copy) {
        activityLogRepository.save(expenseRow(ActivityType.RECURRING_EXPENSE_GENERATED, copy).build());
    }

    private ActivityLog.ActivityLogBuilder expenseRow(ActivityType type, Expense expense) {
        return ActivityLog.builder()
                .household(expense.getHousehold())
                .type(type)
                .subjectUserId(expense.getPaidBy().getId())
                .amount(expense.getAmount())
                .label(cut(expense.getDescription()))
                .expenseId(expense.getId());
    }

    // ---------- Payments ----------

    public void paymentAdded(Payment payment, User actor) {
        activityLogRepository.save(paymentRow(ActivityType.PAYMENT_ADDED, payment)
                .actorUserId(actor.getId())
                .build());
    }

    // Returns true when a row was written (same meaning as expenseUpdated).
    public boolean paymentUpdated(Payment payment, User actor, PaymentSnapshot before) {
        List<FieldChange> changes = paymentChanges(before, PaymentSnapshot.of(payment));
        if (changes.isEmpty()) {
            return false;
        }
        activityLogRepository.save(paymentRow(ActivityType.PAYMENT_UPDATED, payment)
                .actorUserId(actor.getId())
                .changes(toJson(changes))
                .build());
        return true;
    }

    public void paymentDeleted(Payment payment, User actor) {
        activityLogRepository.save(paymentRow(ActivityType.PAYMENT_DELETED, payment)
                .actorUserId(actor.getId())
                .build());
    }

    private ActivityLog.ActivityLogBuilder paymentRow(ActivityType type, Payment payment) {
        return ActivityLog.builder()
                .household(payment.getHousehold())
                .type(type)
                .subjectUserId(payment.getFromUser().getId())
                .otherUserId(payment.getToUser().getId())
                .amount(payment.getAmount())
                .paymentId(payment.getId());
    }

    // ---------- Cleanup ----------

    // Called by HouseholdService.purgeHousehold before the household row is deleted (foreign key).
    public void deleteForHousehold(Long householdId) {
        activityLogRepository.deleteByHouseholdId(householdId);
    }

    // ---------- Reading ----------

    // Newest first. limit is clamped to 1..100. beforeId (optional) returns only rows older than that id.
    @Transactional(readOnly = true)
    public List<ActivityResponse> list(Long householdId, int limit, Long beforeId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        int pageSize = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        PageRequest page = PageRequest.of(0, pageSize);
        List<ActivityLog> logs = beforeId == null
                ? activityLogRepository.findByHouseholdIdOrderByIdDesc(householdId, page)
                : activityLogRepository.findByHouseholdIdAndIdLessThanOrderByIdDesc(householdId, beforeId, page);

        // Pass 1: read each row's changes and collect every user id this page needs.
        Map<Long, List<FieldChange>> changesByLogId = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (ActivityLog log : logs) {
            List<FieldChange> changes = readChanges(log.getChanges());
            changesByLogId.put(log.getId(), changes);
            addUserId(userIds, log.getActorUserId());
            addUserId(userIds, log.getSubjectUserId());
            addUserId(userIds, log.getOtherUserId());
            for (FieldChange change : changes) {
                if (PERSON_FIELDS.contains(change.field())) {
                    addUserId(userIds, parseId(change.oldValue()));
                    addUserId(userIds, parseId(change.newValue()));
                }
                // A "share" change keeps its person's id in userId (its values are amounts, not ids).
                addUserId(userIds, change.userId());
            }
        }

        // Pass 2: one query for all names. An id that matches no user simply gets no name (null).
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), user.getName());
        }

        return logs.stream()
                .map(log -> toResponse(log, changesByLogId.get(log.getId()), names))
                .toList();
    }

    private ActivityResponse toResponse(ActivityLog log, List<FieldChange> changes, Map<Long, String> names) {
        List<FieldChangeResponse> changeResponses = changes.stream()
                .map(change -> resolveChange(change, names))
                .toList();
        return new ActivityResponse(
                log.getId(),
                log.getType(),
                log.getActorUserId(),
                nameOf(log.getActorUserId(), names),
                log.getSubjectUserId(),
                nameOf(log.getSubjectUserId(), names),
                log.getOtherUserId(),
                nameOf(log.getOtherUserId(), names),
                log.getAmount(),
                log.getLabel(),
                log.getExpenseId(),
                log.getPaymentId(),
                changeResponses,
                log.getCreatedAt());
    }

    private FieldChangeResponse resolveChange(FieldChange change, Map<Long, String> names) {
        if (change.userId() != null) {
            // A "share" change: the values stay amounts, and the person's current name is added.
            return new FieldChangeResponse(change.field(), change.oldValue(), change.newValue(),
                    change.userId(), nameOf(change.userId(), names));
        }
        if (!PERSON_FIELDS.contains(change.field())) {
            return new FieldChangeResponse(change.field(), change.oldValue(), change.newValue(), null, null);
        }
        return new FieldChangeResponse(
                change.field(),
                nameOf(parseId(change.oldValue()), names),
                nameOf(parseId(change.newValue()), names),
                null,
                null);
    }

    // ---------- Diff helpers (pure logic, no database writes) ----------

    // Compares the two snapshots field by field. Only fields that really changed become entries.
    private static List<FieldChange> expenseChanges(ExpenseSnapshot before, ExpenseSnapshot after) {
        List<FieldChange> changes = new ArrayList<>();
        addAmountChange(changes, "amount", before.amount(), after.amount());
        addChange(changes, "description", before.description(), after.description());
        addChange(changes, "category", enumText(before.category()), enumText(after.category()));
        addChange(changes, "expenseDate", dateText(before.expenseDate()), dateText(after.expenseDate()));
        addChange(changes, "paidBy", idText(before.paidByUserId()), idText(after.paidByUserId()));
        addChange(changes, "recurring", String.valueOf(before.recurring()), String.valueOf(after.recurring()));
        addChange(changes, "splitType", enumText(before.splitType()), enumText(after.splitType()));
        // The split: one "share" entry per person whose owed amount changed (see splitChanged for when it counts).
        if (splitChanged(before.amount(), after.amount(), before.owedByUserId(), after.owedByUserId())) {
            addShareChanges(changes, before.owedByUserId(), after.owedByUserId());
        }
        return changes;
    }

    // Decides whether the split really changed.
    // Why the total matters: when the total changes, every owed amount changes with it. If everyone
    // keeps the same proportion, the owed amounts moved only because of the total, and the "amount"
    // line already shows that. So a total change counts as a split change only when someone's owed
    // amount differs from their old share scaled to the new total. The tolerance absorbs cent rounding.
    private static boolean splitChanged(BigDecimal oldAmount, BigDecimal newAmount,
                                        Map<Long, BigDecimal> before, Map<Long, BigDecimal> after) {
        if (!before.keySet().equals(after.keySet())) {
            return true;
        }
        // Same total, or a zero old total (no proportion can be computed): compare the owed amounts directly.
        if (oldAmount.compareTo(newAmount) == 0 || oldAmount.signum() == 0) {
            return !sameShares(before, after);
        }
        for (Map.Entry<Long, BigDecimal> entry : before.entrySet()) {
            BigDecimal expected = entry.getValue().multiply(newAmount).divide(oldAmount, 2, RoundingMode.HALF_UP);
            BigDecimal actual = after.get(entry.getKey());
            if (actual.subtract(expected).abs().compareTo(SHARE_TOLERANCE) > 0) {
                return true;
            }
        }
        return false;
    }

    // One "share" entry per person whose owed amount differs, or who was added to or removed from the
    // split. Ordered by user id so the stored order is always the same.
    private static void addShareChanges(List<FieldChange> changes, Map<Long, BigDecimal> before,
                                        Map<Long, BigDecimal> after) {
        Set<Long> userIds = new TreeSet<>(before.keySet());
        userIds.addAll(after.keySet());
        for (Long userId : userIds) {
            BigDecimal oldOwed = before.get(userId);
            BigDecimal newOwed = after.get(userId);
            if (sameAmount(oldOwed, newOwed)) {
                continue;
            }
            changes.add(new FieldChange("share", moneyText(oldOwed), moneyText(newOwed), userId));
        }
    }

    private static List<FieldChange> paymentChanges(PaymentSnapshot before, PaymentSnapshot after) {
        List<FieldChange> changes = new ArrayList<>();
        addChange(changes, "fromUser", idText(before.fromUserId()), idText(after.fromUserId()));
        addChange(changes, "toUser", idText(before.toUserId()), idText(after.toUserId()));
        addAmountChange(changes, "amount", before.amount(), after.amount());
        addChange(changes, "note", before.note(), after.note());
        addChange(changes, "paidAt", dateText(before.paidAt()), dateText(after.paidAt()));
        return changes;
    }

    private static void addChange(List<FieldChange> changes, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new FieldChange(field, oldValue, newValue, null));
        }
    }

    // Money is compared with compareTo, so 30000 and 30000.00 count as the same amount.
    private static void addAmountChange(List<FieldChange> changes, String field, BigDecimal oldValue, BigDecimal newValue) {
        if (sameAmount(oldValue, newValue)) {
            return;
        }
        changes.add(new FieldChange(field, moneyText(oldValue), moneyText(newValue), null));
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    // Same people with the same owed amounts (compared with compareTo), in any order.
    private static boolean sameShares(Map<Long, BigDecimal> before, Map<Long, BigDecimal> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (Map.Entry<Long, BigDecimal> entry : before.entrySet()) {
            BigDecimal other = after.get(entry.getKey());
            if (other == null || entry.getValue().compareTo(other) != 0) {
                return false;
            }
        }
        return true;
    }

    // Snapshot of an expense's values, copied out of the entity so later changes do not affect it.
    public record ExpenseSnapshot(Long paidByUserId, BigDecimal amount, ExpenseCategory category,
                                  String description, boolean recurring, LocalDateTime expenseDate,
                                  SplitType splitType, Map<Long, BigDecimal> owedByUserId) {

        // Call this BEFORE changing the expense, and again after the change with the new shares.
        public static ExpenseSnapshot of(Expense expense, List<ExpenseShare> shares) {
            Map<Long, BigDecimal> owed = new HashMap<>();
            for (ExpenseShare share : shares) {
                owed.put(share.getUser().getId(), share.getOwedAmount());
            }
            return new ExpenseSnapshot(
                    expense.getPaidBy().getId(),
                    expense.getAmount(),
                    expense.getCategory(),
                    expense.getDescription(),
                    expense.isRecurring(),
                    expense.getExpenseDate(),
                    expense.getSplitType(),
                    owed);
        }
    }

    // Snapshot of a payment's values. paidAt never changes today, but it is listed for completeness.
    public record PaymentSnapshot(Long fromUserId, Long toUserId, BigDecimal amount, String note, Instant paidAt) {

        public static PaymentSnapshot of(Payment payment) {
            return new PaymentSnapshot(
                    payment.getFromUser().getId(),
                    payment.getToUser().getId(),
                    payment.getAmount(),
                    payment.getNote(),
                    payment.getPaidAt());
        }
    }

    // ---------- Small helpers ----------

    // Money as plain text with exactly two decimals, e.g. "30000.00". Used for the JSON values.
    private static String moneyText(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String idText(Long id) {
        return id == null ? null : id.toString();
    }

    private static String enumText(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String dateText(Object value) {
        return value == null ? null : value.toString();
    }

    // Labels are copied text, so they are cut to the column length instead of failing the action.
    private static String cut(String text) {
        if (text == null || text.length() <= LABEL_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, LABEL_MAX_LENGTH);
    }

    private static void addUserId(Set<Long> userIds, Long userId) {
        if (userId != null) {
            userIds.add(userId);
        }
    }

    private static Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nameOf(Long userId, Map<Long, String> names) {
        return userId == null ? null : names.get(userId);
    }

    private String toJson(List<FieldChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write activity changes", e);
        }
    }

    // A broken stored value must not break the whole history page, so it is logged and skipped.
    private List<FieldChange> readChanges(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FieldChange>>() {
            });
        } catch (JacksonException e) {
            log.error("Could not read activity changes: {}", json, e);
            return List.of();
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
