package com.dogukan_tufan.shared_expense_tracking_system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds the app.jwt.* keys from application.yaml (secret, expiration-ms) onto this class,
// so JwtService does not have to read @Value fields one by one.
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;
}
