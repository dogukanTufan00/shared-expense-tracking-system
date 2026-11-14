package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.ChoreReminderResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.ChoreResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.CreateChoreRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.UpdateChoreRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Chore;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreCompletion;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreReminder;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreRotationMember;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.event.ChoreReminderSentEvent;
import com.dogukan_tufan.shared_expense_tracking_system.event.ChoreTurnEvent;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.TooManyRequestsException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ChoreCompletionRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ChoreReminderRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ChoreRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ChoreRotationMemberRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Chores that pass between household members in turn ("Bulasik", "Cop").
//
// Why a rotation table and a current person: the rotation is the fixed list of people and their order.
// The current person is whose turn it is now. Marking a chore done moves the turn to the next person
// in the rotation, but the turn never has to be taken by the person whose turn it was: anyone may do it.
//
// Why new members are NOT added to a rotation automatically: some chores are personal (e.g. "my own
// bike"), and a newcomer should not end up on a rotation they never agreed to. People are added by
// editing the chore.
//
// Why marking done is not in the activity history: it repeats every few days and would flood the list.
// The recent completions on the chore show it instead.
@Service
@RequiredArgsConstructor
@Transactional
public class ChoreService {

    // Same length as the payment reminder cooldown: one nudge per sender per chore per person per day.
    static final Duration REMINDER_COOLDOWN = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final ChoreRepository choreRepository;
    private final ChoreRotationMemberRepository rotationRepository;
    private final ChoreCompletionRepository completionRepository;
    private final ChoreReminderRepository reminderRepository;
    private final HouseholdAccessService accessService;
    private final ActivityLogService activityLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ChoreResponse> list(Long householdId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);

        // One query for every rotation in the household, instead of one query per chore.
        Map<Long, List<ChoreRotationMember>> rotationByChore = new HashMap<>();
        for (ChoreRotationMember row : rotationRepository.findByChoreHouseholdIdOrderByChoreIdAscPositionAsc(householdId)) {
            rotationByChore.computeIfAbsent(row.getChore().getId(), id -> new ArrayList<>()).add(row);
        }

        // Case-insensitive name order; the id breaks ties so the order is always the same.
        return choreRepository.findByHouseholdId(householdId).stream()
                .sorted(Comparator.comparing(Chore::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Chore::getId))
                .map(chore -> toResponse(chore, rotationByChore.getOrDefault(chore.getId(), List.of()), caller))
                .toList();
    }

    public ChoreResponse create(Long householdId, CreateChoreRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(caller.getId(), householdId);
        Household household = membership.getHousehold();

        if (choreRepository.countByHouseholdId(householdId) >= HouseholdLimits.MAX_CHORES_PER_HOUSEHOLD) {
            throw new ConflictException("A household can have at most "
                    + HouseholdLimits.MAX_CHORES_PER_HOUSEHOLD + " chores");
        }
        String name = requireName(request.name());
        List<User> rotation = resolveRotation(householdId, request.rotationUserIds());

        // The first person in the rotation starts.
        Chore chore = new Chore(household, name, rotation.get(0));
        choreRepository.save(chore);
        List<ChoreRotationMember> rows = saveRotation(chore, rotation);
        activityLogService.choreAdded(chore, caller);
        return toResponse(chore, rows, caller);
    }

    // Replaces the whole chore. The rotation rows are rewritten; the completion and reminder history stays.
    public ChoreResponse update(Long householdId, Long choreId, UpdateChoreRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        Chore chore = findChore(householdId, choreId);

        String name = requireName(request.name());
        List<User> rotation = resolveRotation(householdId, request.rotationUserIds());
        List<ChoreRotationMember> oldRows = rotationRepository.findByChoreIdOrderByPositionAsc(choreId);
        ActivityLogService.ChoreSnapshot before = ActivityLogService.ChoreSnapshot.of(chore, idsOfRows(oldRows));

        User newCurrent = pickCurrentAfterUpdate(chore, rotation, request.currentUserId());
        chore.setName(name);
        chore.setCurrentUser(newCurrent);

        // Old rows go first and are flushed before the new ones are written. Otherwise the unique
        // (chore, user) constraint could clash for a person who keeps their place in the rotation.
        rotationRepository.deleteByChoreId(choreId);
        rotationRepository.flush();
        List<ChoreRotationMember> rows = saveRotation(chore, rotation);

        ActivityLogService.ChoreSnapshot after = ActivityLogService.ChoreSnapshot.of(chore, idsOfUsers(rotation));
        activityLogService.choreUpdated(chore, caller, before, after, request.currentUserId() != null);
        return toResponse(chore, rows, caller);
    }

    public void delete(Long householdId, Long choreId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        Chore chore = findChore(householdId, choreId);

        // Recorded first, while the chore still has its name.
        activityLogService.choreDeleted(chore, caller);
        // Children first: reminders and completions point at the chore, and rotation rows too.
        reminderRepository.deleteByChoreId(choreId);
        completionRepository.deleteByChoreId(choreId);
        rotationRepository.deleteByChoreId(choreId);
        choreRepository.delete(chore);
    }

    // "I did it". Any active member may do it, even when it is not their turn. The turn then moves to
    // the rotation member right after the CALLER (wrapping around). If the caller is not in the rotation,
    // it moves to the one right after the current person.
    public ChoreResponse markDone(Long householdId, Long choreId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        Chore chore = findChore(householdId, choreId);

        List<ChoreRotationMember> rows = rotationRepository.findByChoreIdOrderByPositionAsc(choreId);
        User next = nextInRotation(rows, caller, chore.getCurrentUser());

        completionRepository.save(new ChoreCompletion(chore, caller));
        chore.setCurrentUser(next);

        // Nobody is told about their own action. A single-person rotation keeps the turn with the caller.
        if (next != null && !next.getId().equals(caller.getId())) {
            // Sent after the commit by NotificationService, so the notice only goes out if this saves.
            eventPublisher.publishEvent(new ChoreTurnEvent(choreId, caller.getId(), next.getId()));
        }
        return toResponse(chore, rows, caller);
    }

    // A nudge to the person whose turn it is. Same 24-hour rule as payment reminders: one per sender,
    // per chore, per person, so nobody can spam a roommate.
    public ChoreReminderResponse remind(Long householdId, Long choreId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        Chore chore = findChore(householdId, choreId);

        User target = chore.getCurrentUser();
        if (target == null) {
            throw new BadRequestException("This chore has nobody whose turn it is");
        }
        if (target.getId().equals(caller.getId())) {
            throw new BadRequestException("It is your turn");
        }

        Instant cooldownStart = Instant.now().minus(REMINDER_COOLDOWN);
        if (reminderRepository
                .findFirstByChoreIdAndSenderIdAndTargetIdAndSentAtAfterOrderBySentAtDesc(
                        choreId, caller.getId(), target.getId(), cooldownStart)
                .isPresent()) {
            throw new TooManyRequestsException("You already reminded this person about this chore in the last 24 hours");
        }

        ChoreReminder reminder = new ChoreReminder(chore, caller, target);
        reminderRepository.save(reminder);
        eventPublisher.publishEvent(new ChoreReminderSentEvent(reminder.getId()));

        return new ChoreReminderResponse(choreId, target.getId(), reminder.getSentAt().plus(REMINDER_COOLDOWN));
    }

    // Called when a member leaves, is removed, or when an account is deleted (in that household).
    // Removes the person from every rotation of the household and renumbers the rest 0..n-1.
    // If they were the current person, the next one in line (wrapping) takes the turn. If nobody is left,
    // the chore has no current person (null) until someone is added by editing it.
    public void removeFromRotations(Long householdId, Long userId) {
        Map<Long, List<ChoreRotationMember>> rowsByChore = new HashMap<>();
        for (ChoreRotationMember row : rotationRepository.findByChoreHouseholdIdOrderByChoreIdAscPositionAsc(householdId)) {
            rowsByChore.computeIfAbsent(row.getChore().getId(), id -> new ArrayList<>()).add(row);
        }

        for (List<ChoreRotationMember> rows : rowsByChore.values()) {
            int index = indexOfUser(rows, userId);
            if (index < 0) {
                continue;
            }
            Chore chore = rows.get(0).getChore();
            boolean wasCurrent = chore.getCurrentUser() != null && chore.getCurrentUser().getId().equals(userId);
            if (wasCurrent) {
                // Taken from the full list (before removal): the person after the leaving one, wrapping around.
                User next = rows.size() == 1 ? null : rows.get((index + 1) % rows.size()).getUser();
                chore.setCurrentUser(next);
            }

            ChoreRotationMember leaving = rows.get(index);
            List<ChoreRotationMember> remaining = new ArrayList<>(rows);
            remaining.remove(leaving);
            rotationRepository.delete(leaving);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setPosition(i);
            }
        }
    }

    // Called by HouseholdService.purgeHousehold before the household row is deleted (foreign keys).
    public void deleteForHousehold(Long householdId) {
        reminderRepository.deleteByChoreHouseholdId(householdId);
        completionRepository.deleteByChoreHouseholdId(householdId);
        rotationRepository.deleteByChoreHouseholdId(householdId);
        choreRepository.deleteByHouseholdId(householdId);
    }

    // ---------- Rules and helpers ----------

    // Who takes the turn after an edit. An explicit currentUserId must be in the new rotation. Without it,
    // the current person is kept if still in the rotation; otherwise the first person in the rotation takes over.
    private User pickCurrentAfterUpdate(Chore chore, List<User> rotation, Long requestedCurrentUserId) {
        if (requestedCurrentUserId != null) {
            return rotation.stream()
                    .filter(user -> user.getId().equals(requestedCurrentUserId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("currentUserId must be one of the people in the rotation"));
        }
        Long oldCurrentId = chore.getCurrentUser() == null ? null : chore.getCurrentUser().getId();
        return rotation.stream()
                .filter(user -> user.getId().equals(oldCurrentId))
                .findFirst()
                .orElse(rotation.get(0));
    }

    // Checks the ids and returns the users in turn order. Everyone must be an ACTIVE member of the household.
    private List<User> resolveRotation(Long householdId, List<Long> userIds) {
        if (new HashSet<>(userIds).size() != userIds.size()) {
            throw new BadRequestException("A person can appear only once in the rotation");
        }
        // Members who left are not in this map, so they are rejected like any other stranger.
        Map<Long, User> activeUsers = membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(Membership::getUser)
                .collect(Collectors.toMap(User::getId, user -> user));

        List<User> rotation = new ArrayList<>();
        for (Long userId : userIds) {
            User user = activeUsers.get(userId);
            if (user == null) {
                throw new BadRequestException("Everyone in the rotation must be an active member");
            }
            rotation.add(user);
        }
        return rotation;
    }

    // The rotation member right after the caller. If the caller is not in the rotation, the one right after
    // the current person. If nobody can be found, the first person in the rotation.
    private static User nextInRotation(List<ChoreRotationMember> rows, User caller, User currentUser) {
        if (rows.isEmpty()) {
            return null;
        }
        int anchor = indexOfUser(rows, caller.getId());
        if (anchor < 0 && currentUser != null) {
            anchor = indexOfUser(rows, currentUser.getId());
        }
        if (anchor < 0) {
            return rows.get(0).getUser();
        }
        return rows.get((anchor + 1) % rows.size()).getUser();
    }

    private static int indexOfUser(List<ChoreRotationMember> rows, Long userId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getUser().getId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    private List<ChoreRotationMember> saveRotation(Chore chore, List<User> rotation) {
        List<ChoreRotationMember> rows = new ArrayList<>();
        for (int i = 0; i < rotation.size(); i++) {
            rows.add(rotationRepository.save(new ChoreRotationMember(chore, rotation.get(i), i)));
        }
        return rows;
    }

    // The caller's own reminder status for this chore. Null when they can remind now, or when it is their
    // own turn (a reminder is not possible then, so there is no waiting time to show).
    private Instant reminderAvailableAt(Chore chore, User caller) {
        User target = chore.getCurrentUser();
        if (target == null || target.getId().equals(caller.getId())) {
            return null;
        }
        return reminderRepository
                .findFirstByChoreIdAndSenderIdAndTargetIdAndSentAtAfterOrderBySentAtDesc(
                        chore.getId(), caller.getId(), target.getId(), Instant.now().minus(REMINDER_COOLDOWN))
                .map(reminder -> reminder.getSentAt().plus(REMINDER_COOLDOWN))
                .orElse(null);
    }

    private ChoreResponse toResponse(Chore chore, List<ChoreRotationMember> rotationRows, User caller) {
        List<ChoreResponse.MemberItem> rotation = rotationRows.stream()
                .map(row -> new ChoreResponse.MemberItem(row.getUser().getId(), row.getUser().getName()))
                .toList();
        List<ChoreResponse.CompletionItem> recent = completionRepository
                .findTop5ByChoreIdOrderByDoneAtDescIdDesc(chore.getId()).stream()
                .map(completion -> new ChoreResponse.CompletionItem(
                        completion.getDoneBy().getName(), completion.getDoneAt()))
                .toList();
        User current = chore.getCurrentUser();
        return new ChoreResponse(
                chore.getId(),
                chore.getName(),
                rotation,
                current == null ? null : current.getId(),
                current == null ? null : current.getName(),
                recent,
                reminderAvailableAt(chore, caller));
    }

    private Chore findChore(Long householdId, Long choreId) {
        return choreRepository.findByIdAndHouseholdId(choreId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Chore not found: " + choreId));
    }

    // The request is validated with @NotBlank, which is stricter than trim(); this catches the rest.
    private static String requireName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Name is required");
        }
        return name;
    }

    private static List<Long> idsOfRows(List<ChoreRotationMember> rows) {
        return rows.stream().map(row -> row.getUser().getId()).toList();
    }

    private static List<Long> idsOfUsers(List<User> users) {
        return users.stream().map(User::getId).toList();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
