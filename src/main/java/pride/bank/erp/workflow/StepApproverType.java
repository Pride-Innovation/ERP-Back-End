package pride.bank.erp.workflow;

public enum StepApproverType {
    DIRECT_SUPERVISOR,   // requester.title.reportsTo user in same branch (always resolves, regardless of role)
    SUPERVISOR,          // nearest ancestor title (walking reportsTo) carrying the SUPERVISOR role;
                         // automatically skipped at runtime if the officer has no supervisor in their chain
    MANAGER,             // nearest ancestor title (walking reportsTo) carrying the MANAGER role; when no
                         // individual manager resolves it falls back to the requester's managers-group email,
                         // and it is skipped when the officer has no manager tier in their chain at all
    DEPT_HEAD,           // requester.department.headOfDepartment
    BOM,                 // branch.branchOperationsManager
    BRANCH_MANAGER,      // branch.branchManager
    ADMIN,               // application.admin.email config user
    GROUP_EMAIL,         // send only to a configured group email, no individual
    SPECIFIC_USER,       // a specific user by ID
    REQUESTER            // the user who originally raised the request — used for "Acknowledge Receipt"
}
