package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.UserResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.ChangePasswordRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.UpdateProfileRequest;
import com.dogukan_tufan.shared_expense_tracking_system.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// JWT-protected by the default rule in SecurityConfig (anyRequest().authenticated()), so no config change is needed.
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse getMe(Authentication authentication) {
        return userService.getMe(authentication.getName());
    }

    @PutMapping
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userService.changePassword(authentication.getName(), request);
    }
}
