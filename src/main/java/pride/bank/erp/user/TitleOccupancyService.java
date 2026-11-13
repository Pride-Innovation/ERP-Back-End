/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.exception.TitleOccupiedException;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The one rule for single-holder titles: at a location (a branch, Head Office counting as one) a
 * title marked {@link Title#isSingleHolder() single holder} may have only one active holder.
 *
 * <p>It exists for approval routing. Routing finds "the holder of this title at the requester's
 * branch", and with two Branch Managers there was no rule for which one got the request.
 *
 * <p><b>Who counts as holding a title</b> is {@link UserRepository#ACTIVE_HOLDER}: not blocked, and
 * enabled or on leave. Blocking or disabling an account frees the title; going on leave does not.
 *
 * <p>Called wherever an account comes to hold a title at a location: creating, editing (only when
 * the title, location or account state actually changes, so nobody is blocked from editing their own
 * record), importing, enabling, unblocking and verifying. Each refusal is a 409 naming the holder.
 */
@Service
@RequiredArgsConstructor
public class TitleOccupancyService {

    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final TitleRepository titleRepository;

    /** One location where a single-holder title has more than one active holder. */
    public record Conflict(Long titleId, String titleName, Long branchId, String branchName,
                           List<Holder> holders) {}

    public record Holder(Long id, String name, String email) {}

    /** The Java form of {@link UserRepository#ACTIVE_HOLDER}, for one account already loaded. */
    public boolean isActiveHolder(User user) {
        if (user == null || user.isBlocked()) return false;
        if (user.isEnabled()) return true;
        return user.getId() != null && leaveRepository.findActiveLeaveByUser(user.getId()).isPresent();
    }

    /**
     * Refuses giving {@code title} at {@code branch} to an account when the title is single-holder
     * and another active account already holds it there. {@code excludingUserId} is the account being
     * changed, which never counts against itself.
     *
     * <p>Locks the title's row for the caller's transaction, so two concurrent assignments queue.
     *
     * <p>Deliberately not {@code @Transactional}: a refusal thrown across a transactional proxy marks
     * the caller's transaction rollback-only even when the caller catches it, which
     * {@code verifyAccount} does. Every caller that saves runs in its own transaction already.
     */
    public void requireAssignable(Title title, Branch branch, Long excludingUserId) {
        if (title == null || title.getId() == null || branch == null || branch.getId() == null) return;
        if (!title.isSingleHolder()) return;

        titleRepository.lockRow(title.getId());
        List<User> others = userRepository.findActiveHoldersInBranch(title.getId(), branch.getId()).stream()
                .filter(u -> !Objects.equals(u.getId(), excludingUserId))
                .toList();
        if (others.isEmpty()) return;

        User holder = others.get(0);
        throw new TitleOccupiedException(String.format(
                "%s already has an active %s: %s. A %s can only be held by one active account per location. "
                        + "Disable or block that account, or move it to another title or location, first.",
                branch.getName(), title.getName(), describe(holder), title.getName()));
    }

    /**
     * Refuses turning a title into a single-holder title while any location already has more than
     * one active holder of it, naming every such location - so the rule is never switched on while
     * it is already broken.
     */
    public void requireCanBecomeSingleHolder(Title title) {
        List<Conflict> conflicts = group(userRepository.findActiveHoldersOfTitle(title.getId()));
        if (conflicts.isEmpty()) return;

        String where = conflicts.stream()
                .map(c -> c.branchName() + " (" + c.holders().stream().map(Holder::name)
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("; "));
        throw new TitleOccupiedException(String.format(
                "%s cannot be limited to one holder per location yet: %s. "
                        + "Disable or block the extra accounts so only one remains at each location, then try again.",
                title.getName(), where));
    }

    /** Every location where a single-holder title has more than one active holder. */
    public List<Conflict> allConflicts() {
        return group(userRepository.findActiveHoldersOfSingleHolderTitles());
    }

    /** Groups holders by title and location (the queries order them so) and keeps the duplicates. */
    private List<Conflict> group(List<User> holders) {
        Map<String, List<User>> byPlace = new LinkedHashMap<>();
        for (User u : holders) {
            byPlace.computeIfAbsent(u.getTitle().getId() + ":" + u.getBranch().getId(), k -> new ArrayList<>()).add(u);
        }
        List<Conflict> out = new ArrayList<>();
        for (List<User> place : byPlace.values()) {
            if (place.size() < 2) continue;
            User first = place.get(0);
            out.add(new Conflict(first.getTitle().getId(), first.getTitle().getName(),
                    first.getBranch().getId(), first.getBranch().getName(),
                    place.stream().map(u -> new Holder(u.getId(), name(u), u.getEmail())).toList()));
        }
        return out;
    }

    private static String name(User u) {
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isEmpty() ? "User #" + u.getId() : full;
    }

    private static String describe(User u) {
        return name(u) + (u.getEmail() == null ? "" : " (" + u.getEmail() + ")");
    }
}
