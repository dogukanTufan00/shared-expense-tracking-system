package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault.CategorySplitDefaultResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault.SplitDefaultRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.service.SplitDefaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/households/{householdId}/split-defaults")
@RequiredArgsConstructor
@Tag(name = "Split defaults")
public class SplitDefaultController {

    private final SplitDefaultService splitDefaultService;

    @GetMapping
    @Operation(summary = "List the default splits of every category (any active member)")
    public List<CategorySplitDefaultResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return splitDefaultService.listDefaults(householdId, authentication.getName());
    }

    @PutMapping("/{category}")
    @Operation(summary = "Replace the default split of one category (ADMIN only)")
    public CategorySplitDefaultResponse replace(@PathVariable Long householdId, @PathVariable ExpenseCategory category,
                                                @Valid @RequestBody SplitDefaultRequest request,
                                                Authentication authentication) {
        return splitDefaultService.replaceDefault(householdId, category, request, authentication.getName());
    }

    @DeleteMapping("/{category}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the default split of one category (ADMIN only)")
    public void delete(@PathVariable Long householdId, @PathVariable ExpenseCategory category,
                       Authentication authentication) {
        splitDefaultService.deleteDefault(householdId, category, authentication.getName());
    }
}
