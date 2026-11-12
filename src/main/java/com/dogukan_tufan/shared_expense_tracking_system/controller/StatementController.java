package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.statement.StatementResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// Read-only report endpoint. It lives under /api/households because it is a view of one household's data.
// @DateTimeFormat(iso = DATE) makes Spring parse "2026-11-01" into a LocalDate; a malformed date
// fails conversion and GlobalExceptionHandler turns it into a 400.
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "Period account statement and per-person statistics")
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/{householdId}/statement")
    @Operation(
            summary = "Period statement and per-person statistics",
            description = "Lists the household's expenses and payments in a date range, with totals, "
                    + "who paid how much, category totals and a 6-month trend. Both dates are optional and inclusive. "
                    + "Give both or neither: with neither, the current calendar month (Europe/Istanbul) is used. "
                    + "The range can be at most 366 days.")
    public StatementResponse getStatement(
            @PathVariable Long householdId,
            @Parameter(description = "First day of the period, ISO format yyyy-MM-dd (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, ISO format yyyy-MM-dd (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return statementService.getStatement(householdId, from, to, authentication.getName());
    }
}
