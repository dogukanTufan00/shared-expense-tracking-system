package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.CreateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.HouseholdResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.UpdateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.service.HouseholdService;
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

// Authentication is injected by Spring MVC/Security for any request that got past
// JwtAuthenticationFilter; authentication.getName() is the email we put as the JWT subject
// (AppUserDetailsService builds the UserDetails with .withUsername(user.getEmail())), so this
// is how every method here finds out "which user is making this request".
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
@Tag(name = "Households")
public class HouseholdController {

    private final HouseholdService householdService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdResponse create(@Valid @RequestBody CreateHouseholdRequest request, Authentication authentication) {
        return householdService.create(request, authentication.getName());
    }

    @GetMapping
    public List<HouseholdResponse> listMine(Authentication authentication) {
        return householdService.listMine(authentication.getName());
    }

    @GetMapping("/{id}")
    public HouseholdResponse getById(@PathVariable Long id, Authentication authentication) {
        return householdService.getById(id, authentication.getName());
    }

    @PutMapping("/{id}")
    public HouseholdResponse rename(@PathVariable Long id, @Valid @RequestBody UpdateHouseholdRequest request,
                                     Authentication authentication) {
        return householdService.rename(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        householdService.delete(id, authentication.getName());
    }
}
