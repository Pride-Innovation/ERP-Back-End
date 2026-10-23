/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;
import pride.bank.erp.workflow.ApprovalWorkflowRepository;
import pride.bank.erp.workflow.ApproverSubject;
import pride.bank.erp.workflow.NotifyGroupSource;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflow.WorkflowBranchScope;
import pride.bank.erp.workflow.WorkflowPurpose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pride.bank.erp.workflow.ApproverSubject.ISSUER;
import static pride.bank.erp.workflow.ApproverSubject.REQUESTER;
import static pride.bank.erp.workflow.NotifyGroupSource.ADMIN;
import static pride.bank.erp.workflow.NotifyGroupSource.REQUESTER_MANAGERS_GROUP;
import static pride.bank.erp.workflow.StepApproverType.BOM;
import static pride.bank.erp.workflow.StepApproverType.BRANCH_MANAGER;
import static pride.bank.erp.workflow.StepApproverType.DEPT_HEAD;
import static pride.bank.erp.workflow.StepApproverType.GROUP_EMAIL;
import static pride.bank.erp.workflow.StepApproverType.MANAGER;
import static pride.bank.erp.workflow.StepApproverType.SUPERVISOR;
import static pride.bank.erp.workflow.StepType.ACKNOWLEDGE_RECEIPT;
import static pride.bank.erp.workflow.StepType.ACKNOWLEDGE_REQUEST;
import static pride.bank.erp.workflow.StepType.ISSUANCE;
import static pride.bank.erp.workflow.StepType.REQUEST_APPROVAL;

/**
 * Seeds the approval workflows into a brand-new database, once.
 *
 * <h2>Why a seeder and not a Flyway migration</h2>
 * A workflow points at asset categories, units and roles, and on a new database every one of those
 * is created by a seeder - which runs <em>after</em> Flyway. A migration would find nothing to link
 * to, and naming ids instead would bake one environment's seed order into every other (trap #4).
 * So this runs last in {@code DataInitializer}, when all of them exist, and names everything by a
 * stable key: categories by name, units by the role they confer ({@code ADMIN_UNIT} /
 * {@code INFRA_UNIT} - units are renamed in Settings), roles by name. No step names a person.
 *
 * <h2>Only on a new database</h2>
 * Through {@link pride.bank.erp.config.seed.OneTimeSeed}, and even then only into an
 * <b>empty</b> workflow table. An existing environment already has its workflows - possibly edited
 * in Settings - so it gets the marker and nothing else. Once the marker is set nothing is ever
 * re-seeded, so deleting every workflow later does not bring these back, and an edit made in
 * Settings is never overwritten.
 *
 * <h2>Mailboxes come from configuration</h2>
 * Every step's group and CC address is {@code WORKFLOW_GROUP_EMAIL}, optional and blank by
 * default. A unit step mails its unit's own group mailbox first (the engine prefers it), and that
 * one is already configured, by {@code IT_TEAM_EMAIL_ADDRESS} and {@code ADMIN_TEAM_EMAIL_ADDRESS}
 * through {@link UnitSeeder}.
 *
 * <h2>Where the definitions came from</h2>
 * The four workflows that were live when this was written, copied step for step (including their
 * skip roles, notification sources and 48-hour escalation), plus the two Phase 3 added: the
 * branch store-handled Stationery workflow and the branch store-restock workflow. On the copied
 * ones three things changed by decision: Stationery moved from Branch · Admin-handled to the
 * store-handled workflow, the branch Admin and Infrastructure workflows serve personal requests
 * only (otherwise, by priority, they would capture restocks ahead of the restock workflow), and two
 * names lost a typo and a stray leading space. See {@code workflow.md}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowSeeder {

    /** Permanent. Changing this string re-runs the seed on any database whose workflow table is empty. */
    public static final String MARKER = "SEED_APPROVAL_WORKFLOWS";

    static final String ADMIN_UNIT = "ADMIN_UNIT";
    static final String INFRA_UNIT = "INFRA_UNIT";

    static final List<String> ADMIN_CATEGORIES = List.of(
            "Furniture", "Stationery", "Vehicle/Fleet", "Security", "Cleaning & Hygiene", "Healthcare",
            "Printing & Branding", "Building & Construction", "Kitchen & Groceries");
    static final List<String> INFRA_CATEGORIES = List.of(
            "Equipment", "Computers", "Financial & Banking Equipment");
    static final List<String> RESTOCK_CATEGORIES = List.of(
            "Cleaning & Hygiene", "Kitchen & Groceries", "Printing & Branding", "Stationery");

    private static final int ESCALATION_HOURS = 48;

    private final ApprovalWorkflowRepository workflowRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    /** An instance field on purpose: {@code @Value} on a static field injects nothing. */
    @Value("${workflow.seed.group-email:}")
    private String groupEmail;

    // ── definitions ─────────────────────────────────────────────────────────

    record StepDef(String name, StepType type, StepApproverType approver, ApproverSubject subject,
                   String unitRole, NotifyGroupSource notifySource, Set<NotifyGroupSource> onCompletion,
                   List<String> skipRoles) {}

    record WorkflowDef(String name, String description, WorkflowBranchScope scope, WorkflowPurpose purpose,
                       int priority, List<String> categories, List<StepDef> steps) {}

    private static StepDef step(String name, StepType type, StepApproverType approver, ApproverSubject subject,
                                String unitRole, NotifyGroupSource notify, Set<NotifyGroupSource> onCompletion,
                                String... skipRoles) {
        return new StepDef(name, type, approver, subject, unitRole, notify, onCompletion, List.of(skipRoles));
    }

    private static final Set<NotifyGroupSource> TELL_REQUESTER = Set.of(NotifyGroupSource.REQUESTER);
    private static final Set<NotifyGroupSource> NOBODY = Set.of();

    static List<WorkflowDef> definitions() {
        return List.of(
                new WorkflowDef("HO · Admin-handled",
                        "Head Office requests for categories the Admin Unit fulfils.",
                        WorkflowBranchScope.HEAD_OFFICE, WorkflowPurpose.BOTH, 10, ADMIN_CATEGORIES, List.of(
                        step("Supervisor Approval", REQUEST_APPROVAL, SUPERVISOR, REQUESTER, null, NotifyGroupSource.REQUESTER, TELL_REQUESTER,
                                "MANAGER", "HEAD_OF_DEPARTMENT", "SUPERVISOR"),
                        step("Manager Approval", REQUEST_APPROVAL, MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "HEAD_OF_DEPARTMENT"),
                        step("HOD Approval", REQUEST_APPROVAL, DEPT_HEAD, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "HEAD_OF_DEPARTMENT"),
                        step("Admin Acknowledgement", ACKNOWLEDGE_REQUEST, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Admin Issuance", ISSUANCE, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Approve Issuance", REQUEST_APPROVAL, MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Acknowledge Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, NotifyGroupSource.REQUESTER, NOBODY))),

                new WorkflowDef("HO · Infrastructure-handled",
                        "Head Office requests for categories the Infrastructure Unit acknowledges.",
                        WorkflowBranchScope.HEAD_OFFICE, WorkflowPurpose.BOTH, 11, INFRA_CATEGORIES, List.of(
                        step("Supervisor Approval", REQUEST_APPROVAL, SUPERVISOR, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "HEAD_OF_DEPARTMENT", "SUPERVISOR"),
                        step("Manager Approval", REQUEST_APPROVAL, MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "HEAD_OF_DEPARTMENT"),
                        step("HOD Approval", REQUEST_APPROVAL, DEPT_HEAD, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "HEAD_OF_DEPARTMENT"),
                        step("Infra Acknowledgement", ACKNOWLEDGE_REQUEST, GROUP_EMAIL, REQUESTER, INFRA_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Admin Issuance", ISSUANCE, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Approve Issuance", REQUEST_APPROVAL, MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Acknowledge Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, ADMIN, NOBODY))),

                new WorkflowDef("Branch · Admin-handled",
                        "Branch requests for categories the Admin Unit fulfils from Head Office. Stationery is handled by the branch store.",
                        WorkflowBranchScope.BRANCH, WorkflowPurpose.PERSONAL, 20,
                        ADMIN_CATEGORIES.stream().filter(c -> !c.equals("Stationery")).toList(), List.of(
                        step("Supervisor Approval", REQUEST_APPROVAL, SUPERVISOR, REQUESTER, null, NotifyGroupSource.REQUESTER, NOBODY,
                                "MANAGER", "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER", "SUPERVISOR"),
                        step("Manager Approval", REQUEST_APPROVAL, MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER"),
                        step("BOM Approval", REQUEST_APPROVAL, BOM, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER"),
                        step("BM Approval", REQUEST_APPROVAL, BRANCH_MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER"),
                        step("Admin Acknowledgement", ACKNOWLEDGE_REQUEST, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Admin Issuance", ISSUANCE, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Approve Issuance", REQUEST_APPROVAL, MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Requester Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, ADMIN, Set.of(REQUESTER_MANAGERS_GROUP)))),

                new WorkflowDef("Branch · Infrastructure-handled",
                        "Branch requests for categories the Infrastructure Unit acknowledges.",
                        WorkflowBranchScope.BRANCH, WorkflowPurpose.PERSONAL, 21, INFRA_CATEGORIES, List.of(
                        step("Supervisor Approval", REQUEST_APPROVAL, SUPERVISOR, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER", "SUPERVISOR"),
                        step("Manager Approval", REQUEST_APPROVAL, MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER"),
                        step("BOM Approval", REQUEST_APPROVAL, BOM, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER"),
                        step("BM Approval", REQUEST_APPROVAL, BRANCH_MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER"),
                        step("Infra Acknowledgement", ACKNOWLEDGE_REQUEST, GROUP_EMAIL, REQUESTER, INFRA_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Admin Issuance", ISSUANCE, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Issuance Approval", REQUEST_APPROVAL, MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, Set.of(NotifyGroupSource.REQUESTER, ADMIN)),
                        step("Acknowledge Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER))),

                // Phase 3: issued at the branch from the branch store. The BOM issues and the BM approves
                // the issuance; the engine swaps them when one of them asked (maker-checker), and the
                // request waits at the branch until the store holds the items. No Acknowledge step.
                new WorkflowDef("Branch · Store-handled",
                        "Branch Stationery requests issued from the branch's own store: the BOM issues and the BM approves the issuance.",
                        WorkflowBranchScope.BRANCH, WorkflowPurpose.PERSONAL, 22, List.of("Stationery"), List.of(
                        step("Supervisor Approval", REQUEST_APPROVAL, SUPERVISOR, REQUESTER, null, NotifyGroupSource.REQUESTER, NOBODY,
                                "MANAGER", "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER", "SUPERVISOR"),
                        step("Manager Approval", REQUEST_APPROVAL, MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "MANAGER", "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER"),
                        step("Branch Issuance", ISSUANCE, BOM, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Approve Issuance", REQUEST_APPROVAL, BRANCH_MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Acknowledge Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, ADMIN, Set.of(REQUESTER_MANAGERS_GROUP)))),

                // The BOM asks to restock the branch store, the BM approves, Head Office's Admin Unit
                // acknowledges and issues, the issuance is approved, and receipt is into the store.
                new WorkflowDef("Branch · Store restock",
                        "Restocking a branch's store from Head Office: raised by the BOM, approved by the BM, fulfilled by the Admin Unit.",
                        WorkflowBranchScope.BRANCH, WorkflowPurpose.RESTOCK, 23, RESTOCK_CATEGORIES, List.of(
                        step("BM Approval", REQUEST_APPROVAL, BRANCH_MANAGER, REQUESTER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER,
                                "BRANCH_MANAGER"),
                        step("Admin Acknowledgement", ACKNOWLEDGE_REQUEST, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Admin Issuance", ISSUANCE, GROUP_EMAIL, REQUESTER, ADMIN_UNIT, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Approve Issuance", REQUEST_APPROVAL, MANAGER, ISSUER, null, REQUESTER_MANAGERS_GROUP, TELL_REQUESTER),
                        step("Acknowledge Receipt", ACKNOWLEDGE_RECEIPT, StepApproverType.REQUESTER, REQUESTER, null, ADMIN, Set.of(REQUESTER_MANAGERS_GROUP))))
        );
    }

    // ── seeding ─────────────────────────────────────────────────────────────

    @Transactional
    public void seedWorkflows() {
        oneTimeSeed.once(MARKER,
                "Seeded the approval workflows into an empty workflow table, or recorded that the table "
                        + "already had workflows and left them alone. Edited in Settings from here on.",
                this::seedIfEmpty);
    }

    private void seedIfEmpty() {
        long existing = workflowRepository.count();
        if (existing > 0) {
            log.info("Workflow seed: {} workflow(s) already exist - leaving them as they are", existing);
            return;
        }
        Built built = build();
        built.problems().forEach(p -> log.error("Workflow seed: {}", p));
        for (ApprovalWorkflow workflow : built.workflows()) {
            saveWithSteps(workflow);
        }
        log.info("Workflow seed: created {} of {} workflow(s)", built.workflows().size(), definitions().size());
    }

    /** As {@code ApprovalWorkflowService.create}: the workflow first, then its steps. */
    private void saveWithSteps(ApprovalWorkflow workflow) {
        List<ApprovalStep> steps = new ArrayList<>(workflow.getSteps());
        workflow.getSteps().clear();
        ApprovalWorkflow saved = workflowRepository.save(workflow);
        for (ApprovalStep step : steps) {
            step.setWorkflow(saved);
            saved.getSteps().add(step);
        }
        workflowRepository.save(saved);
    }

    /** Unsaved workflows, and a line for every one skipped because a name did not resolve. */
    record Built(List<ApprovalWorkflow> workflows, List<String> problems) {}

    Built build() {
        List<ApprovalWorkflow> workflows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (WorkflowDef def : definitions()) {
            List<String> missing = new ArrayList<>();
            ApprovalWorkflow workflow = toEntity(def, missing);
            if (missing.isEmpty()) {
                workflows.add(workflow);
            } else {
                problems.add("skipped '" + def.name() + "', could not find: " + String.join(", ", missing));
            }
        }
        return new Built(workflows, problems);
    }

    private ApprovalWorkflow toEntity(WorkflowDef def, List<String> missing) {
        Set<AssetType> categories = new HashSet<>();
        for (String name : def.categories()) {
            assetTypeRepository.findByNameIgnoreCase(name).ifPresentOrElse(categories::add,
                    () -> missing.add("category '" + name + "'"));
        }
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .name(def.name())
                .description(def.description())
                .branchScope(def.scope())
                .appliesTo(def.purpose())
                .priority(def.priority())
                .active(true)
                .assetTypes(categories)
                .build();
        int order = 1;
        for (StepDef s : def.steps()) {
            Unit unit = null;
            if (s.unitRole() != null) {
                unit = unitConferring(s.unitRole()).orElse(null);
                if (unit == null) missing.add("a unit conferring " + s.unitRole());
            }
            Set<Role> skip = new HashSet<>();
            for (String roleName : s.skipRoles()) {
                roleRepository.findByName(roleName).ifPresentOrElse(skip::add,
                        () -> missing.add("role '" + roleName + "'"));
            }
            workflow.getSteps().add(ApprovalStep.builder()
                    .workflow(workflow)
                    .stepOrder(order++)
                    .stepName(s.name())
                    .stepType(s.type())
                    .approverType(s.approver())
                    .approverSubject(s.subject())
                    .unit(unit)
                    .groupEmail(mailbox())
                    .notifyGroupEmail(mailbox())
                    .notifyGroupSource(s.notifySource())
                    .notifyOnCompletion(new HashSet<>(s.onCompletion()))
                    .optional(false)
                    .escalationHours(ESCALATION_HOURS)
                    .skipIfRequesterRoles(skip)
                    .build());
        }
        return workflow;
    }

    /** The unit is found by the role it confers, never by its name: units are renamed in Settings. */
    private Optional<Unit> unitConferring(String roleName) {
        return unitRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> roleName.equals(r.getName())))
                .min(java.util.Comparator.comparing(Unit::getId));
    }

    private String mailbox() {
        return (groupEmail == null || groupEmail.isBlank()) ? null : groupEmail.trim();
    }
}
