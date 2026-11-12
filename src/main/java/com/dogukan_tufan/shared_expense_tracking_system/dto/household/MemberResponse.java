package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String name,
        String email,
        // Housemates can see each other's IBAN so they know where to send money. Only active members
        // ever get this response (non-members get 404 first), so outsiders never see it.
        String iban,
        MembershipRole role,
        Instant joinedAt,
        // True for the household founder (Household.createdBy), so the app can show a badge and
        // know who is protected from role changes/removal.
        boolean founder
) {

    public static MemberResponse from(Membership membership) {
        User user = membership.getUser();
        boolean founder = membership.getHousehold().getCreatedBy().getId().equals(user.getId());
        return new MemberResponse(user.getId(), user.getName(), user.getEmail(), user.getIban(),
                membership.getRole(), membership.getJoinedAt(), founder);
    }
}
