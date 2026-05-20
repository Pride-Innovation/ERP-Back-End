package pride.bank.erp.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalStepRepository stepRepository;

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
                .assetTypeId(dto.getAssetTypeId())
                .branchId(dto.getBranchId())
                .branchScope(dto.getBranchScope() != null ? dto.getBranchScope() : WorkflowBranchScope.ALL)
                .active(dto.isActive())
                .priority(dto.getPriority() > 0 ? dto.getPriority() : 100)
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
        workflow.setAssetTypeId(dto.getAssetTypeId());
        workflow.setBranchId(dto.getBranchId());
        workflow.setBranchScope(dto.getBranchScope() != null ? dto.getBranchScope() : WorkflowBranchScope.ALL);
        workflow.setActive(dto.isActive());
        workflow.setPriority(dto.getPriority() > 0 ? dto.getPriority() : 100);

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

    private void addSteps(ApprovalWorkflow workflow, List<ApprovalStepDTO> stepDTOs) {
        if (stepDTOs == null || stepDTOs.isEmpty()) return;
        int order = 1;
        for (ApprovalStepDTO dto : stepDTOs) {
            ApprovalStep step = ApprovalStep.builder()
                    .workflow(workflow)
                    .stepOrder(dto.getStepOrder() > 0 ? dto.getStepOrder() : order)
                    .stepName(dto.getStepName())
                    .stepType(dto.getStepType() != null ? dto.getStepType() : StepType.REQUEST_APPROVAL)
                    .approverType(dto.getApproverType())
                    .groupEmail(dto.getGroupEmail())
                    .specificUserId(dto.getSpecificUserId())
                    .notifyGroupEmail(dto.getNotifyGroupEmail())
                    .optional(dto.isOptional())
                    .escalationHours(dto.getEscalationHours())
                    .build();
            workflow.getSteps().add(step);
            order++;
        }
        workflowRepository.save(workflow);
    }

    private ApprovalWorkflowDTO toDTO(ApprovalWorkflow w) {
        return ApprovalWorkflowDTO.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .assetTypeId(w.getAssetTypeId())
                .branchId(w.getBranchId())
                .branchScope(w.getBranchScope())
                .active(w.isActive())
                .priority(w.getPriority())
                .steps(w.getSteps().stream().map(this::toStepDTO).collect(Collectors.toList()))
                .build();
    }

    private ApprovalStepDTO toStepDTO(ApprovalStep s) {
        return ApprovalStepDTO.builder()
                .id(s.getId())
                .stepOrder(s.getStepOrder())
                .stepName(s.getStepName())
                .stepType(s.getStepType())
                .approverType(s.getApproverType())
                .groupEmail(s.getGroupEmail())
                .specificUserId(s.getSpecificUserId())
                .notifyGroupEmail(s.getNotifyGroupEmail())
                .optional(s.isOptional())
                .escalationHours(s.getEscalationHours())
                .build();
    }
}
