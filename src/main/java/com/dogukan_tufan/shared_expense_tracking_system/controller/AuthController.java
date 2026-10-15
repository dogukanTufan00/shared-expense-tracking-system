package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.LoginRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.LoginResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.RegisterRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.UserResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Thin by design (CLAUDE.md section 5): validate the request (@Valid), call the service, return
// a DTO. No business logic lives here.
// @SecurityRequirements (empty) overrides the global bearerAuth requirement from OpenApiConfig --
// these endpoints are public, so Swagger UI should not show a lock icon on them.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
