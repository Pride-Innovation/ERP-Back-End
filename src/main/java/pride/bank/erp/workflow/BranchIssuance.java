/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.workflow;

import java.util.List;

/**
 * Workflows that issue at the branch, from the branch's own store (Phase 3, WORKFLOW.md).
 *
 * <p>There is no flag for it: a workflow issues at the branch when its Issue step - or its
 * issuance-approval step (a request approval resolved against the issuer) - is routed to the branch's
 * BOM or Branch Manager. Routed to a unit or a manager instead, it issues from wherever that unit is.
 *
 * <p>One place for the rule, because the engine's routing, its no-skip rule and the choice of
 * workflow for a request all ask it, and three copies would drift.
 */
public final class BranchIssuance {

    private BranchIssuance() {}

    /** An Issue or issuance-approval step routed to the branch's BOM or Branch Manager. */
    public static boolean isBranchPersonIssuanceStep(ApprovalStep step) {
        boolean branchRole = step.getApproverType() == StepApproverType.BOM
                || step.getApproverType() == StepApproverType.BRANCH_MANAGER;
        boolean issuing = step.getStepType() == StepType.ISSUANCE
                || (step.getStepType() == StepType.REQUEST_APPROVAL
                    && step.getApproverSubject() == ApproverSubject.ISSUER);
        return branchRole && issuing;
    }

    /** Whether this workflow issues at the requester's branch: its Issue step goes to the BOM or BM. */
    public static boolean issuesAtTheBranch(ApprovalWorkflow workflow) {
        return workflow.getSteps() != null && workflow.getSteps().stream()
                .anyMatch(s -> s.getStepType() == StepType.ISSUANCE && isBranchPersonIssuanceStep(s));
    }

    /**
     * The candidate workflows a request may actually travel through, in the repository's order.
     *
     * <p>A store restock can never travel through a workflow that issues at the branch: the goods
     * would be issued out of the very store they are meant to fill, which {@code IssuanceService}
     * refuses, so the request would sit at its Issue step forever. Measured when this was written:
     * request 140, a Gulu restock, matched Branch · Store-handled because that workflow had been set
     * to "Applies to: Both" and outranked the restock workflow on priority. Skipping such workflows
     * here lets the restock workflow take it, or - if there is none - refuses the request when it is
     * raised, which is the existing behaviour for a restock nothing serves.
     */
    public static List<ApprovalWorkflow> servable(List<ApprovalWorkflow> candidates, boolean restock) {
        if (!restock) return candidates;
        return candidates.stream().filter(w -> !issuesAtTheBranch(w)).toList();
    }
}
