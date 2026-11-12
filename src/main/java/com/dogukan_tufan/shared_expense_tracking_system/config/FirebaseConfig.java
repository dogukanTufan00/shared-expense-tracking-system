package com.dogukan_tufan.shared_expense_tracking_system.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Starts the Firebase Admin SDK (needed for push notifications).
//
// Why push is optional: the API must run without Firebase too (tests, other developers' machines,
// the course demo). So when no credentials file is configured, we log one line and expose a holder
// with no client. In-app notifications always work either way.
@Slf4j
@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    public FirebaseMessagingHolder firebaseMessagingHolder(FirebaseProperties properties) {
        String path = properties.getCredentialsPath();
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            log.info("Push notifications disabled: FIREBASE_CREDENTIALS not set");
            return new FirebaseMessagingHolder(null);
        }

        try (InputStream credentials = new FileInputStream(path)) {
            // FirebaseApp is process-wide. Initialize it only once (a restart of the Spring context
            // in dev tools would otherwise fail with "default app already exists").
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(credentials))
                            .build())
                    : FirebaseApp.getInstance();
            log.info("Push notifications enabled (Firebase Cloud Messaging)");
            return new FirebaseMessagingHolder(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException e) {
            // A broken key file must not stop the whole API from starting. Log it and run without push.
            log.error("Push notifications disabled: could not load the Firebase credentials file", e);
            return new FirebaseMessagingHolder(null);
        }
    }
}
