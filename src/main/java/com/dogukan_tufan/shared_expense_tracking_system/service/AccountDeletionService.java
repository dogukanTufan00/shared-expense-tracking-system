package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.balance.BalanceResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.DeleteAccountRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.DeletionCheckResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.DeletionCheckResponse.Blocker;
import com.dogukan_tufan.shared_expense_tracking_system.dto.user.DeletionCheckResponse.OpenBalance;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Invitation;
import com.dogukan_tufan.shared_expense_tracking_system.entity.InvitationStatus;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.DeviceTokenRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.InvitationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.NotificationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Account deletion (KVKK / Google Play requirement). We ANONYMIZE instead of hard-deleting the user:
// expenses, shares and payments of the user are part of the roommates' balances, so those rows must
// stay and keep pointing at a real user row. Everything that identifies the person is removed.
@Service
@RequiredArgsConstructor
@Transactional
public class AccountDeletionService {

    // "Silinmis kullanici" with the Turkish letters written as unicode escapes, so the result does not
    // depend on the source file encoding.
    static final String DELETED_USER_NAME = "Silinmiş kullanıcı";
    private static final BigDecimal VISIBLE_BALANCE_THRESHOLD = new BigDecimal("0.01");
    private static final String FOUNDER_WITH_MEMBERS = "FOUNDER_WITH_MEMBERS";
    private static final String LAST_ADMIN_WITH_MEMBERS = "LAST_ADMIN_WITH_MEMBERS";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final HouseholdService householdService;
    private final BalanceService balanceService;

    // Read-only preview for the app's warning screen. Uses the same blocker rules as deleteAccount.
    @Transactional(readOnly = true)
    public DeletionCheckResponse getDeletionCheck(String currentUserEmail) {
        User user = getUser(currentUserEmail);

        List<Blocker> blockers = new ArrayList<>();
        List<String> householdsToDelete = new ArrayList<>();
        List<OpenBalance> openBalances = new ArrayList<>();

        for (Membership membership : membershipRepository.findByUserIdAndLeftAtIsNull(user.getId())) {
            Household household = membership.getHousehold();

            if (isOnlyActiveMember(household)) {
                householdsToDelete.add(household.getName());
            } else {
                String reason = blockerReason(membership);
                if (reason != null) {
                    blockers.add(new Blocker(household.getId(), household.getName(), reason));
                }
            }

            BigDecimal net = netBalanceOf(user, household);
            if (net.abs().compareTo(VISIBLE_BALANCE_THRESHOLD) >= 0) {
                openBalances.add(new OpenBalance(household.getId(), household.getName(), net));
            }
        }

        return new DeletionCheckResponse(blockers, openBalances, householdsToDelete);
    }

    public void deleteAccount(String currentUserEmail, DeleteAccountRequest request) {
        User user = getUser(currentUserEmail);

        // 400, not 401: a 401 would make the app think the token is invalid and log the user out.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }

        List<Membership> activeMemberships = membershipRepository.findByUserIdAndLeftAtIsNull(user.getId());
        rejectIfBlocked(activeMemberships);

        leaveOrDeleteHouseholds(activeMemberships);
        cancelPendingInvitations(user);
        deleteDevicesAndOwnNotifications(user);
        replaceNameInHouseholdNotifications(user);
        anonymize(user);
    }

    // Stops the deletion when the user is still needed somewhere: founder of a home with other people,
    // or the only admin of a home with other people. Those must hand over first, otherwise the home
    // would be left without an owner or without anyone who can manage it.
    private void rejectIfBlocked(List<Membership> activeMemberships) {
        List<String> names = new ArrayList<>();
        for (Membership membership : activeMemberships) {
            if (!isOnlyActiveMember(membership.getHousehold()) && blockerReason(membership) != null) {
                names.add(membership.getHousehold().getName());
            }
        }
        if (!names.isEmpty()) {
            throw new ConflictException("Transfer ownership or hand over admin rights first: "
                    + String.join(", ", names));
        }
    }

    // Returns the reason the user cannot leave this household, or null if they can leave.
    // Only meaningful for households with other active members (callers check that first).
    private String blockerReason(Membership membership) {
        Household household = membership.getHousehold();
        if (household.getCreatedBy().getId().equals(membership.getUser().getId())) {
            return FOUNDER_WITH_MEMBERS;
        }
        if (membership.getRole() == MembershipRole.ADMIN
                && membershipRepository.countByHouseholdIdAndRoleAndLeftAtIsNull(household.getId(), MembershipRole.ADMIN) <= 1) {
            return LAST_ADMIN_WITH_MEMBERS;
        }
        return null;
    }

    private boolean isOnlyActiveMember(Household household) {
        return membershipRepository.countByHouseholdIdAndLeftAtIsNull(household.getId()) <= 1;
    }

    // Solo homes are deleted: nobody else can see their data anymore, so keeping it has no use.
    // Other homes are left: the membership row stays with leftAt set, so balances and history still work.
    // Leaving is done first, because deleting a solo home also deletes its memberships.
    private void leaveOrDeleteHouseholds(List<Membership> activeMemberships) {
        List<Household> soloHouseholds = new ArrayList<>();
        Instant now = Instant.now();
        for (Membership membership : activeMemberships) {
            if (isOnlyActiveMember(membership.getHousehold())) {
                soloHouseholds.add(membership.getHousehold());
            } else {
                membership.setLeftAt(now);
            }
        }
        for (Household household : soloHouseholds) {
            householdService.purgeHousehold(household);
        }
    }

    // A pending invitation in either direction would let someone accept for a person who no longer
    // exists. Rows are kept and marked CANCELLED, the same way an admin cancel works.
    private void cancelPendingInvitations(User user) {
        Instant now = Instant.now();
        List<Invitation> sent = invitationRepository.findByInvitedByIdAndStatus(user.getId(), InvitationStatus.PENDING);
        List<Invitation> received = invitationRepository.findByInvitedUserIdAndStatus(user.getId(), InvitationStatus.PENDING);
        for (Invitation invitation : sent) {
            invitation.setStatus(InvitationStatus.CANCELLED);
            invitation.setRespondedAt(now);
        }
        for (Invitation invitation : received) {
            invitation.setStatus(InvitationStatus.CANCELLED);
            invitation.setRespondedAt(now);
        }
    }

    // Push tokens: without this, the deleted user's phones would keep getting notifications.
    // Own notifications: they are the user's inbox, which goes with the account.
    private void deleteDevicesAndOwnNotifications(User user) {
        deviceTokenRepository.deleteByUserId(user.getId());
        notificationRepository.deleteByRecipientId(user.getId());
    }

    // Notifications copy names as text (see Notification entity), so the other roommates' inboxes would
    // still show the old name. We replace it in the homes the user was part of. Only the exact current
    // name is replaced: an older name (from before a rename) is not matched by this rule.
    private void replaceNameInHouseholdNotifications(User user) {
        List<Long> householdIds = membershipRepository.findByUserId(user.getId()).stream()
                .map(membership -> membership.getHousehold().getId())
                .distinct()
                .toList();
        // An empty IN (...) list is not valid SQL on every database, and there is nothing to change anyway.
        if (householdIds.isEmpty()) {
            return;
        }
        notificationRepository.replaceActorName(householdIds, user.getName(), DELETED_USER_NAME);
        notificationRepository.replaceTargetName(householdIds, user.getName(), DELETED_USER_NAME);
    }

    // The row stays (foreign keys from expenses and payments), but every personal field is replaced.
    private void anonymize(User user) {
        // Frees the real email, so the same address can register again. ".invalid" is a reserved
        // TLD that can never be a real address. The id keeps it unique.
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setName(DELETED_USER_NAME);
        // A hash of a random UUID nobody knows: login becomes impossible, even if the email matched.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        // Bank details are personal data and no longer needed.
        user.setIban(null);
        user.setDeletedAt(Instant.now());
    }

    private BigDecimal netBalanceOf(User user, Household household) {
        return balanceService.getBalances(household.getId(), user.getEmail()).stream()
                .filter(balance -> balance.userId().equals(user.getId()))
                .map(BalanceResponse::netBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
