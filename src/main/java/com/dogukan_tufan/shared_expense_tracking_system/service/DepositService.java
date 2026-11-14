package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.DepositResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.DepositShareResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.DepositTransferResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.JoinDepositRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.MoveOutRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.UpdateDepositRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.UpdateDepositRequest.DepositShareInput;
import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositTransfer;
import com.dogukan_tufan.shared_expense_tracking_system.entity.HouseholdDeposit;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DepositShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DepositTransferRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.HouseholdDepositRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// The rent deposit ("depozito") the landlord holds, and how much of it each person owns.
//
// Why two kinds of data: HouseholdDeposit + DepositShare say WHO OWNS WHAT RIGHT NOW (the state).
// DepositTransfer records HOW IT CHANGED HANDS (the history). Only transfers feed the balances
// (BalanceService): when someone moves out, the people who take over their share owe them that money.
//
// Why the state is changed by these rules: the shares always add up to the total, so the
// "unassigned" number is 0 unless someone breaks that rule by hand. Moves keep the sum the same.
@Service
@RequiredArgsConstructor
@Transactional
public class DepositService {

    private static final BigDecimal MONEY_ZERO = new BigDecimal("0.00");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdDepositRepository depositRepository;
    private final DepositShareRepository shareRepository;
    private final DepositTransferRepository transferRepository;
    private final HouseholdAccessService accessService;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public DepositResponse get(Long householdId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        return buildResponse(householdId);
    }

    // Manual setup or correction. Any member may do it (like adding chores): every change is written to
    // the activity history, so the others can see who changed what. Deleting the record stays admin-only.
    // Balances are not touched here, because the deposit is not part of the expense balances.
    public DepositResponse update(Long householdId, UpdateDepositRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(caller.getId(), householdId);
        Household household = membership.getHousehold();

        BigDecimal total = request.totalAmount().setScale(2, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> newAmounts = new HashMap<>();
        BigDecimal sum = MONEY_ZERO;
        for (DepositShareInput input : request.shares()) {
            Long userId = input.userId();
            // A former member is allowed (their share may still need to be corrected). A stranger is not.
            if (!membershipRepository.existsByUserIdAndHouseholdId(userId, householdId)) {
                throw new BadRequestException("User " + userId + " is not a member of this household");
            }
            if (newAmounts.containsKey(userId)) {
                throw new BadRequestException("User " + userId + " is listed more than once in shares");
            }
            BigDecimal amount = input.amount().setScale(2, RoundingMode.HALF_UP);
            newAmounts.put(userId, amount);
            sum = sum.add(amount);
        }
        if (sum.compareTo(total) != 0) {
            throw new BadRequestException("Shares must add up to the total deposit");
        }

        // The "before" snapshot is taken first, because the rows are changed below.
        Optional<HouseholdDeposit> existing = depositRepository.findByHouseholdId(householdId);
        List<DepositShare> existingShares = shareRepository.findByHouseholdId(householdId);
        ActivityLogService.DepositSnapshot before = ActivityLogService.DepositSnapshot.of(
                existing.map(HouseholdDeposit::getTotalAmount).orElse(null), existingShares);

        if (existing.isPresent()) {
            existing.get().changeTotal(total);
        } else {
            depositRepository.save(new HouseholdDeposit(household, total));
        }

        // Rows of people who are not in the request become 0 (rows are kept, so the unique pair stays simple).
        Map<Long, BigDecimal> afterByUser = new HashMap<>();
        for (DepositShare row : existingShares) {
            Long userId = row.getUser().getId();
            BigDecimal amount = newAmounts.getOrDefault(userId, MONEY_ZERO);
            row.setAmount(amount);
            afterByUser.put(userId, amount);
        }
        for (Map.Entry<Long, BigDecimal> entry : newAmounts.entrySet()) {
            if (!afterByUser.containsKey(entry.getKey())) {
                User user = findUser(entry.getKey());
                shareRepository.save(new DepositShare(household, user, entry.getValue()));
                afterByUser.put(entry.getKey(), entry.getValue());
            }
        }

        activityLogService.depositUpdated(household, caller, before,
                new ActivityLogService.DepositSnapshot(total, afterByUser));
        return buildResponse(householdId);
    }

    // Removes the deposit record and all shares. Refused while transfers exist: undoing them one by one
    // (DELETE /transfers/{id}) is the way to keep the balances consistent.
    public void delete(Long householdId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(caller.getId(), householdId);
        accessService.requireAdmin(membership);

        HouseholdDeposit deposit = requireDeposit(householdId);
        if (transferRepository.countByHouseholdId(householdId) > 0) {
            throw new ConflictException("Delete the deposit transfers first");
        }
        shareRepository.deleteByHouseholdId(householdId);
        depositRepository.delete(deposit);
    }

    // The leaver's whole share is handed over, split equally between the other active members.
    // The cents that do not divide evenly go one by one to the first receivers (ordered by user id).
    public DepositResponse moveOut(Long householdId, MoveOutRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Long leaverId = request.userId();
        requireSelfOrAdmin(caller, householdId, leaverId);

        HouseholdDeposit deposit = requireDeposit(householdId);
        Household household = deposit.getHousehold();
        User leaver = findUser(leaverId);

        Optional<DepositShare> leaverShare = shareRepository.findByHouseholdIdAndUserId(householdId, leaverId);
        BigDecimal share = leaverShare.map(DepositShare::getAmount).orElse(MONEY_ZERO);
        if (share.signum() == 0) {
            throw new BadRequestException("This user has no deposit share to hand over");
        }

        // Active members other than the leaver, in a stable order (user id).
        List<User> receivers = membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(Membership::getUser)
                .filter(user -> !user.getId().equals(leaverId))
                .sorted(Comparator.comparing(User::getId))
                .toList();
        if (receivers.isEmpty()) {
            throw new BadRequestException("Nobody is left to take over the deposit share");
        }

        List<BigDecimal> parts = splitEvenly(share, receivers.size());
        for (int i = 0; i < receivers.size(); i++) {
            BigDecimal part = parts.get(i);
            if (part.signum() == 0) {
                continue; // only possible for a share of a few cents; a 0 transfer would be rejected
            }
            User receiver = receivers.get(i);
            transferRepository.save(new DepositTransfer(household, leaver, receiver, part, caller));
            DepositShare receiverShare = shareRowFor(household, receiver);
            receiverShare.setAmount(receiverShare.getAmount().add(part));
        }
        leaverShare.get().setAmount(MONEY_ZERO);

        activityLogService.depositTransferred(household, caller, leaver, share, "MOVE_OUT");
        return buildResponse(householdId);
    }

    // A newcomer buys into the deposit. They must be an active member with no share yet.
    // The amount is taken from the current owners in proportion to their shares.
    public DepositResponse join(Long householdId, JoinDepositRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Long newcomerId = request.userId();
        requireSelfOrAdmin(caller, householdId, newcomerId);

        HouseholdDeposit deposit = requireDeposit(householdId);
        Household household = deposit.getHousehold();

        Membership newcomerMembership = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(newcomerId, householdId)
                .orElseThrow(() -> new BadRequestException("User " + newcomerId + " is not an active member of this household"));
        User newcomer = newcomerMembership.getUser();

        Optional<DepositShare> newcomerShare = shareRepository.findByHouseholdIdAndUserId(householdId, newcomerId);
        if (newcomerShare.isPresent() && newcomerShare.get().getAmount().signum() != 0) {
            throw new BadRequestException("This user already has a deposit share");
        }

        // Current owners (amount > 0), biggest share first; the id breaks ties so the order is stable.
        List<DepositShare> owners = shareRepository.findByHouseholdId(householdId).stream()
                .filter(share -> !share.getUser().getId().equals(newcomerId))
                .filter(share -> share.getAmount().signum() > 0)
                .sorted(Comparator.comparing(DepositShare::getAmount).reversed()
                        .thenComparing(share -> share.getUser().getId()))
                .toList();
        BigDecimal ownersTotal = owners.stream().map(DepositShare::getAmount).reduce(MONEY_ZERO, BigDecimal::add);

        // Default: an equal split between the current owners and the newcomer.
        BigDecimal amount = request.amount() != null
                ? request.amount().setScale(2, RoundingMode.HALF_UP)
                : deposit.getTotalAmount().divide(BigDecimal.valueOf(owners.size() + 1L), 2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }
        if (amount.compareTo(ownersTotal) > 0) {
            throw new BadRequestException("Amount cannot be more than the current owners hold (" + ownersTotal.toPlainString() + ")");
        }

        // Each owner gives up a part proportional to their share. Rounding to cents can leave a cent or
        // two over or under; that difference goes to the largest owner, so the parts add up exactly.
        List<BigDecimal> parts = new ArrayList<>();
        BigDecimal partsSum = MONEY_ZERO;
        for (DepositShare owner : owners) {
            BigDecimal part = amount.multiply(owner.getAmount()).divide(ownersTotal, 2, RoundingMode.HALF_UP);
            parts.add(part);
            partsSum = partsSum.add(part);
        }
        parts.set(0, parts.get(0).add(amount.subtract(partsSum)));

        for (int i = 0; i < owners.size(); i++) {
            BigDecimal part = parts.get(i);
            DepositShare owner = owners.get(i);
            if (part.signum() == 0) {
                continue;
            }
            owner.setAmount(owner.getAmount().subtract(part));
            transferRepository.save(new DepositTransfer(household, owner.getUser(), newcomer, part, caller));
        }
        DepositShare newShare = newcomerShare.orElseGet(() -> shareRepository.save(new DepositShare(household, newcomer, MONEY_ZERO)));
        newShare.setAmount(amount);

        activityLogService.depositTransferred(household, caller, newcomer, amount, "JOIN");
        return buildResponse(householdId);
    }

    // Undoes one transfer: the giver gets the amount back, the taker gives it up. Admin only.
    public DepositResponse deleteTransfer(Long householdId, Long transferId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(caller.getId(), householdId);
        accessService.requireAdmin(membership);

        DepositTransfer transfer = transferRepository.findByIdAndHouseholdId(transferId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit transfer not found: " + transferId));
        Household household = transfer.getHousehold();
        BigDecimal amount = transfer.getAmount();

        // The taker must still hold at least that much, otherwise undoing it would make a share negative.
        Optional<DepositShare> takerShare = shareRepository.findByHouseholdIdAndUserId(householdId, transfer.getToUser().getId());
        BigDecimal takerAmount = takerShare.map(DepositShare::getAmount).orElse(MONEY_ZERO);
        if (takerAmount.compareTo(amount) < 0) {
            throw new BadRequestException("The receiver no longer holds that much of the deposit, so this transfer cannot be undone");
        }

        DepositShare giverShare = shareRowFor(household, transfer.getFromUser());
        giverShare.setAmount(giverShare.getAmount().add(amount));
        takerShare.get().setAmount(takerAmount.subtract(amount));

        activityLogService.depositTransferDeleted(transfer, caller);
        transferRepository.delete(transfer);
        return buildResponse(householdId);
    }

    // The user may act for themself (also after leaving, so a former member can hand over their share).
    // Anyone else needs to be an active admin.
    private void requireSelfOrAdmin(User caller, Long householdId, Long targetUserId) {
        if (caller.getId().equals(targetUserId)) {
            if (!membershipRepository.existsByUserIdAndHouseholdId(caller.getId(), householdId)) {
                throw new ResourceNotFoundException("Household not found: " + householdId);
            }
            return;
        }
        accessService.requireAdmin(accessService.requireActiveMembership(caller.getId(), householdId));
    }

    // Splits an amount into equal parts in cents. Parts are rounded down; the leftover cents
    // (fewer than the number of parts) go one each to the first parts, so the sum is exact.
    private List<BigDecimal> splitEvenly(BigDecimal total, int count) {
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        int leftoverCents = total.subtract(base.multiply(BigDecimal.valueOf(count)))
                .movePointRight(2).intValueExact();
        List<BigDecimal> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parts.add(i < leftoverCents ? base.add(CENT) : base);
        }
        return parts;
    }

    // Returns the person's share row, creating it with 0 if they have none yet.
    private DepositShare shareRowFor(Household household, User user) {
        return shareRepository.findByHouseholdIdAndUserId(household.getId(), user.getId())
                .orElseGet(() -> shareRepository.save(new DepositShare(household, user, MONEY_ZERO)));
    }

    // Builds the full picture. Shares: current members (even at 0) and former members who still own something.
    private DepositResponse buildResponse(Long householdId) {
        Optional<HouseholdDeposit> deposit = depositRepository.findByHouseholdId(householdId);
        if (deposit.isEmpty()) {
            return new DepositResponse(null, List.of(), null, List.of());
        }

        Map<Long, BigDecimal> amounts = new HashMap<>();
        for (DepositShare row : shareRepository.findByHouseholdId(householdId)) {
            amounts.put(row.getUser().getId(), row.getAmount());
        }
        BigDecimal sum = amounts.values().stream().reduce(MONEY_ZERO, BigDecimal::add);

        // One entry per person (a user who left and rejoined has two membership rows).
        Map<Long, User> users = new HashMap<>();
        Set<Long> activeIds = new HashSet<>();
        for (Membership membership : membershipRepository.findByHouseholdId(householdId)) {
            Long userId = membership.getUser().getId();
            users.putIfAbsent(userId, membership.getUser());
            if (membership.getLeftAt() == null) {
                activeIds.add(userId);
            }
        }

        List<DepositShareResponse> shares = users.entrySet().stream()
                .filter(entry -> activeIds.contains(entry.getKey()) || amountOf(amounts, entry.getKey()).signum() != 0)
                .map(entry -> new DepositShareResponse(
                        entry.getKey(),
                        entry.getValue().getName(),
                        amountOf(amounts, entry.getKey()),
                        activeIds.contains(entry.getKey())))
                .sorted(Comparator.comparing(DepositShareResponse::amount).reversed()
                        .thenComparing(DepositShareResponse::name))
                .toList();

        List<DepositTransferResponse> transfers = transferRepository
                .findByHouseholdIdOrderByCreatedAtDescIdDesc(householdId).stream()
                .map(DepositTransferResponse::from)
                .toList();

        BigDecimal unassigned = deposit.get().getTotalAmount().subtract(sum);
        return new DepositResponse(deposit.get().getTotalAmount(), shares, unassigned, transfers);
    }

    private BigDecimal amountOf(Map<Long, BigDecimal> amounts, Long userId) {
        return amounts.getOrDefault(userId, MONEY_ZERO);
    }

    private HouseholdDeposit requireDeposit(Long householdId) {
        return depositRepository.findByHouseholdId(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("No deposit recorded for this household"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
