package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    // All phones of one user. Used when a push notification is sent to that user.
    List<DeviceToken> findByUserId(Long userId);

    // Logout: only removes the token if it belongs to this user. Anything else is a no-op.
    @Transactional
    void deleteByUserIdAndToken(Long userId, String token);

    // Used when FCM says a token is dead. A bulk delete query, so it runs in its own short
    // transaction even though PushService itself is not transactional.
    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token = :token")
    int deleteByToken(@Param("token") String token);
}
