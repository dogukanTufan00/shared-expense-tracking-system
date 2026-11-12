package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ShareInput;
import com.dogukan_tufan.shared_expense_tracking_system.entity.CategorySplitDefault;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.SplitType;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.AttachmentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.CategorySplitDefaultRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final MembershipRepository membershipRepository;
    private final CategorySplitDefaultRepository categorySplitDefaultRepository;
    private final HouseholdAccessService accessService;
    private final ShareValidator shareValidator;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;

    public ExpenseResponse create(Long householdId, ExpenseRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Membership payerMembership = requireActivePayer(householdId, request.paidByUserId());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        // Work out who pays what BEFORE saving anything, so a bad split throws and nothing is written.
        SplitPlan plan = planForCreate(householdId, request.category(), request.splitType(), request.shares(), amount);

        Expense expense = new Expense(
                requesterMembership.getHousehold(),
                payerMembership.getUser(),
                amount,
                request.category(),
                request.description(),
                request.recurring(),
                request.expenseDate());
        expense.setSplitType(plan.splitType());
        expenseRepository.save(expense);

        List<ExpenseShare> shares = recalculateShares(expense, plan.shares());

        return toResponse(expense, shares);
    }

    public List<ExpenseResponse> list(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        // One query for all shares of the household, grouped by expense id, instead of one query per expense.
        Map<Long, List<ExpenseShare>> sharesByExpenseId = expenseShareRepository.findByExpenseHouseholdId(householdId)
                .stream()
                .collect(Collectors.groupingBy(share -> share.getExpense().getId()));

        return expenseRepository.findByHouseholdId(householdId).stream()
                .map(expense -> toResponse(expense, sharesByExpenseId.getOrDefault(expense.getId(), List.of())))
                .toList();
    }

    public ExpenseResponse getById(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        return toResponse(expense, expenseShareRepository.findByExpenseId(expense.getId()));
    }

    public ExpenseResponse update(Long householdId, Long expenseId, ExpenseRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        requirePayerOrAdmin(expense, currentUser, requesterMembership);

        Membership payerMembership = requireActivePayer(householdId, request.paidByUserId());
        BigDecimal newAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        // Planned BEFORE the expense is changed: the plan compares the new amount with the old one
        // and reads the old shares, so the expense must still hold its old values at this point.
        SplitPlan plan = planForUpdate(householdId, expense, request, newAmount);

        expense.setPaidBy(payerMembership.getUser());
        expense.setAmount(newAmount);
        expense.setCategory(request.category());
        expense.setDescription(request.description());
        expense.setRecurring(request.recurring());
        expense.setExpenseDate(request.expenseDate());
        expense.setSplitType(plan.splitType());

        List<ExpenseShare> shares = recalculateShares(expense, plan.shares());

        return toResponse(expense, shares);
    }

    public void delete(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        requirePayerOrAdmin(expense, currentUser, requesterMembership);

        // Attachments point at this expense too, so their rows and files go first. Then the shares.
        attachmentService.deleteForExpense(expense.getId());
        expenseShareRepository.deleteByExpenseId(expense.getId());
        expenseRepository.delete(expense);
    }

    // ---------- Deciding the split (no database writes in this section) ----------

    // The result of planning: which split type applies and the exact share of each person.
    private record SplitPlan(SplitType splitType, List<ShareDraft> shares) {
    }

    // One person's share before it is saved. percent is only set for PERCENT splits.
    private record ShareDraft(User user, BigDecimal percent, BigDecimal owedAmount) {
    }

    // Create: explicit shares are used as sent. Without shares, the category default is tried first.
    private SplitPlan planForCreate(Long householdId, ExpenseCategory category, SplitType requested,
                                    List<ShareInput> shares, BigDecimal amount) {
        SplitType type = requested == null ? SplitType.EQUAL : requested;

        if (!hasShares(shares)) {
            if (type != SplitType.EQUAL) {
                throw new BadRequestException("shares are required for " + type + " split");
            }
            return defaultPlan(householdId, category, amount);
        }
        return planFromShares(householdId, type, shares, amount);
    }

    // Without shares on create: use the saved category default ONLY if its people are exactly the
    // current active members. Why: an old default must not silently apply to a different group of
    // people after someone moved in or out. If the group changed, we fall back to an equal split.
    private SplitPlan defaultPlan(Long householdId, ExpenseCategory category, BigDecimal amount) {
        List<User> activeMembers = activeMemberUsers(householdId);
        List<CategorySplitDefault> defaults =
                categorySplitDefaultRepository.findByHouseholdIdAndCategory(householdId, category);

        if (!defaults.isEmpty() && sameUsers(defaults, activeMembers)) {
            List<ShareInput> inputs = defaults.stream()
                    .map(row -> new ShareInput(row.getUser().getId(), row.getPercent()))
                    .toList();
            return planFromShares(householdId, SplitType.PERCENT, inputs, amount);
        }
        return new SplitPlan(SplitType.EQUAL, equalShares(activeMembers, amount));
    }

    // Update: explicit shares replace the split. Without shares, the expense keeps its stored split
    // (to change the split type, send shares). Old shares are reused even if someone has left since;
    // the shares of an old bill stay with the people who originally shared it.
    private SplitPlan planForUpdate(Long householdId, Expense expense, ExpenseRequest request, BigDecimal newAmount) {
        SplitType requested = request.splitType();

        if (hasShares(request.shares())) {
            return planFromShares(householdId, requested == null ? SplitType.EQUAL : requested,
                    request.shares(), newAmount);
        }
        if (requested == SplitType.PERCENT || requested == SplitType.AMOUNT) {
            throw new BadRequestException("shares are required for " + requested + " split");
        }

        List<ExpenseShare> existing = expenseShareRepository.findByExpenseId(expense.getId());
        List<User> users = existing.stream().map(ExpenseShare::getUser).toList();
        SplitType stored = expense.getSplitType();

        // No split type sent = "keep the split as it is". An explicit EQUAL means "split it equally now".
        if (requested == null && stored == SplitType.PERCENT) {
            // Same percentages, applied to the new amount.
            List<BigDecimal> percents = existing.stream().map(ExpenseShare::getPercent).toList();
            return new SplitPlan(SplitType.PERCENT, percentShares(users, percents, newAmount));
        }
        if (requested == null && stored == SplitType.AMOUNT) {
            // Fixed amounts only fit the old total. If the total changed, the client must say how to split it.
            if (newAmount.compareTo(expense.getAmount()) != 0) {
                throw new BadRequestException("shares are required when changing the amount of an AMOUNT split");
            }
            List<BigDecimal> owed = existing.stream().map(ExpenseShare::getOwedAmount).toList();
            return new SplitPlan(SplitType.AMOUNT, amountShares(users, owed));
        }

        // EQUAL: split equally among the original participants. Expenses created before shares
        // existed have none yet, so they fall back to the current members.
        if (users.isEmpty()) {
            users = activeMemberUsers(householdId);
        }
        return new SplitPlan(SplitType.EQUAL, equalShares(users, newAmount));
    }

    // Explicit shares from the client: validate them with the shared rules, then compute the amounts.
    private SplitPlan planFromShares(Long householdId, SplitType type, List<ShareInput> shares, BigDecimal amount) {
        List<User> users = shareValidator.requireValidParticipants(householdId, shares);

        return switch (type) {
            case EQUAL -> new SplitPlan(SplitType.EQUAL, equalShares(users, amount));
            case PERCENT -> {
                shareValidator.requirePercentages(shares);
                yield new SplitPlan(SplitType.PERCENT, percentShares(users, valuesOf(shares), amount));
            }
            case AMOUNT -> {
                shareValidator.requireAmounts(shares, amount);
                yield new SplitPlan(SplitType.AMOUNT, amountShares(users, valuesOf(shares)));
            }
        };
    }

    // ---------- Computing the amounts ----------

    private List<ShareDraft> equalShares(List<User> users, BigDecimal amount) {
        List<BigDecimal> amounts = splitEqually(amount, users.size());
        List<ShareDraft> drafts = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            drafts.add(new ShareDraft(users.get(i), null, amounts.get(i)));
        }
        return drafts;
    }

    // Plain amount/count division loses or gains cents to rounding (e.g. 150.50 / 3 = 50.1666...).
    // This splits in whole cents instead, so the shares always sum back to exactly `amount`; any
    // leftover cent goes to the first few shares, one each, rather than being dropped or duplicated.
    private List<BigDecimal> splitEqually(BigDecimal amount, int count) {
        long totalCents = amount.movePointRight(2).longValueExact();
        long baseCents = totalCents / count;
        long remainderCents = totalCents % count;

        List<BigDecimal> shares = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long cents = baseCents + (i < remainderCents ? 1 : 0);
            shares.add(BigDecimal.valueOf(cents, 2));
        }
        return shares;
    }

    // PERCENT split in whole cents. Each person first gets floor(total cents * percent / 100).
    // Those floors can add up to a few cents less than the total (the cents lost to rounding down);
    // those leftover cents go to the first people in the list, one each, so the sum is exact.
    private List<ShareDraft> percentShares(List<User> users, List<BigDecimal> percents, BigDecimal amount) {
        long totalCents = amount.movePointRight(2).longValueExact();

        List<Long> flooredCents = new ArrayList<>();
        long assignedCents = 0;
        for (BigDecimal percent : percents) {
            long cents = BigDecimal.valueOf(totalCents)
                    .multiply(percent)
                    .divide(HUNDRED, 0, RoundingMode.FLOOR)
                    .longValueExact();
            flooredCents.add(cents);
            assignedCents += cents;
        }
        long leftoverCents = totalCents - assignedCents;

        List<ShareDraft> drafts = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            long extra = i < leftoverCents ? 1 : 0;
            BigDecimal owed = BigDecimal.valueOf(flooredCents.get(i) + extra, 2);
            drafts.add(new ShareDraft(users.get(i), percents.get(i).setScale(2), owed));
        }
        return drafts;
    }

    // AMOUNT split: the values were already checked to add up to the expense amount, so use them as they are.
    private List<ShareDraft> amountShares(List<User> users, List<BigDecimal> amounts) {
        List<ShareDraft> drafts = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            drafts.add(new ShareDraft(users.get(i), null, amounts.get(i).setScale(2)));
        }
        return drafts;
    }

    // ---------- Saving ----------

    // Rebuilds this expense's ExpenseShare rows from scratch from the planned shares.
    // Returns the saved rows so the response can include them without another query.
    private List<ExpenseShare> recalculateShares(Expense expense, List<ShareDraft> drafts) {
        expenseShareRepository.deleteByExpenseId(expense.getId());

        List<ExpenseShare> saved = new ArrayList<>();
        for (ShareDraft draft : drafts) {
            ExpenseShare share = new ExpenseShare(expense, draft.user(), draft.owedAmount(), draft.percent());
            saved.add(expenseShareRepository.save(share));
        }
        return saved;
    }

    // ---------- Small helpers ----------

    private boolean hasShares(List<ShareInput> shares) {
        return shares != null && !shares.isEmpty();
    }

    private List<BigDecimal> valuesOf(List<ShareInput> shares) {
        return shares.stream().map(ShareInput::value).toList();
    }

    // True when the saved default covers exactly the same set of users as the active members.
    private boolean sameUsers(List<CategorySplitDefault> defaults, List<User> members) {
        Set<Long> defaultUserIds = defaults.stream()
                .map(row -> row.getUser().getId())
                .collect(Collectors.toSet());
        Set<Long> memberUserIds = members.stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        return defaultUserIds.equals(memberUserIds);
    }

    private List<User> activeMemberUsers(Long householdId) {
        return membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(Membership::getUser)
                .toList();
    }

    // Builds the response including the attachment count (one count query per expense; lists are small).
    private ExpenseResponse toResponse(Expense expense, List<ExpenseShare> shares) {
        int attachmentCount = (int) attachmentRepository.countByExpenseId(expense.getId());
        return ExpenseResponse.from(expense, shares, attachmentCount);
    }

    private Expense findExpense(Long householdId, Long expenseId) {
        return expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
    }

    private Membership requireActivePayer(Long householdId, Long userId) {
        return membershipRepository.findByUserIdAndHouseholdIdAndLeftAtIsNull(userId, householdId)
                .orElseThrow(() -> new BadRequestException(
                        "paidByUserId " + userId + " is not an active member of this household"));
    }

    private void requirePayerOrAdmin(Expense expense, User currentUser, Membership requesterMembership) {
        boolean isPayer = expense.getPaidBy().getId().equals(currentUser.getId());
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        if (!isPayer && !isAdmin) {
            throw new ForbiddenActionException("Only the payer or a household admin can modify this expense");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
