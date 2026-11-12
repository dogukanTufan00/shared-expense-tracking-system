package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.config.FirebaseMessagingHolder;
import com.dogukan_tufan.shared_expense_tracking_system.entity.DeviceToken;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DeviceTokenRepository;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// Sends the push message for each saved in-app notification to every phone of its recipient.
//
// Why this runs after the notification rows are committed (see NotificationService): a push must
// never announce something that was rolled back. Why it never throws: the expense or payment is
// already saved; a failed push is only logged.
// Why no transaction here: the tokens are read first, then the network calls happen with no open
// database transaction. The only write (removing dead tokens) runs in its own short transaction.
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    // Android notification channel. The Flutter app must create a channel with this id.
    private static final String ANDROID_CHANNEL_ID = "evce_default";

    private final FirebaseMessagingHolder firebase;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushTextBuilder pushTextBuilder;

    public void send(List<Notification> notifications) {
        if (notifications.isEmpty() || !firebase.isEnabled()) {
            return;
        }
        try {
            List<Message> messages = new ArrayList<>();
            List<String> tokens = new ArrayList<>();
            for (Notification notification : notifications) {
                Long recipientId = notification.getRecipient().getId();
                for (DeviceToken device : deviceTokenRepository.findByUserId(recipientId)) {
                    PushTextBuilder.PushText text = pushTextBuilder.build(notification, device.getLanguage());
                    messages.add(buildMessage(notification, device.getToken(), text));
                    tokens.add(device.getToken());
                }
            }
            if (messages.isEmpty()) {
                return;
            }

            // sendEach returns one result per message, in the same order as the list we passed in.
            BatchResponse response = firebase.messaging().sendEach(messages);
            List<SendResponse> results = response.getResponses();
            for (int i = 0; i < results.size(); i++) {
                handleResult(results.get(i), tokens.get(i));
            }
        } catch (Exception e) {
            log.error("Push: could not send notifications", e);
        }
    }

    // UNREGISTERED: the app was uninstalled or the token expired, so it can never work again and is
    // deleted. Everything else (INVALID_ARGUMENT, network, quota) is only logged: INVALID_ARGUMENT can
    // also mean WE built a bad message, and deleting tokens then would silently cut off every phone.
    private void handleResult(SendResponse result, String token) {
        if (result.isSuccessful()) {
            return;
        }
        FirebaseMessagingException error = result.getException();
        MessagingErrorCode code = error == null ? null : error.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED) {
            deviceTokenRepository.deleteByToken(token);
            log.info("Push: removed a stale device token ({})", code);
        } else {
            log.warn("Push: FCM did not accept a message ({}): {}", code,
                    error == null ? "no details" : error.getMessage());
        }
    }

    // The notification id is the Android tag: the phone shows one notice per id, so a message that
    // arrives twice does not appear twice.
    private Message buildMessage(Notification notification, String token, PushTextBuilder.PushText text) {
        String notificationId = String.valueOf(notification.getId());
        String householdId = notification.getHousehold() == null
                ? ""
                : String.valueOf(notification.getHousehold().getId());
        String invitationId = notification.getInvitationId() == null
                ? ""
                : String.valueOf(notification.getInvitationId());

        return Message.builder()
                .setToken(token)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(text.title())
                        .setBody(text.body())
                        .build())
                // Data is read by the app when the user taps the push, to open the right screen.
                .putData("type", notification.getType().name())
                .putData("notificationId", notificationId)
                .putData("householdId", householdId)
                .putData("invitationId", invitationId)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL_ID)
                                .setTag(notificationId)
                                .build())
                        .build())
                .build();
    }
}
