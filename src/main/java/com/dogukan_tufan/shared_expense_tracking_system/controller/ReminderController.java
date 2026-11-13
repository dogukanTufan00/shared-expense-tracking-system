package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.ReminderCooldownResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.ReminderResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.reminder.SendReminderRequest;
import com.dogukan_tufan.shared_expense_tracking_system.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/households/{householdId}/reminders")
@RequiredArgsConstructor
@Tag(name = "Reminders")
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a polite payment reminder to someone who owes you (max once per 24 hours)")
    public ReminderResponse send(@PathVariable Long householdId, @Valid @RequestBody SendReminderRequest request,
                                 Authentication authentication) {
        return reminderService.send(householdId, request, authentication.getName());
    }

    @GetMapping("/cooldowns")
    @Operation(summary = "List the debtors you reminded in the last 24 hours and when you may remind them again")
    public List<ReminderCooldownResponse> cooldowns(@PathVariable Long householdId, Authentication authentication) {
        return reminderService.cooldowns(householdId, authentication.getName());
    }
}
