package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.balance.BalanceResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.balance.SettlementResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositTransfer;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DepositTransferRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// CLAUDE.md section 6: "Balances are computed from Expense/ExpenseShare/Payment, not stored."
// Nothing here is persisted -- every call recomputes from scratch. This gives each member a
// single net number rather than a full pairwise "who owes whom" ledger: simpler to build, test
// and explain, at the cost of not telling you which specific person to pay. Good enough for this
// checkpoint; pairwise debt simplification is a v2 idea if there's time left.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final PaymentRepository paymentRepository;
    private final DepositTransferRepository depositTransferRepository;
    private final HouseholdAccessService accessService;

    public List<BalanceResponse> getBalances(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        // All members, not just active ones: someone who moved out can still owe (or be owed) money,
        // and their debt must not silently disappear from the list. A user who left and rejoined
        // has two Membership rows, so we keep one entry per user.
        List<Membership> allMemberships = membershipRepository.findByHouseholdId(householdId);
        Map<Long, Membership> membersByUserId = allMemberships.stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), m -> m, (first, second) -> first));
        Set<Long> activeUserIds = allMemberships.stream()
                .filter(m -> m.getLeftAt() == null)
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paid = sumBy(
                expenseRepository.findByHouseholdId(householdId), e -> e.getPaidBy().getId(), Expense::getAmount);
        Map<Long, BigDecimal> owed = sumBy(
                expenseShareRepository.findByExpenseHouseholdId(householdId),
                s -> s.getUser().getId(), ExpenseShare::getOwedAmount);

        List<Payment> payments = paymentRepository.findByHouseholdId(householdId);
        Map<Long, BigDecimal> sent = sumBy(payments, p -> p.getFromUser().getId(), Payment::getAmount);
        Map<Long, BigDecimal> received = sumBy(payments, p -> p.getToUser().getId(), Payment::getAmount);

        // Deposit transfers: the giver (fromUser) is owed the amount, the taker (toUser) owes it.
        // So they count like a payment in the opposite direction: + given away, - taken over.
        List<DepositTransfer> depositTransfers = depositTransferRepository.findByHouseholdId(householdId);
        Map<Long, BigDecimal> givenAway = sumBy(depositTransfers, t -> t.getFromUser().getId(), DepositTransfer::getAmount);
        Map<Long, BigDecimal> takenOver = sumBy(depositTransfers, t -> t.getToUser().getId(), DepositTransfer::getAmount);

        return membersByUserId.values().stream()
                .map(m -> {
                    Long userId = m.getUser().getId();
                    // paid - owed: what they fronted beyond their own fair share across all expenses.
                    // + sent - received: payments already settle some of that, in either direction.
                    // + given away - taken over: deposit share handed over (see DepositTransfer).
                    BigDecimal net = amountFor(paid, userId)
                            .subtract(amountFor(owed, userId))
                            .add(amountFor(sent, userId))
                            .subtract(amountFor(received, userId))
                            .add(amountFor(givenAway, userId))
                            .subtract(amountFor(takenOver, userId));
                    return new BalanceResponse(userId, m.getUser().getName(), net);
                })
                // Former members who are fully settled are just noise, so they are hidden.
                .filter(b -> b.netBalance().signum() != 0 || activeUserIds.contains(b.userId()))
                .toList();
    }

    // "Who pays whom", as DIRECT debts: everyone pays the person they actually owe.
    // For every pair of people we add up what one owes the other, then net the two directions:
    // - an expense: each person with a share owes that share to the payer;
    // - a deposit transfer: the taker owes the giver (see DepositTransfer);
    // - a payment: lowers what the sender owes the receiver.
    // Why not the shortest list of payments ("simplified debts"): it could tell Ali to pay Dogukan for
    // an expense Bulent paid, which confused users and did not fit the per-person share message or the
    // rule that only the sender or the receiver may mark a payment as paid. Each person's balance
    // (getBalances) is the same either way; only this list changes. Nothing is stored, as with balances.
    public List<SettlementResponse> getSettlements(Long householdId, String currentUserEmail) {
        // getBalances also checks that the current user is an active member of this household.
        Map<Long, String> names = new HashMap<>();
        for (BalanceResponse balance : getBalances(householdId, currentUserEmail)) {
            names.put(balance.userId(), balance.name());
        }

        // owes.get(a).get(b) = how much a owes b, before netting the two directions.
        Map<Long, Map<Long, BigDecimal>> owes = new HashMap<>();
        for (ExpenseShare share : expenseShareRepository.findByExpenseHouseholdId(householdId)) {
            User payer = share.getExpense().getPaidBy();
            addDebt(owes, share.getUser(), payer, share.getOwedAmount(), names);
        }
        for (DepositTransfer transfer : depositTransferRepository.findByHouseholdId(householdId)) {
            addDebt(owes, transfer.getToUser(), transfer.getFromUser(), transfer.getAmount(), names);
        }
        for (Payment payment : paymentRepository.findByHouseholdId(householdId)) {
            addDebt(owes, payment.getFromUser(), payment.getToUser(), payment.getAmount().negate(), names);
        }

        List<SettlementResponse> settlements = new ArrayList<>();
        Set<String> donePairs = new HashSet<>();
        for (Long from : owes.keySet()) {
            for (Long to : owes.get(from).keySet()) {
                // Each pair is looked at once, from the side that ends up owing.
                if (!donePairs.add(Math.min(from, to) + "-" + Math.max(from, to))) {
                    continue;
                }
                BigDecimal net = debt(owes, from, to).subtract(debt(owes, to, from));
                if (net.signum() == 0) {
                    continue;
                }
                Long debtor = net.signum() > 0 ? from : to;
                Long creditor = net.signum() > 0 ? to : from;
                String toIban = userRepository.findById(creditor).map(User::getIban).orElse(null);
                settlements.add(new SettlementResponse(debtor, names.get(debtor), creditor, names.get(creditor),
                        toIban, net.abs()));
            }
        }
        // Biggest amount first, then by names, so the list is always in the same order.
        settlements.sort(Comparator.comparing(SettlementResponse::amount).reversed()
                .thenComparing(SettlementResponse::fromName)
                .thenComparing(SettlementResponse::toName));
        return settlements;
    }

    // Adds "debtor owes creditor amount" (a negative amount lowers it). A person never owes themself.
    private void addDebt(Map<Long, Map<Long, BigDecimal>> owes, User debtor, User creditor, BigDecimal amount,
                         Map<Long, String> names) {
        if (debtor.getId().equals(creditor.getId())) {
            return;
        }
        names.putIfAbsent(debtor.getId(), debtor.getName());
        names.putIfAbsent(creditor.getId(), creditor.getName());
        owes.computeIfAbsent(debtor.getId(), id -> new HashMap<>())
                .merge(creditor.getId(), amount, BigDecimal::add);
    }

    private BigDecimal debt(Map<Long, Map<Long, BigDecimal>> owes, Long from, Long to) {
        return owes.getOrDefault(from, Map.of()).getOrDefault(to, BigDecimal.ZERO);
    }

    private <T> Map<Long, BigDecimal> sumBy(List<T> items, Function<T, Long> keyFn, Function<T, BigDecimal> amountFn) {
        return items.stream().collect(Collectors.groupingBy(
                keyFn, Collectors.reducing(BigDecimal.ZERO, amountFn, BigDecimal::add)));
    }

    private BigDecimal amountFor(Map<Long, BigDecimal> amounts, Long userId) {
        return amounts.getOrDefault(userId, BigDecimal.ZERO);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
