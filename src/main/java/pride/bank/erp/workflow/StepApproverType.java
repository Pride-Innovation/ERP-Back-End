package pride.bank.erp.workflow;

public enum StepApproverType {
    DIRECT_SUPERVISOR,   // requester.title.reportsTo user in same branch (always resolves, regardless of role)
    SUPERVISOR,          // routes to requester.title.reportsTo ONLY when that title carries the SUPERVISOR role;
                         // automatically skipped at runtime if the officer has no supervisor in their chain
    DEPT_HEAD,           // requester.department.headOfDepartment
    BOM,                 // branch.branchOperationsManager
    BRANCH_MANAGER,      // branch.branchManager
    ADMIN,               // application.admin.email config user
    GROUP_EMAIL,         // send only to a configured group email, no individual
    SPECIFIC_USER,       // a specific user by ID
    REQUESTER            // the user who originally raised the request — used for "Acknowledge Receipt"
}
