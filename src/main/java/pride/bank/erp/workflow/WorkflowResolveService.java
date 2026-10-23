package pride.bank.erp.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves which workflow will run for a given (category, branch, requester) tuple
 * and computes per-step approver / fallback / skip metadata.
 *
 * <p>Used by both the inline request-form preview ("This goes to Jane Doe → …")
 * and the admin "Test workflow" panel. Approval routing is delegated to
 * {@link ApproverChainResolver} so the preview always matches what the engine will do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowResolveService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final LeaveRepository leaveRepository;
    private final ApproverChainResolver approverChainResolver;

    @Value("${application.admin.email:}")
    private String adminEmail;

    @Transactional(readOnly = true)
    public ResolvedApprovalWorkflowDTO resolve(Long categoryId, Long branchId, Long requesterId) {
        return resolve(categoryId, branchId, requesterId, false);
    }

    /** As above, for a personal request or a store restock - which can match different workflows. */
    @Transactional(readOnly = true)
    public ResolvedApprovalWorkflowDTO resolve(Long categoryId, Long branchId, Long requesterId, boolean restock) {
        AssetType category = assetTypeRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Asset category " + categoryId + " not found"));

        User requester = (requesterId != null)
                ? userRepository.findById(requesterId)
                .orElseThrow(() -> new NotFoundException("User " + requesterId + " not found"))
                : null;

        Branch branch = resolveBranch(branchId, requester);

        WorkflowBranchScope scope = branch != null && branch.isHeadOffice()
                ? WorkflowBranchScope.HEAD_OFFICE
                : (branch != null ? WorkflowBranchScope.BRANCH : WorkflowBranchScope.ALL);

        Set<Long> requesterRoleIds = (requester != null)
                ? requester.getEffectiveRoleIds()
                : Collections.emptySet();

        Long effectiveBranchId = branch != null ? branch.getId() : null;

        List<ApprovalWorkflow> candidates = workflowRepository.findActiveWorkflowsForRequest(
                effectiveBranchId, categoryId, scope, requesterRoleIds, WorkflowPurpose.of(restock));

        if (candidates.isEmpty()) {
            return ResolvedApprovalWorkflowDTO.builder()
                    .workflowId(null)
                    .workflowName(null)
                    .resolvedSteps(new ArrayList<>())
                    .noMatchReason(buildNoMatchReason(category, branch, requester))
                    .build();
        }

        ApprovalWorkflow chosen = candidates.get(0);

        return ResolvedApprovalWorkflowDTO.builder()
                .workflowId(chosen.getId())
                .workflowName(chosen.getName())
                .fulfilmentGroupEmail(category.getOwnerGroupEmail())
                .resolvedSteps(buildResolvedSteps(chosen, requester))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resolved-step assembly
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the resolved step list: the approval chain comes from {@link ApproverChainResolver}
     * (active stops + bypassed tiers), followed by the workflow's non-approval steps
     * (acknowledge / issuance / receipt) resolved as configured.
     */
    private List<ResolvedApprovalStepDTO> buildResolvedSteps(ApprovalWorkflow workflow, User requester) {
        List<ResolvedApprovalStepDTO> out = new ArrayList<>();
        List<ApprovalStep> steps = workflow.getSteps().stream()
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .toList();

        // No requester → labels only (spec: return labels + types, no per-user resolution).
        if (requester == null) {
            int order = 1;
            for (ApprovalStep step : steps) {
                out.add(ResolvedApprovalStepDTO.builder()
                        .stepOrder(order++)
                        .stepName(step.getStepName())
                        .stepType(step.getStepType())
                        .approverType(step.getApproverType())
                        .optional(step.isOptional())
                        .skipped(false)
                        .build());
            }
            return out;
        }

        int order = 1;
        ApproverChainResolver.Outcome outcome = approverChainResolver.resolveDetailed(requester, workflow);
        for (ResolvedApprover stop : outcome.getStops()) {
            out.add(approvalStop(stop, order++));
        }
        for (ApproverChainResolver.SkippedTier sk : outcome.getSkipped()) {
            out.add(skippedTier(sk, order++));
        }
        for (ApprovalStep step : steps) {
            if (step.getStepType() == StepType.REQUEST_APPROVAL) continue;
            out.add(postApprovalStep(step, requester, order++));
        }
        return out;
    }

    private ResolvedApprovalStepDTO approvalStop(ResolvedApprover stop, int order) {
        ApprovalStep step = stop.getSourceStep();
        ResolvedApprovalStepDTO.ResolvedApprovalStepDTOBuilder b = ResolvedApprovalStepDTO.builder()
                .stepOrder(order)
                .stepName(step != null ? step.getStepName() : "Approval")
                .stepType(StepType.REQUEST_APPROVAL)
                .approverType(step != null ? step.getApproverType() : null)
                .optional(step != null && step.isOptional())
                .skipped(false)
                .note(stop.getNote());
        if (stop.isGroup()) {
            b.approverEmail(stop.getGroupEmail());
        } else if (stop.getApprover() != null) {
            b.approverName(buildName(stop.getApprover())).approverEmail(stop.getApprover().getEmail());
        }
        return b.build();
    }

    private ResolvedApprovalStepDTO skippedTier(ApproverChainResolver.SkippedTier sk, int order) {
        return ResolvedApprovalStepDTO.builder()
                .stepOrder(order)
                .stepName(sk.getStepName())
                .stepType(StepType.REQUEST_APPROVAL)
                .approverType(sk.getApproverType())
                .skipped(true)
                .skipReason(sk.getReason())
                .build();
    }

    private ResolvedApprovalStepDTO postApprovalStep(ApprovalStep step, User requester, int order) {
        ResolvedApprovalStepDTO.ResolvedApprovalStepDTOBuilder b = ResolvedApprovalStepDTO.builder()
                .stepOrder(order)
                .stepName(step.getStepName())
                .stepType(step.getStepType())
                .approverType(step.getApproverType())
                .optional(step.isOptional())
                .skipped(false)
                .fallbackEmail(resolveCcEmail(step, requester));

        // Issuer-subject steps resolve against whoever issues the items, which isn't known when
        // previewing a request — surface that rather than resolving against the requester.
        if (step.getApproverSubject() == ApproverSubject.ISSUER) {
            return b.note("Resolves against the issuer's reporting line when the request is issued.").build();
        }

        switch (step.getApproverType()) {
            case GROUP_EMAIL -> {
                if (step.getUnit() != null) {
                    b.approverName(step.getUnit().getName())
                            .approverEmail(blankToNull(step.getUnit().getGroupEmail()));
                } else {
                    b.approverEmail(blankToNull(step.getGroupEmail()));
                }
            }
            case REQUESTER -> b.approverName(buildName(requester)).approverEmail(requester.getEmail());
            case ADMIN -> b.approverEmail(blankToNull(adminEmail));
            case SPECIFIC_USER -> {
                User u = (step.getSpecificUserId() != null)
                        ? userRepository.findById(step.getSpecificUserId()).orElse(null) : null;
                if (u != null) {
                    b.approverName(buildName(u)).approverEmail(u.getEmail());
                } else {
                    b.skipped(true).skipReason("Specific user not found");
                }
            }
            default -> {
                // Hierarchy approver on a non-chain step (e.g. issuance approval) — resolve one rung.
                ResolvedApprover one = approverChainResolver.resolveSingleApprover(requester, step.getApproverType());
                if (one == null) {
                    b.skipped(true).skipReason("No approver resolved on the reporting line");
                } else if (one.isGroup()) {
                    b.approverEmail(one.getGroupEmail()).note(one.getNote());
                } else {
                    b.approverName(buildName(one.getApprover())).approverEmail(one.getApprover().getEmail()).note(one.getNote());
                }
            }
        }
        return b.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CC / completion email resolution (also used by the runtime engine)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the actual CC email for a step at runtime, honouring
     * {@link ApprovalStep#getNotifyGroupSource()}. Returns {@code null} when no
     * CC should be sent. Used by both this resolver (for the request preview)
     * and the runtime email sender.
     */
    public String resolveCcEmail(ApprovalStep step, User requester) {
        NotifyGroupSource source = step.getNotifyGroupSource() != null
                ? step.getNotifyGroupSource()
                : NotifyGroupSource.STATIC;
        if (source == NotifyGroupSource.STATIC) {
            return blankToNull(step.getNotifyGroupEmail());
        }
        return resolveEmailForSource(source, requester);
    }

    /**
     * Resolves a single dynamic notification target to an email address, or
     * {@code null} if the target cannot be resolved. Used for completion
     * notifications and as the underlying resolver for {@link #resolveCcEmail}.
     * <p>{@code STATIC} returns {@code null} here (the caller must read the
     * step's literal email field directly).</p>
     */
    public String resolveEmailForSource(NotifyGroupSource source, User requester) {
        if (source == null) return null;
        switch (source) {
            case NONE:
            case STATIC:
                return null;
            case REQUESTER:
                return (requester != null) ? blankToNull(requester.getEmail()) : null;
            case REQUESTER_DIRECT_SUPERVISOR: {
                User supervisor = (requester != null) ? resolveDirectSupervisor(requester) : null;
                supervisor = substituteIfOnLeave(supervisor);
                return (supervisor != null) ? blankToNull(supervisor.getEmail()) : null;
            }
            case ADMIN:
                return blankToNull(adminEmail);
            case REQUESTER_MANAGERS_GROUP: {
                if (requester == null) return null;
                boolean atHeadOffice = requester.getBranch() != null && requester.getBranch().isHeadOffice();
                if (atHeadOffice) {
                    return (requester.getDepartment() != null)
                            ? blankToNull(requester.getDepartment().getManagersGroupEmail())
                            : null;
                }
                return (requester.getBranch() != null)
                        ? blankToNull(requester.getBranch().getManagersGroupEmail())
                        : null;
            }
            default:
                return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the requester's immediate supervisor: the holder of {@code title.reportsTo}
     * in the requester's branch. Used for the {@code REQUESTER_DIRECT_SUPERVISOR} notify target.
     */
    private User resolveDirectSupervisor(User requester) {
        if (requester.getTitle() == null || requester.getTitle().getReportsTo() == null) {
            return null;
        }
        Long supervisorTitleId = requester.getTitle().getReportsTo().getId();
        Long branchId = (requester.getBranch() != null) ? requester.getBranch().getId() : null;
        if (branchId == null) return null;

        List<User> supervisors = userRepository.findByTitleIdAndBranchId(supervisorTitleId, branchId);
        return supervisors.isEmpty() ? null : supervisors.get(0);
    }

    /**
     * If the resolved user is currently on leave and has assigned an acting user, route to the
     * acting user instead; otherwise return the candidate unchanged.
     */
    private User substituteIfOnLeave(User candidate) {
        if (candidate == null || candidate.getId() == null) return candidate;
        Optional<Leave> active = leaveRepository.findActiveLeaveByUser(candidate.getId());
        if (active.isPresent() && active.get().getActingUser() != null) {
            return active.get().getActingUser();
        }
        return candidate;
    }

    private Branch resolveBranch(Long branchId, User requester) {
        if (branchId != null) {
            return branchRepository.findById(branchId)
                    .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found"));
        }
        if (requester != null) {
            return requester.getBranch();
        }
        return null;
    }

    private String buildName(User user) {
        if (user == null) return null;
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null) sb.append(user.getFirstName());
        if (user.getLastName() != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(user.getLastName());
        }
        return sb.length() > 0 ? sb.toString() : user.getEmail();
    }

    private String buildNoMatchReason(AssetType category, Branch branch, User requester) {
        StringBuilder sb = new StringBuilder("No active workflow matches ");
        sb.append("category=").append(category.getName());
        if (branch != null) {
            sb.append(", branch=").append(branch.getName());
        }
        if (requester != null) {
            String roleNames = requester.getEffectiveRoleIds().isEmpty()
                    ? "(none)"
                    : describeRoles(requester);
            sb.append(", role=").append(roleNames);
        }
        return sb.toString();
    }

    private String describeRoles(User requester) {
        Set<String> names = new HashSet<>();
        if (requester.getTitle() != null && requester.getTitle().getRole() != null
                && requester.getTitle().getRole().getName() != null) {
            names.add(requester.getTitle().getRole().getName());
        }
        if (requester.getAdditionalRoles() != null) {
            for (Role r : requester.getAdditionalRoles()) {
                if (r != null && r.getName() != null) names.add(r.getName());
            }
        }
        return String.join("/", names);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
