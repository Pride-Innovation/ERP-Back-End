package pride.bank.erp.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalStepRepository stepRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> findAll() {
        return workflowRepository.findAllByOrderByPriorityAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApprovalWorkflowDTO findById(Long id) {
        return toDTO(findWorkflow(id));
    }

    @Transactional
    public ApprovalWorkflowDTO create(ApprovalWorkflowDTO dto) {
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .assetTypes(loadAssetTypes(dto.getAssetTypeIds()))
                .branchId(dto.getBranchId())
                .branchScope(dto.getBranchScope() != null ? dto.getBranchScope() : WorkflowBranchScope.ALL)
                .appliesTo(dto.getAppliesTo() != null ? dto.getAppliesTo() : WorkflowPurpose.BOTH)
                .active(dto.isActive())
                .priority(dto.getPriority() > 0 ? dto.getPriority() : 100)
                .requesterRoles(loadRoles(dto.getRequesterRoleIds()))
                .build();

        ApprovalWorkflow saved = workflowRepository.save(workflow);
        addSteps(saved, dto.getSteps());
        return toDTO(workflowRepository.findById(saved.getId()).orElseThrow());
    }

    @Transactional
    public ApprovalWorkflowDTO update(Long id, ApprovalWorkflowDTO dto) {
        ApprovalWorkflow workflow = findWorkflow(id);
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setAssetTypes(loadAssetTypes(dto.getAssetTypeIds()));
        workflow.setBranchId(dto.getBranchId());
        workflow.setBranchScope(dto.getBranchScope() != null ? dto.getBranchScope() : WorkflowBranchScope.ALL);
        workflow.setAppliesTo(dto.getAppliesTo() != null ? dto.getAppliesTo() : WorkflowPurpose.BOTH);
        workflow.setActive(dto.isActive());
        workflow.setPriority(dto.getPriority() > 0 ? dto.getPriority() : 100);
        workflow.setRequesterRoles(loadRoles(dto.getRequesterRoleIds()));

        // Replace all steps
        workflow.getSteps().clear();
        workflowRepository.save(workflow);
        addSteps(workflow, dto.getSteps());

        return toDTO(workflowRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(Long id) {
        ApprovalWorkflow workflow = findWorkflow(id);
        workflowRepository.delete(workflow);
    }

    @Transactional
    public ApprovalWorkflowDTO toggleActive(Long id) {
        ApprovalWorkflow workflow = findWorkflow(id);
        workflow.setActive(!workflow.isActive());
        return toDTO(workflowRepository.save(workflow));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ApprovalWorkflow findWorkflow(Long id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow " + id + " not found"));
    }

    private Set<AssetType> loadAssetTypes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<AssetType> loaded = assetTypeRepository.findAllById(ids);
        if (loaded.size() != ids.stream().distinct().count()) {
            throw new NotFoundException("One or more assetTypeIds not found: " + ids);
        }
        return new HashSet<>(loaded);
    }

    private Set<Role> loadRoles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Role> loaded = roleRepository.findAllById(ids);
        if (loaded.size() != ids.stream().distinct().count()) {
            throw new NotFoundException("One or more role ids not found: " + ids);
        }
        return new HashSet<>(loaded);
    }

    private Unit loadUnit(Long id) {
        if (id == null) return null;
        return unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Unit " + id + " not found"));
    }

    /**
     * A GROUP_EMAIL step must resolve to an address: either a selected unit (preferred) or a
     * legacy free-text group email. Rejecting an empty one at save time prevents a request from
     * silently stranding at runtime (currentUnit null → nobody's inbox matches).
     */
    private void validateGroupStep(ApprovalStepDTO dto, Unit unit) {
        if (dto.getApproverType() != StepApproverType.GROUP_EMAIL) return;
        boolean hasGroupEmail = dto.getGroupEmail() != null && !dto.getGroupEmail().isBlank();
        if (unit == null && !hasGroupEmail) {
            throw new BadRequestException(
                    "Step '" + dto.getStepName() + "': a Group Email step must have a unit selected.");
        }
        if (unit != null && (unit.getGroupEmail() == null || unit.getGroupEmail().isBlank())) {
            throw new BadRequestException(
                    "Step '" + dto.getStepName() + "': the selected unit '" + unit.getName()
                            + "' has no group email. Set one on the Units page first.");
        }
    }

    private void addSteps(ApprovalWorkflow workflow, List<ApprovalStepDTO> stepDTOs) {
        if (stepDTOs == null || stepDTOs.isEmpty()) return;
        int order = 1;
        for (ApprovalStepDTO dto : stepDTOs) {
            Unit unit = loadUnit(dto.getUnitId());
            validateGroupStep(dto, unit);
            ApprovalStep step = ApprovalStep.builder()
                    .workflow(workflow)
                    .stepOrder(dto.getStepOrder() > 0 ? dto.getStepOrder() : order)
                    .stepName(dto.getStepName())
                    .stepType(dto.getStepType() != null ? dto.getStepType() : StepType.REQUEST_APPROVAL)
                    .approverType(dto.getApproverType())
                    .approverSubject(dto.getApproverSubject() != null ? dto.getApproverSubject() : ApproverSubject.REQUESTER)
                    .unit(unit)
                    .groupEmail(dto.getGroupEmail())
                    .specificUserId(dto.getSpecificUserId())
                    .notifyGroupEmail(dto.getNotifyGroupEmail())
                    .notifyGroupSource(dto.getNotifyGroupSource() != null ? dto.getNotifyGroupSource() : NotifyGroupSource.STATIC)
                    .notifyOnCompletion(dto.getNotifyOnCompletion() != null ? new HashSet<>(dto.getNotifyOnCompletion()) : new HashSet<>())
                    .optional(dto.isOptional())
                    .escalationHours(dto.getEscalationHours())
                    .skipIfRequesterRoles(loadRoles(dto.getSkipIfRequesterRoleIds()))
                    .build();
            workflow.getSteps().add(step);
            order++;
        }
        workflowRepository.save(workflow);
    }

    private ApprovalWorkflowDTO toDTO(ApprovalWorkflow w) {
        List<Long> assetTypeIds = new ArrayList<>();
        List<String> assetTypeNames = new ArrayList<>();
        if (w.getAssetTypes() != null) {
            for (AssetType at : w.getAssetTypes()) {
                assetTypeIds.add(at.getId());
                assetTypeNames.add(at.getName());
            }
        }

        List<Long> requesterRoleIds = new ArrayList<>();
        List<String> requesterRoleNames = new ArrayList<>();
        if (w.getRequesterRoles() != null) {
            for (Role r : w.getRequesterRoles()) {
                requesterRoleIds.add(r.getId());
                requesterRoleNames.add(r.getName());
            }
        }

        return ApprovalWorkflowDTO.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .assetTypeIds(assetTypeIds)
                .assetTypeNames(assetTypeNames)
                .branchId(w.getBranchId())
                .branchScope(w.getBranchScope())
                .appliesTo(w.getAppliesTo() != null ? w.getAppliesTo() : WorkflowPurpose.BOTH)
                .active(w.isActive())
                .priority(w.getPriority())
                .requesterRoleIds(requesterRoleIds)
                .requesterRoleNames(requesterRoleNames)
                .steps(w.getSteps().stream().map(this::toStepDTO).collect(Collectors.toList()))
                .build();
    }

    private ApprovalStepDTO toStepDTO(ApprovalStep s) {
        List<Long> skipIds = new ArrayList<>();
        if (s.getSkipIfRequesterRoles() != null) {
            for (Role r : s.getSkipIfRequesterRoles()) {
                skipIds.add(r.getId());
            }
        }
        return ApprovalStepDTO.builder()
                .id(s.getId())
                .stepOrder(s.getStepOrder())
                .stepName(s.getStepName())
                .stepType(s.getStepType())
                .approverType(s.getApproverType())
                .approverSubject(s.getApproverSubject() != null ? s.getApproverSubject() : ApproverSubject.REQUESTER)
                .unitId(s.getUnit() != null ? s.getUnit().getId() : null)
                .unitName(s.getUnit() != null ? s.getUnit().getName() : null)
                .groupEmail(s.getGroupEmail())
                .specificUserId(s.getSpecificUserId())
                .notifyGroupEmail(s.getNotifyGroupEmail())
                .notifyGroupSource(s.getNotifyGroupSource() != null ? s.getNotifyGroupSource() : NotifyGroupSource.STATIC)
                .notifyOnCompletion(s.getNotifyOnCompletion() != null ? new HashSet<>(s.getNotifyOnCompletion()) : new HashSet<>())
                .optional(s.isOptional())
                .escalationHours(s.getEscalationHours())
                .skipIfRequesterRoleIds(skipIds)
                .build();
    }
}
