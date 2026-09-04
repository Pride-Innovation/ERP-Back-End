/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.user.User;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.*;

/**
 * The one place that answers "whose records may I touch, and where".
 *
 * <p>Holding a permission and being allowed to use it on a particular record are different
 * questions, and until now the application only asked the first. {@code UPDATE_ASSET} admitted the
 * caller to the endpoint and nothing afterwards looked at whose asset it was — a branch officer
 * could edit another branch's asset by id, having merely been unable to find it through the
 * listing. This service is the second question.
 *
 * <h2>How reach is decided</h2>
 * Three inputs, in order:
 *
 * <ol>
 *   <li><b>A multiplier.</b> {@code VIEW_ALL_BRANCH_ASSETS}, {@code MANAGE_ALL_BRANCH_ASSETS} and
 *       their siblings widen a permission the holder already has; alone they grant nothing. This is
 *       what a unit confers, and it is the only route to ALL for a write.</li>
 *   <li><b>The dashboard scope ladder</b>, capped at BRANCH. {@code DASH_SCOPE_BRANCH} means the
 *       holder is trusted with their whole duty station; {@code DASH_SCOPE_SELF} confines them to
 *       their own records. Reusing it rather than inventing a parallel SELF/BRANCH vocabulary means
 *       one thing to configure and one answer to give a user who asks why they see what they
 *       see.</li>
 *   <li><b>SELF</b>, when nothing above applies. Branch isolation is the default; reaching past it
 *       is something someone granted deliberately.</li>
 * </ol>
 *
 * <p>{@code DASH_SCOPE_ALL} widens VIEW to ALL but never MANAGE. Without that, a user whose
 * dashboard totals every branch would click into the assets page and find a fraction of what the
 * widget just counted; with a write it would be a licence nobody granted. Read follows the figures,
 * write waits for the explicit multiplier.
 *
 * <h2>Two vocabularies, on purpose</h2>
 * {@code DASH_SCOPE_*} governs summarised figures; the multipliers govern records and actions. They
 * are separate for the reason recorded in CLAUDE.md — borrowing a read permission to decide how much
 * of the estate someone sees summarised is what gave a branch officer the whole branch's totals. This
 * service is where the two meet, and the meeting happens once, here.
 *
 * <h2>Silent scoping versus refusal</h2>
 * {@link #resolveBranch} narrows a listing quietly: asking for "the assets" with no branch named
 * should return yours, not an error. {@link #requireCanView} and {@link #requireCanManage} refuse,
 * because naming a record that is not yours is a deliberate act and quietly substituting your own
 * would answer a question nobody asked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessScopeService {

    private final CurrentUserService currentUserService;

    /**
     * The signed-in user, or null.
     *
     * <p>Wrapped because {@code CurrentUserService.getCurrentUser()} reads the ambient HTTP request
     * and throws when there is none — a scheduled job, a seeder, a test. A resolver that threw
     * outside a web request would be unusable from any of them.
     */
    private User currentUserOrNull() {
        try {
            return currentUserService.getCurrentUser();
        } catch (RuntimeException e) {
            log.debug("No bound request or authenticated user while resolving access scope");
            return null;
        }
    }

    private Set<String> heldAuthorities() {
        User user = currentUserOrNull();
        if (user == null) return Set.of();
        return user.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }

    /**
     * How far the current user may reach for this subject and action.
     *
     * <p>The super administrator needs no special case: {@code DataInitializer} reconciles that role
     * against every permission on startup, including the multipliers, so it arrives here holding
     * them like anyone else.
     */
    public DashboardScope scopeFor(ScopeSubject subject, ScopeAction action) {
        Set<String> held = heldAuthorities();

        if (held.contains(subject.multiplierFor(action))) {
            return DashboardScope.ALL;
        }

        // The legacy cross-branch switch, honoured for reads so nobody who could see the bank
        // yesterday is narrowed today. It was never a write permission and does not become one.
        if (action == ScopeAction.VIEW
                && (held.contains(VIEW_ALL_BRANCHES) || held.contains(DASH_SCOPE_ALL))) {
            return DashboardScope.ALL;
        }

        if (held.contains(DASH_SCOPE_BRANCH)) return DashboardScope.BRANCH;
        if (held.contains(DASH_SCOPE_SELF)) return DashboardScope.SELF;

        // No explicit scope. Fall back to the signal that governed reach before this model existed —
        // a duty station plus an organisational read permission meant the branch — so a role nobody
        // has configured yet is not narrowed on the day this deploys.
        boolean readsOrgData = held.contains(READ_ASSET) || held.contains(READ_REQUEST)
                || held.contains(READ_STORE) || held.contains(READ_INVENTORY);
        return (readsOrgData && currentUserBranchId() != null)
                ? DashboardScope.BRANCH
                : DashboardScope.SELF;
    }

    public Long currentUserBranchId() {
        User user = currentUserOrNull();
        return (user != null && user.getBranch() != null) ? user.getBranch().getId() : null;
    }

    public Long currentUserId() {
        User user = currentUserOrNull();
        return user != null ? user.getId() : null;
    }

    /**
     * The branch a listing should be filtered on.
     *
     * @param requestedBranchId the branch the caller asked for, or null
     * @return a branch id to filter by, or {@code null} meaning no branch restriction
     * @throws AccessDeniedException when the caller named a branch they may not see
     */
    public Long resolveBranch(ScopeSubject subject, Long requestedBranchId) {
        if (scopeFor(subject, ScopeAction.VIEW) == DashboardScope.ALL) {
            return requestedBranchId;   // null here legitimately means every branch
        }

        Long own = currentUserBranchId();
        if (requestedBranchId != null && !requestedBranchId.equals(own)) {
            throw new AccessDeniedException("You can only view data for your own branch.");
        }
        return own;
    }

    /**
     * The user whose own records a listing should be limited to, or {@code null} for no such limit.
     *
     * <p>Non-null only at SELF. This is what lets an officer keep {@code READ_ASSET} — so they can
     * open the detail page of an asset they hold — while the listing shows only what is theirs.
     */
    public Long resolveOwner(ScopeSubject subject) {
        return scopeFor(subject, ScopeAction.VIEW) == DashboardScope.SELF ? currentUserId() : null;
    }

    /** Refuses unless the caller may read this particular record. */
    public void requireCanView(ScopeSubject subject, Long recordBranchId, Long recordOwnerId) {
        require(subject, ScopeAction.VIEW, recordBranchId, recordOwnerId,
                "You do not have access to this record.");
    }

    /** Refuses unless the caller may change this particular record. */
    public void requireCanManage(ScopeSubject subject, Long recordBranchId, Long recordOwnerId) {
        require(subject, ScopeAction.MANAGE, recordBranchId, recordOwnerId,
                "You can only make changes to records at your own branch.");
    }

    /** Whether the caller may change this record, without throwing. For shaping a response. */
    public boolean canManage(ScopeSubject subject, Long recordBranchId, Long recordOwnerId) {
        return permits(scopeFor(subject, ScopeAction.MANAGE), recordBranchId, recordOwnerId);
    }

    /** Whether the caller may read this record, without throwing. */
    public boolean canView(ScopeSubject subject, Long recordBranchId, Long recordOwnerId) {
        return permits(scopeFor(subject, ScopeAction.VIEW), recordBranchId, recordOwnerId);
    }

    /**
     * As {@link #requireCanView}, for a record that touches several branches at once.
     *
     * <p>A movement is the case this exists for: it has a source and a destination, and either end
     * may be a store's location or a person's duty station. The caller is in reach if <em>any</em> of
     * them is theirs, which is what the movements listing has always done — a guard that answered
     * differently from the listing would show someone a row they could not then open.
     */
    public void requireCanViewAny(ScopeSubject subject, Collection<Long> recordBranchIds) {
        requireAny(subject, ScopeAction.VIEW, recordBranchIds,
                "You do not have access to this record.");
    }

    /** As {@link #requireCanManage}, for a record that touches several branches at once. */
    public void requireCanManageAny(ScopeSubject subject, Collection<Long> recordBranchIds) {
        requireAny(subject, ScopeAction.MANAGE, recordBranchIds,
                "You can only act on records that touch your own branch.");
    }

    private void requireAny(ScopeSubject subject, ScopeAction action,
                            Collection<Long> recordBranchIds, String message) {
        DashboardScope scope = scopeFor(subject, action);

        // No branch on any end — the same "unassigned record" case permits() allows, for the same
        // reason: it must not become invisible and unfixable to everyone but a cross-branch holder.
        Set<Long> known = recordBranchIds == null ? Set.of()
                : recordBranchIds.stream().filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        if (known.isEmpty()) {
            if (!permits(scope, null, null)) throw new AccessDeniedException(message);
            return;
        }

        boolean anyInReach = known.stream().anyMatch(branchId -> permits(scope, branchId, null));
        if (!anyInReach) throw new AccessDeniedException(message);
    }

    private void require(ScopeSubject subject, ScopeAction action,
                         Long recordBranchId, Long recordOwnerId, String message) {
        if (!permits(scopeFor(subject, action), recordBranchId, recordOwnerId)) {
            throw new AccessDeniedException(message);
        }
    }

    /**
     * The rule itself.
     *
     * <p>A record with no branch on it — an unassigned asset, a draft — is treated as in reach. The
     * alternative is that such a record becomes invisible and unfixable to everyone but a
     * cross-branch holder, which is how the nullable-association bugs in this codebase have
     * repeatedly presented.
     */
    private boolean permits(DashboardScope scope, Long recordBranchId, Long recordOwnerId) {
        if (scope == DashboardScope.ALL) return true;

        if (scope == DashboardScope.BRANCH) {
            Long own = currentUserBranchId();
            return recordBranchId == null || recordBranchId.equals(own);
        }

        // SELF: your own record, or one belonging to nobody in particular.
        Long me = currentUserId();
        if (recordOwnerId != null) {
            return recordOwnerId.equals(me);
        }
        Long own = currentUserBranchId();
        return recordBranchId == null || recordBranchId.equals(own);
    }
}
