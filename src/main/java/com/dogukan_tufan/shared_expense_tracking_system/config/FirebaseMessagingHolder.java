package com.dogukan_tufan.shared_expense_tracking_system.config;

import com.google.firebase.messaging.FirebaseMessaging;

// Holds the Firebase client, or null when push is disabled (no credentials file).
// Why a holder and not a plain FirebaseMessaging bean: the bean must always exist so PushService
// can be injected; when push is off, messaging() is simply null and PushService does nothing.
public record FirebaseMessagingHolder(FirebaseMessaging messaging) {

    public boolean isEnabled() {
        return messaging != null;
    }
}
