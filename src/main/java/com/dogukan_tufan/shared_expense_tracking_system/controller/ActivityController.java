package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.activity.ActivityResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only history of one household. Writes happen inside the other services, not here.
@RestController
@RequestMapping("/api/households/{householdId}/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Household history: who added, changed or removed what")
public class ActivityController {

    private final ActivityLogService activityLogService;

    @GetMapping
    @Operation(
            summary = "Household activity history",
            description = "Newest first. Only active members can read it. Use limit (1-100, default 30) "
                    + "and beforeId (the id of the last entry you have) to load older pages.")
    public List<ActivityResponse> list(
            @PathVariable Long householdId,
            @Parameter(description = "How many entries to return (1-100, default 30)")
            @RequestParam(defaultValue = "30") int limit,
            @Parameter(description = "Only entries with an id smaller than this (cursor for the next page)")
            @RequestParam(required = false) Long beforeId,
            Authentication authentication) {
        return activityLogService.list(householdId, limit, beforeId, authentication.getName());
    }
}
