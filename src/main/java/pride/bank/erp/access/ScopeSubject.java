/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.access;

import static pride.bank.erp.helper.Constants.*;

/**
 * The kinds of record whose reach can be widened beyond one branch.
 *
 * <p>Each subject names the pair of multiplier permissions that widen it. Keeping the names on the
 * enum rather than at each call site is the point: a check written as
 * {@code scope.scopeFor(ASSETS, MANAGE)} cannot accidentally consult the movements permission, and
 * adding a subject later is one entry here rather than a new branch in every service.
 *
 * <p>Deliberately coarse. Scoping by asset category, location or any other property of the record
 * was considered and deferred — branch is the only axis the bank needs answered today, and a
 * finer-grained model is far easier to add to this than to unpick from it.
 */
public enum ScopeSubject {

    ASSETS(VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS),
    REQUESTS(VIEW_ALL_BRANCH_REQUESTS, MANAGE_ALL_BRANCH_REQUESTS),
    MOVEMENTS(VIEW_ALL_BRANCH_MOVEMENTS, MANAGE_ALL_BRANCH_MOVEMENTS),
    INVENTORY(VIEW_ALL_BRANCH_INVENTORY, MANAGE_ALL_BRANCH_INVENTORY);

    private final String viewMultiplier;
    private final String manageMultiplier;

    ScopeSubject(String viewMultiplier, String manageMultiplier) {
        this.viewMultiplier = viewMultiplier;
        this.manageMultiplier = manageMultiplier;
    }

    /**
     * The permission that widens this subject for the given action.
     *
     * <p>Note that MANAGE asks for the manage multiplier <b>only</b>. Seeing every branch's assets
     * is not licence to edit them, and the pair exists so that reading and writing can be granted
     * apart — which is the ordinary case, not the exception.
     */
    public String multiplierFor(ScopeAction action) {
        return action == ScopeAction.MANAGE ? manageMultiplier : viewMultiplier;
    }
}
