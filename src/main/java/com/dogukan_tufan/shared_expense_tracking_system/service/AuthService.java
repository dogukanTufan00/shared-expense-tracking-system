package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.LoginRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.LoginResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.RegisterRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.UserResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.EmailAlreadyExistsException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import com.dogukan_tufan.shared_expense_tracking_system.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Business rules live here, not in the controller (CLAUDE.md section 5): the controller only
// validates the request shape and calls this.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use: " + request.email());
        }

        User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
        user.setIban(request.iban());

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    public LoginResponse login(LoginRequest request) {
        // Delegates to AppUserDetailsService + the BCryptPasswordEncoder bean under the hood.
        // Throws an AuthenticationException (bad email or bad password, same exception either
        // way) if it doesn't match -- GlobalExceptionHandler turns that into a generic 401
        // without saying which one was wrong (CLAUDE.md section 8).
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String token = jwtService.generateToken(request.email());
        return new LoginResponse(token);
    }
}
