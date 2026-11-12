package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.RecurringExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.RecurringRunResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.RecurringExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Same base path as ExpenseController. "/recurring" is a literal path, so Spring matches it
// before ExpenseController's "/{expenseId}" pattern (literal paths win over variables).
@RestController
@RequestMapping("/api/households/{householdId}/expenses/recurring")
@RequiredArgsConstructor
@Tag(name = "Recurring Expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    @Operation(summary = "List recurring expense chains (any active member)",
            description = "Every expense with recurring=true, i.e. the latest copy of each chain. "
                    + "nextDate is when the next copy will be created.")
    public List<RecurringExpenseResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return recurringExpenseService.listForHousehold(householdId, authentication.getName());
    }

    @PostMapping("/run")
    @Operation(summary = "Create due recurring expenses now (ADMIN only)",
            description = "For demos and testing only. Recurring expenses are normally created by the daily "
                    + "job at 00:05 (Europe/Istanbul), so without this you would have to wait a month to see "
                    + "the next copy. Runs the same generation, but only for this household.")
    public RecurringRunResponse runNow(@PathVariable Long householdId, Authentication authentication) {
        return recurringExpenseService.runForHousehold(householdId, authentication.getName());
    }
}
