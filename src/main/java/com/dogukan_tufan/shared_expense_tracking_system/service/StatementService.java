package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.CategoryStat;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.DominantPayer;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.MemberStat;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.MonthMemberPaid;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.MonthStat;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.StatementExpense;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.StatementPayment;
import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse.StatementShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Builds the period statement for a household. Read-only: it only reads expenses, shares and
// payments and adds them up. Each private method does one small job so the flow stays easy to explain.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatementService {

    // A statement covers at most one leap year, so the queries and the response stay a sane size.
    private static final int MAX_PERIOD_DAYS = 366;
    private static final int TREND_MONTHS = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Why 50%: that is "more than everyone else together". Half or more of the money is one person's.
    private static final BigDecimal DOMINANT_THRESHOLD = BigDecimal.valueOf(50);
    // Why 2: with only one person sharing costs there is nobody to compare against.
    private static final int DOMINANT_MIN_SHARERS = 2;
    private static final BigDecimal MONEY_ZERO = new BigDecimal("0.00");
    private static final BigDecimal PERCENT_ZERO = new BigDecimal("0.0");

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;

    public StatementResponse getStatement(Long householdId, LocalDate from, LocalDate to, String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserEmail));
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        Period period = resolvePeriod(from, to);

        // Expenses: the whole first day up to (not including) the day after the last day.
        List<Expense> expenses = expenseRepository.findByHouseholdIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThan(
                householdId, period.from().atStartOfDay(), period.to().plusDays(1).atStartOfDay());
        List<ExpenseShare> shares = findSharesOf(expenses);
        // Payments are stored as Instants, so the Turkish calendar dates are turned into instants first.
        List<Payment> payments = paymentRepository.findByHouseholdIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                householdId, startInstant(period.from()), startInstant(period.to().plusDays(1)));

        // The trend uses its own window (see buildTrend), so it is loaded separately from the period.
        LocalDate trendFrom = firstOfMonth(period.to()).minusMonths(TREND_MONTHS - 1);
        LocalDate trendToExclusive = firstOfMonth(period.to()).plusMonths(1);
        List<Expense> trendExpenses = expenseRepository.findByHouseholdIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThan(
                householdId, trendFrom.atStartOfDay(), trendToExclusive.atStartOfDay());

        // Everyone we may print a name for: active members plus anyone seen in the data.
        List<Membership> activeMemberships = membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId);
        Set<Long> activeIds = activeMemberships.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        Map<Long, User> users = new HashMap<>();
        activeMemberships.forEach(m -> addUser(users, m.getUser()));
        expenses.forEach(e -> addUser(users, e.getPaidBy()));
        shares.forEach(s -> addUser(users, s.getUser()));
        payments.forEach(p -> {
            addUser(users, p.getFromUser());
            addUser(users, p.getToUser());
        });
        trendExpenses.forEach(e -> addUser(users, e.getPaidBy()));

        // Members in the list = active now OR took part in this period. Former members who only
        // appear in the trend are NOT listed; they only show up by name in the trend rows.
        Set<Long> memberIds = new HashSet<>(activeIds);
        expenses.forEach(e -> memberIds.add(e.getPaidBy().getId()));
        shares.forEach(s -> memberIds.add(s.getUser().getId()));
        payments.forEach(p -> {
            memberIds.add(p.getFromUser().getId());
            memberIds.add(p.getToUser().getId());
        });

        BigDecimal totalExpenses = sumAmounts(expenses);
        BigDecimal totalPayments = payments.stream().map(Payment::getAmount).reduce(MONEY_ZERO, BigDecimal::add);
        List<MemberStat> members = buildMembers(memberIds, activeIds, users, expenses, shares, payments, totalExpenses);

        return new StatementResponse(
                period.from(),
                period.to(),
                membership.getHousehold().getName(),
                totalExpenses,
                expenses.size(),
                totalPayments,
                payments.size(),
                members,
                findDominantPayer(members),
                buildCategories(expenses, totalExpenses),
                buildTrend(period.to(), trendExpenses, users),
                buildExpenseRows(expenses, shares),
                buildPaymentRows(payments));
    }

    // ---------- Period ----------

    private record Period(LocalDate from, LocalDate to) {
    }

    // No dates given means "this month" in Turkish time, the same zone the recurring job uses.
    // from and to must be given together, so a half-open period is never guessed.
    private Period resolvePeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            LocalDate today = LocalDate.now(RecurringExpenseService.ZONE);
            return new Period(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        }
        if (from == null || to == null) {
            throw new BadRequestException("Give both from and to, or neither");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        // Both ends are inclusive, so the day count is the difference plus one.
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_PERIOD_DAYS) {
            throw new BadRequestException("The period can be at most " + MAX_PERIOD_DAYS + " days long");
        }
        return new Period(from, to);
    }

    // ---------- Members ----------

    private List<MemberStat> buildMembers(Set<Long> memberIds, Set<Long> activeIds, Map<Long, User> users,
                                          List<Expense> expenses, List<ExpenseShare> shares,
                                          List<Payment> payments, BigDecimal totalExpenses) {
        Map<Long, BigDecimal> paid = sumBy(expenses, e -> e.getPaidBy().getId(), Expense::getAmount);
        Map<Long, BigDecimal> owed = sumBy(shares, s -> s.getUser().getId(), ExpenseShare::getOwedAmount);
        Map<Long, BigDecimal> sent = sumBy(payments, p -> p.getFromUser().getId(), Payment::getAmount);
        Map<Long, BigDecimal> received = sumBy(payments, p -> p.getToUser().getId(), Payment::getAmount);

        return memberIds.stream()
                .map(userId -> {
                    BigDecimal paidAmount = amountFor(paid, userId);
                    BigDecimal shareAmount = amountFor(owed, userId);
                    return new MemberStat(
                            userId,
                            users.get(userId).getName(),
                            activeIds.contains(userId),
                            paidAmount,
                            shareAmount,
                            paidAmount.subtract(shareAmount),
                            amountFor(sent, userId),
                            amountFor(received, userId),
                            percentOf(paidAmount, totalExpenses));
                })
                .sorted(Comparator.comparing(MemberStat::paid, Comparator.reverseOrder()))
                .toList();
    }

    // Nullable on purpose: null means "nobody is carrying the household in this period".
    // The list is already sorted by paid (biggest first), so findFirst returns the biggest payer.
    private DominantPayer findDominantPayer(List<MemberStat> members) {
        long sharers = members.stream().filter(m -> m.share().signum() > 0).count();
        if (sharers < DOMINANT_MIN_SHARERS) {
            return null;
        }
        return members.stream()
                .filter(m -> m.paidPercent().compareTo(DOMINANT_THRESHOLD) >= 0)
                .findFirst()
                .map(m -> new DominantPayer(m.userId(), m.name(), m.paidPercent()))
                .orElse(null);
    }

    // ---------- Categories ----------

    // groupingBy only creates a group for categories that have at least one expense, so count > 0 holds.
    private List<CategoryStat> buildCategories(List<Expense> expenses, BigDecimal totalExpenses) {
        Map<ExpenseCategory, List<Expense>> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory));
        return byCategory.entrySet().stream()
                .map(entry -> {
                    BigDecimal total = sumAmounts(entry.getValue());
                    return new CategoryStat(
                            entry.getKey(), total, entry.getValue().size(), percentOf(total, totalExpenses));
                })
                .sorted(Comparator.comparing(CategoryStat::total, Comparator.reverseOrder()))
                .toList();
    }

    // ---------- Trend ----------

    // Why this ignores the chosen from/to: the trend answers "how has spending moved lately?". It always
    // covers the 6 calendar months that end with the month of `to`, even when the statement is one week long.
    // Oldest month first, so the list reads like a chart from left to right.
    private List<MonthStat> buildTrend(LocalDate to, List<Expense> trendExpenses, Map<Long, User> users) {
        Map<YearMonth, List<Expense>> byMonth = trendExpenses.stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getExpenseDate())));

        LocalDate lastMonth = firstOfMonth(to);
        List<MonthStat> trend = new ArrayList<>();
        for (int back = TREND_MONTHS - 1; back >= 0; back--) {
            LocalDate month = lastMonth.minusMonths(back);
            List<Expense> inMonth = byMonth.getOrDefault(YearMonth.from(month), List.of());

            // Only people who paid something that month are listed.
            List<MonthMemberPaid> perMember = sumBy(inMonth, e -> e.getPaidBy().getId(), Expense::getAmount)
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().signum() > 0)
                    .map(entry -> new MonthMemberPaid(
                            entry.getKey(), users.get(entry.getKey()).getName(), entry.getValue()))
                    .sorted(Comparator.comparing(MonthMemberPaid::paid, Comparator.reverseOrder()))
                    .toList();
            trend.add(new MonthStat(month, sumAmounts(inMonth), perMember));
        }
        return trend;
    }

    // ---------- Detail rows ----------

    private List<StatementExpense> buildExpenseRows(List<Expense> expenses, List<ExpenseShare> shares) {
        Map<Long, List<ExpenseShare>> sharesByExpense = shares.stream()
                .collect(Collectors.groupingBy(s -> s.getExpense().getId()));
        return expenses.stream()
                .sorted(Comparator.comparing(Expense::getExpenseDate))
                .map(e -> new StatementExpense(
                        e.getId(),
                        e.getExpenseDate(),
                        e.getDescription(),
                        e.getCategory(),
                        e.getAmount(),
                        e.getPaidBy().getName(),
                        e.getSplitType(),
                        sharesByExpense.getOrDefault(e.getId(), List.of()).stream()
                                .map(s -> new StatementShare(s.getUser().getName(), s.getOwedAmount()))
                                .toList()))
                .toList();
    }

    private List<StatementPayment> buildPaymentRows(List<Payment> payments) {
        return payments.stream()
                .sorted(Comparator.comparing(Payment::getPaidAt))
                .map(p -> new StatementPayment(
                        p.getId(),
                        p.getPaidAt(),
                        p.getFromUser().getName(),
                        p.getToUser().getName(),
                        p.getAmount(),
                        p.getNote()))
                .toList();
    }

    // ---------- Helpers ----------

    // An empty IN list is pointless (and some databases reject it), so we skip the query then.
    private List<ExpenseShare> findSharesOf(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            return List.of();
        }
        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();
        return expenseShareRepository.findByExpenseIdIn(expenseIds);
    }

    private Instant startInstant(LocalDate day) {
        return day.atStartOfDay(RecurringExpenseService.ZONE).toInstant();
    }

    private LocalDate firstOfMonth(LocalDate day) {
        return day.withDayOfMonth(1);
    }

    // Percent with 1 decimal. Returns 0.0 for an empty period instead of dividing by zero.
    private BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return PERCENT_ZERO;
        }
        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sumAmounts(List<Expense> expenses) {
        return expenses.stream().map(Expense::getAmount).reduce(MONEY_ZERO, BigDecimal::add);
    }

    private BigDecimal amountFor(Map<Long, BigDecimal> amounts, Long userId) {
        return amounts.getOrDefault(userId, MONEY_ZERO);
    }

    private void addUser(Map<Long, User> users, User user) {
        users.putIfAbsent(user.getId(), user);
    }

    // Same grouping pattern as BalanceService: sums one amount per key (user id).
    private <T> Map<Long, BigDecimal> sumBy(List<T> items, Function<T, Long> keyFn, Function<T, BigDecimal> amountFn) {
        return items.stream().collect(Collectors.groupingBy(
                keyFn, Collectors.reducing(MONEY_ZERO, amountFn, BigDecimal::add)));
    }
}
