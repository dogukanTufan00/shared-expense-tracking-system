package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String name,
        String email,
        MembershipRole role,
        Instant joinedAt
) {

    public static MemberResponse from(Membership membership) {
        User user = membership.getUser();
        return new MemberResponse(user.getId(), user.getName(), user.getEmail(), membership.getRole(), membership.getJoinedAt());
    }
}
