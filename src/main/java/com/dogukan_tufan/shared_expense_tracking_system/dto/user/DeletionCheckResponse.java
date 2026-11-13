package com.dogukan_tufan.shared_expense_tracking_system.dto.user;

import java.math.BigDecimal;
import java.util.List;

// Read before DELETE /api/users/me so the app can warn the user first.
// blockers: reasons the deletion is refused right now.
// openBalances: households with money still owed (warning only, does not block).
// householdsToDelete: homes where the user is the only active member; they will be deleted.
public record DeletionCheckResponse(
        List<Blocker> blockers,
        List<OpenBalance> openBalances,
        List<String> householdsToDelete
) {

    // reason is "FOUNDER_WITH_MEMBERS" or "LAST_ADMIN_WITH_MEMBERS".
    public record Blocker(Long householdId, String householdName, String reason) {
    }

    // net > 0 means the user is owed money in that household, net < 0 means the user owes.
    public record OpenBalance(Long householdId, String householdName, BigDecimal net) {
    }
}
