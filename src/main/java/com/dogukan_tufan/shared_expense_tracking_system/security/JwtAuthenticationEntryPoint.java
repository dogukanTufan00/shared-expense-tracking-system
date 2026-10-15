package com.dogukan_tufan.shared_expense_tracking_system.security;

import com.dogukan_tufan.shared_expense_tracking_system.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

// Without this, Spring Security's own default for "no/invalid JWT on a protected route" is a
// bare 403 with no body. @RestControllerAdvice can't catch it either -- it only runs for
// exceptions thrown inside a controller, and this happens earlier, in the security filter chain.
// This makes missing/invalid auth return 401 in the same JSON shape as every other error
// (CLAUDE.md section 9).
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
