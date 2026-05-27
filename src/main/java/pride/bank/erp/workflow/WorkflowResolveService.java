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
import pride.bank.erp.title.Title;
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
 * and the admin "Test workflow" panel.
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

    @Value("${application.admin.email:}")
    private String adminEmail;

    @Transactional(readOnly = true)
    public ResolvedApprovalWorkflowDTO resolve(Long categoryId, Long branchId, Long requesterId) {
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
                effectiveBranchId, categoryId, scope, requesterRoleIds);

        if (candidates.isEmpty()) {
            return ResolvedApprovalWorkflowDTO.builder()
                    .workflowId(null)
                    .workflowName(null)
                    .resolvedSteps(new ArrayList<>())
                    .noMatchReason(buildNoMatchReason(category, branch, requester))
                    .build();
        }

        ApprovalWorkflow chosen = candidates.get(0);

        List<ResolvedApprovalStepDTO> resolved = new ArrayList<>();
        for (ApprovalStep step : chosen.getSteps()) {
            resolved.add(resolveStep(step, requester));
        }

        return ResolvedApprovalWorkflowDTO.builder()
                .workflowId(chosen.getId())
                .workflowName(chosen.getName())
                .fulfilmentGroupEmail(category.getOwnerGroupEmail())
                .resolvedSteps(resolved)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-step resolution
    // ─────────────────────────────────────────────────────────────────────────

    private ResolvedApprovalStepDTO resolveStep(ApprovalStep step, User requester) {
        ResolvedApprovalStepDTO.ResolvedApprovalStepDTOBuilder b = ResolvedApprovalStepDTO.builder()
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .stepType(step.getStepType())
                .approverType(step.getApproverType())
                .optional(step.isOptional());

        // Skip check (only meaningful when we have a requester)
        if (requester != null) {
            Role skipMatch = firstIntersectingRole(step.getSkipIfRequesterRoles(), requester.getEffectiveRoleIds());
            if (skipMatch != null) {
                return b.skipped(true)
                        .skipReason("Requester is a " + skipMatch.getName())
                        .build();
            }
            // Dynamic skip: SUPERVISOR step auto-skips when the officer has no supervisor in their chain
            if (step.getApproverType() == StepApproverType.SUPERVISOR
                    && resolveSupervisor(requester) == null) {
                return b.skipped(true)
                        .skipReason("Officer has no supervisor in their reporting chain")
                        .build();
            }
        }

        if (requester == null) {
            // Labels only: spec says return labels + skip flags, no per-user resolution.
            return b.skipped(false).build();
        }

        // Resolve approver based on type
        User approver = resolveApprover(step, requester);
        String approverName = (approver != null) ? buildName(approver) : null;
        String approverEmail = (approver != null) ? approver.getEmail() : step.getGroupEmail();
        String fallbackEmail = resolveFallbackEmail(step, requester);

        return b
                .approverName(approverName)
                .approverEmail(approverEmail)
                .fallbackEmail(fallbackEmail)
                .skipped(false)
                .build();
    }

    private User resolveApprover(ApprovalStep step, User requester) {
        User candidate = switch (step.getApproverType()) {
            case DIRECT_SUPERVISOR -> resolveDirectSupervisor(requester);
            case SUPERVISOR        -> resolveSupervisor(requester);
            case DEPT_HEAD -> (requester.getDepartment() != null)
                    ? requester.getDepartment().getHeadOfDepartment() : null;
            case BOM -> (requester.getBranch() != null)
                    ? requester.getBranch().getBranchOperationsManager() : null;
            case BRANCH_MANAGER -> (requester.getBranch() != null)
                    ? requester.getBranch().getBranchManager() : null;
            case ADMIN -> (adminEmail != null && !adminEmail.isBlank())
                    ? userRepository.findByEmail(adminEmail).orElse(null) : null;
            case SPECIFIC_USER -> (step.getSpecificUserId() != null)
                    ? userRepository.findById(step.getSpecificUserId()).orElse(null) : null;
            case GROUP_EMAIL -> null;
            case REQUESTER -> requester;
        };
        return substituteIfOnLeave(candidate);
    }

    /**
     * If the resolved approver is currently on leave and has assigned an acting
     * user, route to the acting user instead. Otherwise return the candidate
     * unchanged. Keeps the request flowing while the original approver is away
     * without losing the audit trail of who actually acted.
     */
    private User substituteIfOnLeave(User candidate) {
        if (candidate == null || candidate.getId() == null) return candidate;
        Optional<Leave> active = leaveRepository.findActiveLeaveByUser(candidate.getId());
        if (active.isPresent() && active.get().getActingUser() != null) {
            return active.get().getActingUser();
        }
        return candidate;
    }

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

    private User resolveSupervisor(User requester) {
        if (requester.getTitle() == null || requester.getTitle().getReportsTo() == null) return null;
        Title reportsToTitle = requester.getTitle().getReportsTo();
        if (reportsToTitle.getRole() == null) return null;
        if (!"SUPERVISOR".equalsIgnoreCase(reportsToTitle.getRole().getName())) return null;
        Long branchId = (requester.getBranch() != null) ? requester.getBranch().getId() : null;
        if (branchId == null) return null;
        List<User> candidates = userRepository.findByTitleIdAndBranchId(reportsToTitle.getId(), branchId);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private String resolveFallbackEmail(ApprovalStep step, User requester) {
        // 1) If the step explicitly opts into dynamic CC, honour that and stop.
        String dynamicCc = resolveCcEmail(step, requester);
        if (dynamicCc != null) {
            return dynamicCc;
        }
        // 2) Implicit fallback for dept-managed approvers (legacy behaviour).
        boolean deptManaged = step.getApproverType() == StepApproverType.DEPT_HEAD
                || step.getApproverType() == StepApproverType.DIRECT_SUPERVISOR
                || step.getApproverType() == StepApproverType.SUPERVISOR;
        if (deptManaged && requester.getDepartment() != null
                && requester.getDepartment().getManagersGroupEmail() != null
                && !requester.getDepartment().getManagersGroupEmail().isBlank()) {
            return requester.getDepartment().getManagersGroupEmail();
        }
        return null;
    }

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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

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

    private Role firstIntersectingRole(Set<Role> roles, Set<Long> roleIds) {
        if (roles == null || roles.isEmpty() || roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        for (Role role : roles) {
            if (role != null && role.getId() != null && roleIds.contains(role.getId())) {
                return role;
            }
        }
        return null;
    }

    private String buildName(User user) {
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
}
