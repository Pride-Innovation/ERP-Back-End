package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.config.seed.SeederStateRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;
import pride.bank.erp.workflow.ApprovalWorkflowRepository;
import pride.bank.erp.workflow.ApproverSubject;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflow.WorkflowBranchScope;
import pride.bank.erp.workflow.WorkflowPurpose;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approval workflows seeded into a new database ({@link WorkflowSeeder}).
 *
 * <p>Deliberately <b>not</b> compared against the live workflows: those are edited in Settings, and
 * a test pinning them would fail the moment somebody does the job Settings exists for. The copy
 * was checked against the live rows once, when the seeder was written. What is pinned here stays
 * true: every name resolves, the Phase 3 shapes, how a workflow is saved, and that the seed writes
 * only into an empty table.
 */
@SpringBootTest
@Transactional
class WorkflowSeederTest {

    @Autowired WorkflowSeeder seeder;
    @Autowired ApprovalWorkflowRepository workflowRepository;
    @Autowired SeederStateRepository seederStateRepository;

    private WorkflowSeeder target() {
        return AopTestUtils.getUltimateTargetObject(seeder);
    }

    private Map<String, ApprovalWorkflow> built() {
        WorkflowSeeder.Built built = target().build();
        assertThat(built.problems()).as("every category, unit and role the definitions name exists").isEmpty();
        return built.workflows().stream().collect(Collectors.toMap(ApprovalWorkflow::getName, Function.identity()));
    }

    private static Set<String> categories(ApprovalWorkflow w) {
        return w.getAssetTypes().stream().map(AssetType::getName).collect(Collectors.toSet());
    }

    private static List<StepType> types(ApprovalWorkflow w) {
        return w.getSteps().stream().map(ApprovalStep::getStepType).toList();
    }

    @Test
    void everyDefinitionResolvesAgainstTheSeededReferenceData() {
        assertThat(built()).hasSize(WorkflowSeeder.definitions().size()).hasSize(6);
    }

    @Test
    void eachCategoryHasExactlyOneWorkflowPerScopeAndPurpose() {
        // Otherwise the resolver's choice between two would come down to priority, silently.
        Map<String, ApprovalWorkflow> all = built();
        for (WorkflowBranchScope scope : List.of(WorkflowBranchScope.HEAD_OFFICE, WorkflowBranchScope.BRANCH)) {
            for (WorkflowPurpose purpose : List.of(WorkflowPurpose.PERSONAL, WorkflowPurpose.RESTOCK)) {
                Map<String, Long> counts = all.values().stream()
                        .filter(w -> w.getBranchScope() == scope)
                        .filter(w -> w.getAppliesTo() == purpose || w.getAppliesTo() == WorkflowPurpose.BOTH)
                        .flatMap(w -> categories(w).stream())
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                assertThat(counts.values()).as(scope + " / " + purpose).allMatch(n -> n == 1);
            }
        }
    }

    @Test
    void stationeryAtABranchIsIssuedFromTheBranchStore() {
        Map<String, ApprovalWorkflow> all = built();
        assertThat(categories(all.get("Branch · Admin-handled"))).doesNotContain("Stationery");

        ApprovalWorkflow store = all.get("Branch · Store-handled");
        assertThat(categories(store)).containsExactly("Stationery");
        assertThat(store.getAppliesTo()).isEqualTo(WorkflowPurpose.PERSONAL);
        assertThat(types(store)).as("no Acknowledge step").doesNotContain(StepType.ACKNOWLEDGE_REQUEST);

        ApprovalStep issue = store.getSteps().stream().filter(s -> s.getStepType() == StepType.ISSUANCE).findFirst().orElseThrow();
        assertThat(issue.getApproverType()).isEqualTo(StepApproverType.BOM);
        assertThat(issue.getUnit()).as("issued at the branch, not by a unit").isNull();

        ApprovalStep approve = store.getSteps().get(store.getSteps().indexOf(issue) + 1);
        assertThat(approve.getStepType()).isEqualTo(StepType.REQUEST_APPROVAL);
        assertThat(approve.getApproverSubject()).isEqualTo(ApproverSubject.ISSUER);
        assertThat(approve.getApproverType()).isEqualTo(StepApproverType.BRANCH_MANAGER);
    }

    @Test
    void aRestockIsApprovedByTheBranchManagerAndFulfilledByTheAdminUnit() {
        ApprovalWorkflow restock = built().get("Branch · Store restock");
        assertThat(restock.getAppliesTo()).isEqualTo(WorkflowPurpose.RESTOCK);
        assertThat(restock.getBranchScope()).isEqualTo(WorkflowBranchScope.BRANCH);
        assertThat(categories(restock)).containsExactlyInAnyOrderElementsOf(WorkflowSeeder.RESTOCK_CATEGORIES);
        assertThat(types(restock)).containsExactly(StepType.REQUEST_APPROVAL, StepType.ACKNOWLEDGE_REQUEST,
                StepType.ISSUANCE, StepType.REQUEST_APPROVAL, StepType.ACKNOWLEDGE_RECEIPT);
        assertThat(restock.getSteps().get(0).getApproverType()).isEqualTo(StepApproverType.BRANCH_MANAGER);
        assertThat(restock.getSteps().get(1).getUnit().getRoles()).extracting(Role::getName).contains(WorkflowSeeder.ADMIN_UNIT);
        assertThat(restock.getSteps().get(2).getUnit().getRoles()).extracting(Role::getName).contains(WorkflowSeeder.ADMIN_UNIT);
        assertThat(restock.getSteps().get(3).getApproverSubject()).isEqualTo(ApproverSubject.ISSUER);
    }

    @Test
    void theBranchAdminAndInfrastructureWorkflowsDoNotTakeRestocks() {
        // Same specificity, lower priority number: left at BOTH they would win every restock.
        Map<String, ApprovalWorkflow> all = built();
        assertThat(all.get("Branch · Admin-handled").getAppliesTo()).isEqualTo(WorkflowPurpose.PERSONAL);
        assertThat(all.get("Branch · Infrastructure-handled").getAppliesTo()).isEqualTo(WorkflowPurpose.PERSONAL);
    }

    @Test
    void stepsAreNumberedFromOneAndNameNoPerson() {
        for (ApprovalWorkflow w : built().values()) {
            assertThat(w.getSteps()).extracting(ApprovalStep::getStepOrder)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, w.getSteps().size()).boxed().toList());
            assertThat(w.getSteps()).allMatch(s -> s.getSpecificUserId() == null);
            assertThat(w.getSteps()).filteredOn(s -> s.getApproverType() == StepApproverType.GROUP_EMAIL)
                    .as(w.getName() + ": a group step routes to a unit").allMatch(s -> s.getUnit() != null);
        }
    }

    @Test
    void noSeededStepCarriesAFixedMailbox() {
        // Units mail their own mailbox; everyone else is looked up when the email is sent. A fixed
        // CC is read only for a Static CC, so none may be Static either, or its CC would go nowhere.
        assertThat(built().values()).flatMap(ApprovalWorkflow::getSteps)
                .allMatch(s -> s.getGroupEmail() == null && s.getNotifyGroupEmail() == null)
                .noneMatch(s -> s.getNotifyGroupSource() == pride.bank.erp.workflow.NotifyGroupSource.STATIC);
    }

    @Test
    void aDatabaseThatHasWorkflowsIsLeftAlone() {
        long before = workflowRepository.count();
        if (before == 0) {
            System.out.println("workflow seeder test: NO workflows in this database - the leave-alone case is unexercised");
            return;
        }
        ReflectionTestUtils.invokeMethod(target(), "seedIfEmpty");
        assertThat(workflowRepository.count()).isEqualTo(before);
    }

    @Test
    void theBuiltWorkflowsSaveWithTheirSteps() {
        long before = workflowRepository.count();
        for (ApprovalWorkflow w : target().build().workflows()) {
            ReflectionTestUtils.invokeMethod(target(), "saveWithSteps", w);
        }
        workflowRepository.flush();
        assertThat(workflowRepository.count()).isEqualTo(before + 6);
        ApprovalWorkflow restock = workflowRepository.findAll().stream()
                .filter(w -> w.getName().equals("Branch · Store restock") && w.getAppliesTo() == WorkflowPurpose.RESTOCK)
                .reduce((a, b) -> b).orElseThrow();
        assertThat(restock.getSteps()).hasSize(5).allMatch(s -> s.getId() != null && s.getWorkflow().getId().equals(restock.getId()));
    }

    @Test
    void theSeedRanOnceOnThisDatabase() {
        assertThat(seederStateRepository.existsById(WorkflowSeeder.MARKER)).isTrue();
    }
}
