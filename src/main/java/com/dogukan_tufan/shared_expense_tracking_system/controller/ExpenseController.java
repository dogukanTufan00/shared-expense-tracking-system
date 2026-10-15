package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/households/{householdId}/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable Long householdId, @Valid @RequestBody ExpenseRequest request,
                                   Authentication authentication) {
        return expenseService.create(householdId, request, authentication.getName());
    }

    @GetMapping
    public List<ExpenseResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return expenseService.list(householdId, authentication.getName());
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse getById(@PathVariable Long householdId, @PathVariable Long expenseId,
                                    Authentication authentication) {
        return expenseService.getById(householdId, expenseId, authentication.getName());
    }

    @PutMapping("/{expenseId}")
    public ExpenseResponse update(@PathVariable Long householdId, @PathVariable Long expenseId,
                                   @Valid @RequestBody ExpenseRequest request, Authentication authentication) {
        return expenseService.update(householdId, expenseId, request, authentication.getName());
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long householdId, @PathVariable Long expenseId, Authentication authentication) {
        expenseService.delete(householdId, expenseId, authentication.getName());
    }
}
