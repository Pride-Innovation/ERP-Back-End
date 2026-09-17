/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.department.Department;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static pride.bank.erp.workflow.StepApproverType.BOM;
import static pride.bank.erp.workflow.StepApproverType.BRANCH_MANAGER;
import static pride.bank.erp.workflow.StepApproverType.DEPT_HEAD;
import static pride.bank.erp.workflow.StepApproverType.MANAGER;
import static pride.bank.erp.workflow.StepApproverType.SUPERVISOR;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproverChainResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaveRepository leaveRepository;

    @InjectMocks
    private ApproverChainResolver resolver;

    // ── Head Office ────────────────────────────────────────────────────────────

    @Test
    void headOffice_allTiersPresent_resolvesSupervisorNearestManagerAndHod() {
        Branch ho = branch(1, true, "mgrs@ho");
        Department dept = dept("mgrs@ho");
        Title tHod = title(10, role(4, "HEAD_OF_DEPARTMENT"), null);
        Title tMgr2 = title(11, role(2, "MANAGER"), tHod);          // Financial Controller
        Title tMgr1 = title(12, role(2, "MANAGER"), tMgr2);          // Finance Manager (nearest)
        Title tSup = title(13, role(8, "SUPERVISOR"), tMgr1);
        Title tOff = title(14, role(3, "OFFICER"), tSup);

        User uSup = user(101, tSup, ho, dept);
        User uMgr1 = user(102, tMgr1, ho, dept);
        User uHod = user(104, tHod, ho, dept);
        User requester = user(100, tOff, ho, dept);
        holder(13, 1, uSup);
        holder(12, 1, uMgr1);
        holder(11, 1, user(103, tMgr2, ho, dept));
        holder(10, 1, uHod);

        List<ResolvedApprover> chain = resolver.resolve(requester,
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, DEPT_HEAD)));

        assertEquals(3, chain.size());
        assertEquals(101L, chain.get(0).getApprover().getId());
        assertEquals(102L, chain.get(1).getApprover().getId());   // nearest manager only
        assertEquals(104L, chain.get(2).getApprover().getId());
    }

    @Test
    void headOffice_noSupervisorOnLadder_skipsSupervisorTier() {
        Branch ho = branch(1, true, "mgrs@ho");
        Department dept = dept("mgrs@ho");
        Title tHod = title(20, role(4, "HEAD_OF_DEPARTMENT"), null);
        Title tMgr = title(21, role(2, "MANAGER"), tHod);
        Title tOff = title(22, role(3, "OFFICER"), tMgr);          // reports straight to a manager

        User uMgr = user(201, tMgr, ho, dept);
        User uHod = user(202, tHod, ho, dept);
        holder(21, 1, uMgr);
        holder(20, 1, uHod);

        List<ResolvedApprover> chain = resolver.resolve(user(200, tOff, ho, dept),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, DEPT_HEAD)));

        assertEquals(2, chain.size());
        assertEquals(201L, chain.get(0).getApprover().getId());
        assertEquals(202L, chain.get(1).getApprover().getId());
    }

    @Test
    void headOffice_hodRemoved_managerBecomesTerminal() {
        Branch ho = branch(1, true, "mgrs@ho");
        Department dept = dept("mgrs@ho");
        Title tHod = title(10, role(4, "HEAD_OF_DEPARTMENT"), null);
        Title tMgr1 = title(12, role(2, "MANAGER"), tHod);
        Title tSup = title(13, role(8, "SUPERVISOR"), tMgr1);
        Title tOff = title(14, role(3, "OFFICER"), tSup);
        holder(13, 1, user(101, tSup, ho, dept));
        holder(12, 1, user(102, tMgr1, ho, dept));
        holder(10, 1, user(104, tHod, ho, dept));

        List<ResolvedApprover> chain = resolver.resolve(user(100, tOff, ho, dept),
                workflow(step(1, SUPERVISOR), step(2, MANAGER)));   // no HoD

        assertEquals(2, chain.size());
        assertEquals(101L, chain.get(0).getApprover().getId());
        assertEquals(102L, chain.get(1).getApprover().getId());     // terminal — HoD never reached
    }

    @Test
    void headOffice_managerAbsentNoActing_routesToManagersGroup() {
        Branch ho = branch(1, true, "mgrs@ho");
        Department dept = dept("mgrs@ho");
        Title tHod = title(10, role(4, "HEAD_OF_DEPARTMENT"), null);
        Title tMgr1 = title(12, role(2, "MANAGER"), tHod);
        Title tSup = title(13, role(8, "SUPERVISOR"), tMgr1);
        Title tOff = title(14, role(3, "OFFICER"), tSup);
        User uMgr1 = user(102, tMgr1, ho, dept);
        holder(13, 1, user(101, tSup, ho, dept));
        holder(12, 1, uMgr1);
        holder(10, 1, user(104, tHod, ho, dept));
        onLeaveNoActing(uMgr1);

        List<ResolvedApprover> chain = resolver.resolve(user(100, tOff, ho, dept),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, DEPT_HEAD)));

        assertEquals(3, chain.size());
        ResolvedApprover managerStop = chain.get(1);
        assertTrue(managerStop.isGroup());
        assertEquals("mgrs@ho", managerStop.getGroupEmail());
        assertNull(managerStop.getApprover());
    }

    @Test
    void supervisorOnLeaveWithActing_routesToActingUser() {
        Branch ho = branch(1, true, "mgrs@ho");
        Department dept = dept("mgrs@ho");
        Title tHod = title(10, role(4, "HEAD_OF_DEPARTMENT"), null);
        Title tMgr1 = title(12, role(2, "MANAGER"), tHod);
        Title tSup = title(13, role(8, "SUPERVISOR"), tMgr1);
        Title tOff = title(14, role(3, "OFFICER"), tSup);
        User uSup = user(101, tSup, ho, dept);
        User acting = user(999, null, ho, dept);
        holder(13, 1, uSup);
        holder(12, 1, user(102, tMgr1, ho, dept));
        holder(10, 1, user(104, tHod, ho, dept));
        onLeaveWithActing(uSup, acting);

        List<ResolvedApprover> chain = resolver.resolve(user(100, tOff, ho, dept),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, DEPT_HEAD)));

        assertEquals(3, chain.size());
        assertEquals(999L, chain.get(0).getApprover().getId());     // acting user substituted
    }

    // ── Branches ────────────────────────────────────────────────────────────────

    @Test
    void branch_managerSitsAboveTerminal_isSkipped() {
        Branch b = branch(2, false, "mgrs@b2");
        Title tRegional = title(30, role(2, "MANAGER"), null);
        Title tBm = title(31, role(5, "BRANCH_MANAGER"), tRegional);
        Title tBom = title(32, role(6, "BRANCH_OPERATIONS_MANAGER"), tBm);
        Title tSup = title(33, role(8, "SUPERVISOR"), tBom);
        Title tOff = title(34, role(3, "OFFICER"), tSup);
        holder(33, 2, user(301, tSup, b, null));
        holder(32, 2, user(302, tBom, b, null));
        holder(31, 2, user(303, tBm, b, null));

        List<ResolvedApprover> chain = resolver.resolve(user(300, tOff, b, null),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, BOM), step(4, BRANCH_MANAGER)));

        assertEquals(3, chain.size());
        assertEquals(301L, chain.get(0).getApprover().getId());
        assertEquals(302L, chain.get(1).getApprover().getId());
        assertEquals(303L, chain.get(2).getApprover().getId());
        assertTrue(chain.stream().noneMatch(r -> "MANAGER".equals(r.getRoleName())));
    }

    @Test
    void branch_noSupervisor_startsAtBom() {
        Branch b = branch(2, false, "mgrs@b2");
        Title tRegional = title(40, role(2, "MANAGER"), null);
        Title tBm = title(41, role(5, "BRANCH_MANAGER"), tRegional);
        Title tBom = title(42, role(6, "BRANCH_OPERATIONS_MANAGER"), tBm);
        Title tOff = title(43, role(3, "OFFICER"), tBom);          // reports straight to BOM
        holder(42, 2, user(402, tBom, b, null));
        holder(41, 2, user(403, tBm, b, null));

        List<ResolvedApprover> chain = resolver.resolve(user(400, tOff, b, null),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, BOM), step(4, BRANCH_MANAGER)));

        assertEquals(2, chain.size());
        assertEquals(402L, chain.get(0).getApprover().getId());
        assertEquals(403L, chain.get(1).getApprover().getId());
    }

    @Test
    void branch_supervisorAbsentNoActing_skipsUpToNextTier() {
        Branch b = branch(2, false, "mgrs@b2");
        Title tRegional = title(30, role(2, "MANAGER"), null);
        Title tBm = title(31, role(5, "BRANCH_MANAGER"), tRegional);
        Title tBom = title(32, role(6, "BRANCH_OPERATIONS_MANAGER"), tBm);
        Title tSup = title(33, role(8, "SUPERVISOR"), tBom);
        Title tOff = title(34, role(3, "OFFICER"), tSup);
        User uSup = user(301, tSup, b, null);
        holder(33, 2, uSup);
        holder(32, 2, user(302, tBom, b, null));
        holder(31, 2, user(303, tBm, b, null));
        onLeaveNoActing(uSup);

        List<ResolvedApprover> chain = resolver.resolve(user(300, tOff, b, null),
                workflow(step(1, SUPERVISOR), step(2, MANAGER), step(3, BOM), step(4, BRANCH_MANAGER)));

        assertEquals(2, chain.size());
        assertEquals(302L, chain.get(0).getApprover().getId());     // supervisor skipped, starts at BOM
        assertEquals(303L, chain.get(1).getApprover().getId());
    }

    @Test
    void branch_noWhitelistedTierOnLadder_fallsBackToImmediateReportsTo() {
        Branch b = branch(2, false, "mgrs@b2");
        Title tRegional = title(50, role(2, "MANAGER"), null);
        Title tBm = title(51, role(5, "BRANCH_MANAGER"), tRegional);
        Title tOff = title(52, role(3, "OFFICER"), tBm);           // Credit Admin → Branch Manager directly
        User uBm = user(501, tBm, b, null);
        holder(51, 2, uBm);

        // workflow has neither Branch Manager nor a tier on this officer's line
        List<ResolvedApprover> chain = resolver.resolve(user(500, tOff, b, null),
                workflow(step(1, SUPERVISOR), step(2, BOM)));

        assertEquals(1, chain.size());
        assertTrue(chain.get(0).isFallback());
        assertEquals(501L, chain.get(0).getApprover().getId());     // immediate reportsTo holder
    }

    // ── Builders ────────────────────────────────────────────────────────────────

    private Role role(long id, String name) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private Title title(long id, Role role, Title reportsTo) {
        return Title.builder().id(id).name("T" + id).role(role).reportsTo(reportsTo).build();
    }

    private Branch branch(long id, boolean headOffice, String managersGroup) {
        return Branch.builder().id(id).isHeadOffice(headOffice).managersGroupEmail(managersGroup).build();
    }

    private Department dept(String managersGroup) {
        return Department.builder().managersGroupEmail(managersGroup).build();
    }

    private User user(long id, Title title, Branch branch, Department dept) {
        return User.builder().id(id).firstName("U" + id).title(title).branch(branch).department(dept).build();
    }

    private ApprovalStep step(int order, StepApproverType type) {
        return ApprovalStep.builder()
                .stepOrder(order)
                .stepType(StepType.REQUEST_APPROVAL)
                .approverType(type)
                .stepName(type.name())
                .build();
    }

    private ApprovalWorkflow workflow(ApprovalStep... steps) {
        return ApprovalWorkflow.builder().steps(new ArrayList<>(List.of(steps))).build();
    }

    private void holder(long titleId, long branchId, User u) {
        when(userRepository.findByTitleIdAndBranchId(titleId, branchId)).thenReturn(List.of(u));
    }

    private void onLeaveNoActing(User u) {
        when(leaveRepository.findActiveLeaveByUser(u.getId()))
                .thenReturn(Optional.of(Leave.builder().active(true).onLeaveUser(u).build()));
    }

    private void onLeaveWithActing(User u, User acting) {
        when(leaveRepository.findActiveLeaveByUser(u.getId()))
                .thenReturn(Optional.of(Leave.builder().active(true).onLeaveUser(u).actingUser(acting).build()));
    }
}
