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
import pride.bank.erp.user.User;

import static pride.bank.erp.helper.Constants.VIEW_ALL_BRANCHES;

/**
 * Decides which branch's data the signed-in user may see.
 *
 * <p>One rule, applied the same way everywhere: a user holding {@link
 * pride.bank.erp.helper.Constants#VIEW_ALL_BRANCHES} sees every branch; everyone else sees only the
 * branch they belong to. The permission is granted and revoked like any other, so Head Office staff
 * who need a national view get it and branch staff do not.
 *
 * <p>Two behaviours, deliberately different:
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

    /** Whether the current user may look beyond their own branch. */
    public boolean canViewAllBranches() {
        User user = currentUserService.getCurrentUser();
        if (user == null) return false;
        return user.getAuthorities().stream()
                .anyMatch(a -> VIEW_ALL_BRANCHES.equals(a.getAuthority()));
    }

    /** The current user's own branch, or null when they have none on record. */
    public Long currentUserBranchId() {
        User user = currentUserService.getCurrentUser();
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

        Long own = currentUserBranchId();
        if (own == null) {
            throw new AccessDeniedException(
                    "Your account is not attached to a branch, so branch data cannot be shown. "
                            + "Ask an administrator to set your branch.");
        }

        if (requestedBranchId != null && !requestedBranchId.equals(own)) {
            throw new AccessDeniedException(
                    "You can only view data for your own branch.");
        }
        return own;
    }

    /** Asserts the caller may see a specific branch's data. */
    public void requireAccessTo(Long branchId) {
        if (branchId == null || canViewAllBranches()) return;

        Long own = currentUserBranchId();
        if (own == null || !own.equals(branchId)) {
            throw new AccessDeniedException("You can only view data for your own branch.");
        }
    }
}
