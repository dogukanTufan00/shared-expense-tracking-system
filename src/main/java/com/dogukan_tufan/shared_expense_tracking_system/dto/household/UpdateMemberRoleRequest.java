package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(

        @NotNull(message = "Role is required")
        MembershipRole role
) {
}
