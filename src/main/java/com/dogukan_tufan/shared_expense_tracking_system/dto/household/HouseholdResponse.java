package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;

import java.time.Instant;

public record HouseholdResponse(
        Long id,
        String name,
        Long createdByUserId,
        Instant createdAt
) {

    public static HouseholdResponse from(Household household) {
        return new HouseholdResponse(
                household.getId(),
                household.getName(),
                household.getCreatedBy().getId(),
                household.getCreatedAt());
    }
}
