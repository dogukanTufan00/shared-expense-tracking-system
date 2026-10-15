package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.balance.BalanceResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        return membersByUserId.values().stream()
                .map(m -> {
                    Long userId = m.getUser().getId();
                    // paid - owed: what they fronted beyond their own fair share across all expenses.
                    // + sent - received: payments already settle some of that, in either direction.
                    BigDecimal net = amountFor(paid, userId)
                            .subtract(amountFor(owed, userId))
                            .add(amountFor(sent, userId))
                            .subtract(amountFor(received, userId));
                    return new BalanceResponse(userId, m.getUser().getName(), net);
                })
                // Former members who are fully settled are just noise, so they are hidden.
                .filter(b -> b.netBalance().signum() != 0 || activeUserIds.contains(b.userId()))
                .toList();
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
