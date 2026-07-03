/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the ordered approval chain for a request by walking the requester's title
 * {@code reportsTo} ladder and intersecting it with the workflow's role whitelist.
 *
 * <p>This is the single source of truth for approval routing — used both at runtime
 * (the workflow engine) and for the inline preview / "Test workflow" panel, so the two
 * never drift.
 *
 * <p>Rules implemented (see the design discussion):
 * <ol>
 *   <li>The workflow's {@code REQUEST_APPROVAL} steps define an <b>ordered role whitelist</b>;
 *       the {@code reportsTo} ladder is the source of truth for who actually sits above the
 *       requester and whether a tier exists at all.</li>
 *   <li>A whitelisted role on the ladder → its in-branch holder approves; a whitelisted role
 *       absent from the ladder → skipped (no fabrication, no group fallback).</li>
 *   <li>Duplicate role on the ladder → only the <b>nearest</b> rung approves.</li>
 *   <li>The <b>final approver</b> is the workflow's most-senior whitelisted role; the walk
 *       stops once it is reached, and never climbs past the branch ceiling
 *       (Branch Manager for branches, HoD for Head Office).</li>
 *   <li><b>Absence</b> (active leave, no acting user, or a vacant tier): a <b>Manager</b> routes
 *       to the managers' group mailbox; Supervisor / BOM / Branch Manager <b>skip up</b> to the
 *       next whitelisted tier.</li>
 *   <li><b>No-intersection fallback</b>: if no whitelisted role is on the ladder, fall back to
 *       the requester's immediate {@code reportsTo} holder, walking up only to the branch ceiling.</li>
 *   <li>A rung resolving to the requester themselves, or matching the step's
 *       {@code skipIfRequesterRoles}, is skipped.</li>
 * </ol>
 *
 * <p>Only {@code REQUEST_APPROVAL} steps are governed by {@link #resolve}. The single
 * issuance-approval step is resolved one rung at a time via {@link #resolveSingleApprover}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApproverChainResolver {

    static final String ROLE_SUPERVISOR = "SUPERVISOR";
    static final String ROLE_MANAGER = "MANAGER";
    static final String ROLE_DEPT_HEAD = "HEAD_OF_DEPARTMENT";
    static final String ROLE_BOM = "BRANCH_OPERATIONS_MANAGER";
    static final String ROLE_BRANCH_MANAGER = "BRANCH_MANAGER";

    private static final int LADDER_DEPTH_GUARD = 30;

    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;

    /** Full resolution result: the active approval chain plus the tiers that were bypassed (for the preview). */
    @Data
    @AllArgsConstructor
    public static class Outcome {
        private List<ResolvedApprover> stops;
        private List<SkippedTier> skipped;
    }

    /** A whitelisted approval tier that did not produce an approver, with a human reason. */
    @Data
    @AllArgsConstructor
    public static class SkippedTier {
        private String roleName;
        private StepApproverType approverType;
        private String stepName;
        private String reason;
    }

    /**
     * Resolves the ordered approval chain for {@code requester} under {@code workflow}.
     * Returns an empty list when the workflow declares no ladder-role approval steps,
     * or when the requester has no branch / title to resolve against.
     */
    public List<ResolvedApprover> resolve(User requester, ApprovalWorkflow workflow) {
        return resolveDetailed(requester, workflow).getStops();
    }

    /**
     * Like {@link #resolve} but also reports the whitelisted tiers that were bypassed and why —
     * used by the workflow preview / tester to explain the resolved path.
     */
    public Outcome resolveDetailed(User requester, ApprovalWorkflow workflow) {
        List<ResolvedApprover> chain = new ArrayList<>();
        List<SkippedTier> skipped = new ArrayList<>();
        if (requester == null || requester.getBranch() == null || workflow == null) {
            return new Outcome(chain, skipped);
        }
        Long branchId = requester.getBranch().getId();
        if (branchId == null) {
            return new Outcome(chain, skipped);
        }

        // 1. Ordered role whitelist + the step that carries each role's config.
        Map<String, ApprovalStep> whitelist = buildWhitelist(workflow);
        if (whitelist.isEmpty()) {
            return new Outcome(chain, skipped); // no ladder-role approval steps
        }
        String terminalRole = lastKey(whitelist);
        String ceilingRole = ceilingRole(requester);

        // 2. Walk the reportsTo ladder, emitting stops for whitelisted rungs (nearest-only),
        //    stopping at the terminal role or the branch ceiling.
        Set<String> handled = new HashSet<>();
        for (Title rung : ladder(requester)) {
            String roleName = roleName(rung);
            if (roleName == null) continue;
            String key = roleName.toUpperCase();

            if (whitelist.containsKey(key) && !handled.contains(key)) {
                processRung(rung, key, whitelist.get(key), requester, branchId, chain, skipped);
                handled.add(key);
                if (key.equalsIgnoreCase(terminalRole)) break;
            }
            if (roleName.equalsIgnoreCase(ceilingRole)) break;
        }

        // Whitelisted tiers never reached on this requester's line.
        whitelist.forEach((role, step) -> {
            if (!handled.contains(role)) {
                skipped.add(new SkippedTier(role, step.getApproverType(), step.getStepName(),
                        "No " + human(role) + " on the reporting line"));
            }
        });

        // 3. No whitelisted role intersected the ladder → branch-capped fallback.
        if (chain.isEmpty()) {
            ResolvedApprover fallback = resolveFallback(requester, branchId, ceilingRole, whitelist.get(terminalRole));
            if (fallback != null) {
                chain.add(fallback);
            }
        }
        return new Outcome(chain, skipped);
    }

    /**
     * Resolves a single approver for a non-chain step (e.g. the issuance-approval step that
     * routes to the requester's manager/supervisor). Returns the nearest matching rung on the
     * ladder, a managers' group stop when a manager is unavailable, or null when nothing resolves.
     */
    public ResolvedApprover resolveSingleApprover(User requester, StepApproverType type) {
        if (requester == null || requester.getBranch() == null || requester.getBranch().getId() == null) {
            return null;
        }
        Long branchId = requester.getBranch().getId();

        if (type == StepApproverType.DIRECT_SUPERVISOR) {
            List<Title> ladder = ladder(requester);
            if (ladder.isEmpty()) return null;
            User eff = effectiveApprover(holderInBranch(ladder.get(0), branchId));
            return (eff != null && !sameUser(eff, requester))
                    ? ResolvedApprover.builder().approver(eff).note("Immediate supervisor").build()
                    : null;
        }

        String role = ladderRoleFor(type);
        if (role == null) return null;
        String ceilingRole = ceilingRole(requester);
        for (Title rung : ladder(requester)) {
            String rn = roleName(rung);
            if (rn == null) continue;
            if (role.equalsIgnoreCase(rn)) {
                User eff = effectiveApprover(holderInBranch(rung, branchId));
                if (eff != null && !sameUser(eff, requester)) {
                    return ResolvedApprover.builder().roleName(role).approver(eff)
                            .note("Resolved from reporting line").build();
                }
                if (ROLE_MANAGER.equals(role)) {
                    String group = managersGroupEmail(requester);
                    if (group != null && !group.isBlank()) {
                        return ResolvedApprover.builder().roleName(role).groupEmail(group)
                                .note("Manager unavailable — routed to managers' group").build();
                    }
                }
                return null;
            }
            if (ceilingRole.equalsIgnoreCase(rn)) break;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-rung resolution
    // ─────────────────────────────────────────────────────────────────────────

    private void processRung(Title rung, String roleName, ApprovalStep step, User requester,
                             Long branchId, List<ResolvedApprover> chain, List<SkippedTier> skipped) {
        // Requester-role skip (e.g. don't make a Manager approve their own request).
        if (matchesSkipRole(step, requester)) {
            skipped.add(new SkippedTier(roleName, step.getApproverType(), step.getStepName(),
                    "Requester's role is exempt from this step"));
            return;
        }

        User effective = effectiveApprover(holderInBranch(rung, branchId));

        if (effective != null) {
            if (sameUser(effective, requester)) {
                skipped.add(new SkippedTier(roleName, step.getApproverType(), step.getStepName(),
                        "Would resolve to the requester"));
                return;
            }
            chain.add(ResolvedApprover.builder()
                    .sourceStep(step).roleName(roleName).approver(effective)
                    .fallback(false).note("Resolved from reporting line").build());
            return;
        }

        // No available individual (vacant tier or on leave with no acting user).
        if (ROLE_MANAGER.equals(roleName)) {
            String group = managersGroupEmail(requester);
            if (group != null && !group.isBlank()) {
                chain.add(ResolvedApprover.builder()
                        .sourceStep(step).roleName(roleName).groupEmail(group)
                        .fallback(false).note("Manager unavailable — routed to managers' group").build());
            } else {
                skipped.add(new SkippedTier(roleName, step.getApproverType(), step.getStepName(),
                        "Manager unavailable and no managers' group configured"));
            }
            return;
        }
        // Supervisor / BOM / Branch Manager unavailable → skip up to the next whitelisted tier.
        skipped.add(new SkippedTier(roleName, step.getApproverType(), step.getStepName(),
                "On leave or unavailable — skipped to the next tier"));
    }

    /**
     * No-intersection fallback: walk the ladder from the immediate {@code reportsTo} upward,
     * returning the first available in-branch holder, capped at the branch ceiling.
     */
    private ResolvedApprover resolveFallback(User requester, Long branchId, String ceilingRole, ApprovalStep terminalStep) {
        for (Title rung : ladder(requester)) {
            User effective = effectiveApprover(holderInBranch(rung, branchId));
            if (effective != null && !sameUser(effective, requester)) {
                return ResolvedApprover.builder()
                        .sourceStep(terminalStep).roleName(null).approver(effective)
                        .fallback(true).note("Fallback: no configured approver on the reporting line").build();
            }
            if (ceilingRole.equalsIgnoreCase(roleName(rung))) {
                break; // never climb past the branch ceiling
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Whitelist + ceiling
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, ApprovalStep> buildWhitelist(ApprovalWorkflow workflow) {
        Map<String, ApprovalStep> whitelist = new LinkedHashMap<>();
        if (workflow.getSteps() == null) return whitelist;
        workflow.getSteps().stream()
                .filter(s -> s.getStepType() == StepType.REQUEST_APPROVAL)
                // Steps that resolve against the issuer are not part of the requester's reporting
                // chain — exclude them so an issuer step (e.g. issuer's Manager) can't consume the
                // requester's slot for the same role.
                .filter(s -> s.getApproverSubject() != ApproverSubject.ISSUER)
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .forEach(step -> {
                    String role = ladderRoleFor(step.getApproverType());
                    if (role != null) {
                        whitelist.putIfAbsent(role, step);
                    }
                });
        return whitelist;
    }

    /** Maps a hierarchy approver type to the title role it targets, or null if it isn't a ladder role. */
    private String ladderRoleFor(StepApproverType type) {
        if (type == null) return null;
        return switch (type) {
            case SUPERVISOR -> ROLE_SUPERVISOR;
            case MANAGER -> ROLE_MANAGER;
            case DEPT_HEAD -> ROLE_DEPT_HEAD;
            case BOM -> ROLE_BOM;
            case BRANCH_MANAGER -> ROLE_BRANCH_MANAGER;
            default -> null; // DIRECT_SUPERVISOR / GROUP_EMAIL / ADMIN / SPECIFIC_USER / REQUESTER
        };
    }

    /** The highest tier a request may climb to: Branch Manager at branches, HoD at Head Office. */
    private String ceilingRole(User requester) {
        boolean headOffice = requester.getBranch() != null && requester.getBranch().isHeadOffice();
        return headOffice ? ROLE_DEPT_HEAD : ROLE_BRANCH_MANAGER;
    }

    private String lastKey(Map<String, ApprovalStep> map) {
        String last = null;
        for (String k : map.keySet()) last = k;
        return last;
    }

    private String human(String role) {
        return switch (role) {
            case ROLE_SUPERVISOR -> "Supervisor";
            case ROLE_MANAGER -> "Manager";
            case ROLE_DEPT_HEAD -> "Head of Department";
            case ROLE_BOM -> "Branch Operations Manager";
            case ROLE_BRANCH_MANAGER -> "Branch Manager";
            default -> role;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ladder + holder resolution
    // ─────────────────────────────────────────────────────────────────────────

    /** Titles strictly above the requester (their reportsTo chain), cycle- and depth-guarded. */
    private List<Title> ladder(User requester) {
        List<Title> out = new ArrayList<>();
        if (requester.getTitle() == null) return out;
        Title current = requester.getTitle().getReportsTo();
        Set<Long> seen = new HashSet<>();
        int guard = 0;
        while (current != null && guard++ < LADDER_DEPTH_GUARD) {
            if (current.getId() != null && !seen.add(current.getId())) break;
            out.add(current);
            current = current.getReportsTo();
        }
        return out;
    }

    /** First user holding {@code title} in the requester's branch, or null. */
    private User holderInBranch(Title title, Long branchId) {
        if (title == null || title.getId() == null || branchId == null) return null;
        List<User> holders = userRepository.findByTitleIdAndBranchId(title.getId(), branchId);
        return (holders == null || holders.isEmpty()) ? null : holders.get(0);
    }

    /**
     * Returns the user who can actually act for a rung: the holder, the acting user if the holder
     * is on leave, or null when the holder is absent (on leave with no acting user) or the tier is vacant.
     */
    private User effectiveApprover(User holder) {
        if (holder == null || holder.getId() == null) return null;
        Optional<Leave> leave = leaveRepository.findActiveLeaveByUser(holder.getId());
        if (leave.isPresent()) {
            return leave.get().getActingUser(); // null when on leave with no delegate → absent
        }
        return holder;
    }

    private String roleName(Title title) {
        if (title == null || title.getRole() == null) return null;
        return title.getRole().getName();
    }

    /** Managers' group: department group at Head Office, branch group otherwise. */
    private String managersGroupEmail(User requester) {
        boolean headOffice = requester.getBranch() != null && requester.getBranch().isHeadOffice();
        if (headOffice) {
            return requester.getDepartment() != null ? requester.getDepartment().getManagersGroupEmail() : null;
        }
        return requester.getBranch() != null ? requester.getBranch().getManagersGroupEmail() : null;
    }

    private boolean matchesSkipRole(ApprovalStep step, User requester) {
        Set<Role> skipRoles = step.getSkipIfRequesterRoles();
        if (skipRoles == null || skipRoles.isEmpty()) return false;
        Set<Long> requesterRoleIds = requester.getEffectiveRoleIds();
        return skipRoles.stream().anyMatch(r -> r != null && requesterRoleIds.contains(r.getId()));
    }

    private boolean sameUser(User a, User b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }
}
