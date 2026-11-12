package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ShareInput;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The rules for a custom split's "shares" list. Used by ExpenseService (expense create/update)
// and SplitDefaultService (saved category defaults), so both follow exactly the same rules.
// Every check here throws BadRequestException (HTTP 400) with a message that names the problem.
@Component
@RequiredArgsConstructor
public class ShareValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final MembershipRepository membershipRepository;

    // Checks who is in the split: at least one person, nobody listed twice, and everybody an
    // ACTIVE member of the household. Returns the User of each entry, in the same order as the input.
    public List<User> requireValidParticipants(Long householdId, List<ShareInput> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new BadRequestException("At least one participant is required");
        }

        Set<Long> seenUserIds = new HashSet<>();
        List<User> users = new ArrayList<>();
        for (ShareInput share : shares) {
            // A Set remembers what we already saw, so a repeated userId is caught here.
            if (!seenUserIds.add(share.userId())) {
                throw new BadRequestException("Duplicate user " + share.userId() + " in shares");
            }
            // Only active members may share an expense; someone who left is rejected.
            Membership membership = membershipRepository
                    .findByUserIdAndHouseholdIdAndLeftAtIsNull(share.userId(), householdId)
                    .orElseThrow(() -> new BadRequestException(
                            "User " + share.userId() + " is not an active member of this household"));
            users.add(membership.getUser());
        }
        return users;
    }

    // PERCENT rules: every percentage is above 0 with at most 2 decimals, and together they are exactly 100.
    public void requirePercentages(List<ShareInput> shares) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShareInput share : shares) {
            requirePositiveValue(share.value(), "Each percentage must be greater than 0");
            total = total.add(share.value());
        }
        // compareTo, not equals: BigDecimal "100" and "100.00" are equal numbers but not equal objects.
        if (total.compareTo(HUNDRED) != 0) {
            throw new BadRequestException("Percentages must add up to 100 (got " + total.setScale(2) + ")");
        }
    }

    // AMOUNT rules: every amount is above 0 with at most 2 decimals, and together they equal the expense amount.
    public void requireAmounts(List<ShareInput> shares, BigDecimal expenseAmount) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShareInput share : shares) {
            requirePositiveValue(share.value(), "Each share amount must be greater than 0");
            total = total.add(share.value());
        }
        if (total.compareTo(expenseAmount) != 0) {
            throw new BadRequestException("Share amounts must add up to the expense amount "
                    + expenseAmount.setScale(2) + " (got " + total.setScale(2) + ")");
        }
    }

    private void requirePositiveValue(BigDecimal value, String zeroMessage) {
        if (value == null) {
            throw new BadRequestException("Every share needs a value");
        }
        if (value.signum() <= 0) {
            throw new BadRequestException(zeroMessage);
        }
        // stripTrailingZeros makes "10.000" count as 0 decimals; a scale above 2 means real extra digits.
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Share values can have at most 2 decimal places (got " + value + ")");
        }
    }
}
