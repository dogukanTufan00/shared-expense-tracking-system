package com.dogukan_tufan.shared_expense_tracking_system.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Declares the "bearerAuth" scheme once, globally: this is what makes the "Authorize" button
// appear in Swagger UI so the developer can paste a JWT and have it sent as
// "Authorization: Bearer <token>" on every request made from the Swagger UI from then on.
// Individual public controllers (e.g. AuthController) opt back out with @SecurityRequirements.
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Shared Expense Tracking System API", version = "v1"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
