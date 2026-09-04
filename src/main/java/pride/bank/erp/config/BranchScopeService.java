/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeAction;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.dashboard.DashboardScope;

/**
 * Decides which branch's inventory and store data the signed-in user may see.
 *
 * <h2>A façade over {@link AccessScopeService}, not a second model</h2>
 * This used to hold the rule itself: {@code VIEW_ALL_BRANCHES} sees everything, everyone else sees
 * their own branch. That was the third way the application expressed reach, alongside the dashboard
 * scopes and a switch on role names, and three answers to one question is how they came to disagree.
 *
 * <p>It now delegates every decision to the one resolver, for the {@link ScopeSubject#INVENTORY}
 * subject. Kept as a façade rather than deleted because seventeen call sites across the inventory
 * and store controllers read naturally in its vocabulary — "may this caller see that branch" — and
 * rewriting them all to say the same thing in more words would risk more than it clarified.
 *
 * <p>What changed by delegating: a unit's {@code VIEW_ALL_BRANCH_INVENTORY} multiplier now widens
 * these listings, an explicitly granted {@code DASH_SCOPE_*} is honoured, and
 * {@code VIEW_ALL_BRANCHES} still works for anyone who held it. Nobody who could see something
 * yesterday sees less today.
 *
 * <h2>Two behaviours, deliberately different</h2>
 * <ul>
 *   <li>{@link #resolve(Long)} — <b>silently scopes</b> a listing. Asking for "the stock position"
 *       with no branch named should return your own branch, not an empty page or an error.</li>
 *   <li>{@link #requireAccessTo(Long)} — <b>refuses</b>. Naming another branch explicitly is a
 *       different act from omitting the parameter, and quietly rewriting it to your own would show
 *       one branch's figures under another's name.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchScopeService {

    private final CurrentUserService currentUserService;
    private final AccessScopeService accessScopeService;

    /** Whether the current user may look beyond their own branch at inventory and stores. */
    public boolean canViewAllBranches() {
        return accessScopeService.scopeFor(ScopeSubject.INVENTORY, ScopeAction.VIEW)
                == DashboardScope.ALL;
    }

    /** The current user's own branch, or null when they have none on record. */
    public Long currentUserBranchId() {
        var user = currentUserService.getCurrentUser();
        return (user != null && user.getBranch() != null) ? user.getBranch().getId() : null;
    }

    /**
     * The branch a listing should actually be scoped to.
     *
     * @param requestedBranchId what the client asked for; may be null
     * @return the branch id to filter by, or {@code null} meaning "no restriction"
     * @throws AccessDeniedException if the caller named a branch they may not see
     */
    public Long resolve(Long requestedBranchId) {
        if (canViewAllBranches()) {
            return requestedBranchId;      // null here legitimately means "all branches"
        }

        // Checked before delegating so the caller gets the message that says what to do about it.
        // AccessScopeService returns null for a branchless user, which downstream reads as "every
        // branch" — the opposite of what a user with no duty station should get.
        Long own = currentUserBranchId();
        if (own == null) {
            throw new AccessDeniedException(
                    "Your account is not attached to a branch, so branch data cannot be shown. "
                            + "Ask an administrator to set your branch.");
        }

        return accessScopeService.resolveBranch(ScopeSubject.INVENTORY, requestedBranchId);
    }

    /** Asserts the caller may see a specific branch's data. */
    public void requireAccessTo(Long branchId) {
        if (branchId == null) return;
        accessScopeService.requireCanView(ScopeSubject.INVENTORY, branchId, null);
    }

    /**
     * Asserts the caller may <em>change</em> a specific branch's stores or stock.
     *
     * <p>Separate from {@link #requireAccessTo} because seeing a branch's stock position and
     * creating a store in that branch are different acts, and the multipliers grant them apart:
     * {@code VIEW_ALL_BRANCH_INVENTORY} is a reporting right, not a licence to reorganise another
     * branch's stores.
     */
    public void requireCanChange(Long branchId) {
        if (branchId == null) return;
        accessScopeService.requireCanManage(ScopeSubject.INVENTORY, branchId, null);
    }
}
