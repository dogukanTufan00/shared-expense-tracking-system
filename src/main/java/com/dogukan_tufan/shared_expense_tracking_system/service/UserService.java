package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.auth.UserResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.device.RegisterDeviceRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.ChangePasswordRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.UpdateProfileRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.DeviceToken;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DeviceTokenRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Works on "the logged-in user" (/api/users/me). The caller is identified by the email in the JWT.
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IbanValidator ibanValidator;
    private final DeviceTokenRepository deviceTokenRepository;

    public UserResponse getMe(String currentUserEmail) {
        return UserResponse.from(getUser(currentUserEmail));
    }

    // Managed entity: setting fields is enough, Hibernate writes them when the method returns.
    public UserResponse updateProfile(String currentUserEmail, UpdateProfileRequest request) {
        User user = getUser(currentUserEmail);
        user.setName(request.name());
        user.setIban(ibanValidator.normalize(request.iban()));
        return UserResponse.from(user);
    }

    // A wrong current password is a 400, not a 401. The caller is already logged in, and 401 would
    // make the app think the token is invalid and log the user out.
    public void changePassword(String currentUserEmail, ChangePasswordRequest request) {
        User user = getUser(currentUserEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    // Called after login on the phone. One phone has one push token. If the token already exists
    // (maybe under another user, when a different person logs in on the same phone), it is moved to
    // the current user. Otherwise the old account would keep receiving pushes on a phone it no longer uses.
    public void registerDevice(String currentUserEmail, RegisterDeviceRequest request) {
        User user = getUser(currentUserEmail);
        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(request.token());
        if (existing.isPresent()) {
            existing.get().reassign(user, request.language());
        } else {
            deviceTokenRepository.save(new DeviceToken(user, request.token(), request.language()));
        }
    }

    // Called on logout. Unknown tokens or tokens of another user are ignored, so the call is always safe to repeat.
    public void unregisterDevice(String currentUserEmail, String token) {
        User user = getUser(currentUserEmail);
        deviceTokenRepository.deleteByUserIdAndToken(user.getId(), token);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
