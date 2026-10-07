/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocking;

import pride.bank.erp.branch.Branch;

import java.util.Set;

/**
 * Where something may be stocked: Head Office, all branches, and specific branches.
 *
 * <p>Held by a category, and by a commodity that overrides its category. One shape on both, so one
 * evaluator ({@link StockingRuleService#allows}) answers for either and the two cannot drift.
 */
public interface StockingRule {

    boolean isStockAtHeadOffice();

    /** Every branch other than Head Office - including one created tomorrow. */
    boolean isStockAtAllBranches();

    /** Named branches, consulted only while {@link #isStockAtAllBranches()} is off. */
    Set<Branch> getStockingBranches();
}
