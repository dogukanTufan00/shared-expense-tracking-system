package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// A phone's Firebase push token, linked to the user who is logged in on that phone.
//
// Why unique token: one phone has one token. If a different user logs in on the same phone, the
// row is moved to that user (see UserService) instead of creating a second row.
// Why language is stored: the push text is built on the server, so it must know TR or EN.
@Entity
@Table(name = "device_tokens",
        indexes = @Index(name = "idx_device_tokens_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // FCM tokens are about 160 characters; 512 leaves room for longer ones.
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DeviceToken(User user, String token, String language) {
        this.user = user;
        this.token = token;
        this.language = language;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // Called when the same phone logs in again (possibly as another user) or changes app language.
    public void reassign(User user, String language) {
        this.user = user;
        this.language = language;
        this.updatedAt = Instant.now();
    }
}
