package com.dogukan_tufan.shared_expense_tracking_system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.firebase.* from application.yaml. credentialsPath is the location of the Firebase
// service-account key file. It is read ONLY from the FIREBASE_CREDENTIALS environment variable,
// because the key is a secret and must never be stored in the repo.
@Getter
@Setter
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {

    private String credentialsPath;
}
