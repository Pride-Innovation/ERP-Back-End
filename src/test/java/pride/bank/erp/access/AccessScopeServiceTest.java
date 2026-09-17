package pride.bank.erp.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.user.User;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;
import static pride.bank.erp.helper.Constants.*;

/**
 * The scope engine, exercised through the four people the bank actually has.
 *
 * <p>Written as unit tests rather than through the HTTP layer because the interesting cases are
 * combinations of permissions and branches, and there are more of them than it is reasonable to
 * stand up users for. The end-to-end path is covered by the seeding test and the endpoint guard
 * coverage test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessScopeServiceTest {

    private static final Long BRANCH_KAMPALA = 1L;
    private static final Long BRANCH_GULU = 2L;
    private static final Long ME = 100L;
    private static final Long SOMEONE_ELSE = 200L;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AccessScopeService accessScopeService;

    /** Builds a signed-in user holding exactly these permissions, at this branch. */
    private void signedInAt(Long branchId, String... permissions) {
        Set<Permission> held = Arrays.stream(permissions)
                .map(name -> Permission.builder().name(name).build())
                .collect(Collectors.toCollection(HashSet::new));

        Role role = Role.builder().name("TEST_ROLE").permissions(held).build();
        Title title = Title.builder().role(role).build();

        User user = User.builder()
                .id(ME)
                .title(title)
                .branch(branchId == null ? null : Branch.builder().id(branchId).build())
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    /** The same, but the permissions arrive through the user's unit rather than their title. */
    private void signedInAtViaUnit(Long branchId, String... permissions) {
        Set<Permission> held = Arrays.stream(permissions)
                .map(name -> Permission.builder().name(name).build())
                .collect(Collectors.toCollection(HashSet::new));

        Role unitRole = Role.builder().name(ADMIN_UNIT_ROLE).permissions(held).build();
        Unit unit = Unit.builder().name("Admin Unit").roles(new HashSet<>(Set.of(unitRole))).build();

        // A bare title role, so nothing the test asserts can have come from the title.
        Role titleRole = Role.builder().name("OFFICER").permissions(new HashSet<>()).build();

        User user = User.builder()
                .id(ME)
                .title(Title.builder().role(titleRole).build())
                .unit(unit)
                .branch(branchId == null ? null : Branch.builder().id(branchId).build())
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @BeforeEach
    void resetToNobody() {
        when(currentUserService.getCurrentUser()).thenReturn(null);
    }

    // ── The officer ─────────────────────────────────────────────────────────

    @Test
    void anOfficerSeesOnlyTheirOwnAssets() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, DASH_SCOPE_SELF);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .isEqualTo(DashboardScope.SELF);
        assertThat(accessScopeService.resolveOwner(ScopeSubject.ASSETS))
                .as("at SELF the listing must be narrowed to the holder")
                .isEqualTo(ME);
    }

    @Test
    void anOfficerCannotTouchAnotherPersonsAsset() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, UPDATE_ASSET, DASH_SCOPE_SELF);

        assertThatThrownBy(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_KAMPALA, SOMEONE_ELSE))
                .as("UPDATE_ASSET admits them to the endpoint; it does not make the asset theirs")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anOfficerCanTouchTheirOwn() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, UPDATE_ASSET, DASH_SCOPE_SELF);

        assertThatCode(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_KAMPALA, ME))
                .doesNotThrowAnyException();
    }

    // ── The branch lead ─────────────────────────────────────────────────────

    @Test
    void aBranchLeadReachesTheirWholeBranchAndNoFurther() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, UPDATE_ASSET, DASH_SCOPE_BRANCH);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .isEqualTo(DashboardScope.BRANCH);
        assertThat(accessScopeService.resolveOwner(ScopeSubject.ASSETS))
                .as("BRANCH is wider than SELF, so no owner limit applies")
                .isNull();

        assertThatCode(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_KAMPALA, SOMEONE_ELSE))
                .as("a colleague's asset at their own branch is within reach")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_GULU, SOMEONE_ELSE))
                .as("this is the hole the write guard exists to close")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void namingAnotherBranchIsRefusedRatherThanQuietlyRewritten() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, DASH_SCOPE_BRANCH);

        assertThatThrownBy(() -> accessScopeService.resolveBranch(ScopeSubject.ASSETS, BRANCH_GULU))
                .as("showing Kampala's figures under Gulu's name is worse than refusing")
                .isInstanceOf(AccessDeniedException.class);

        assertThat(accessScopeService.resolveBranch(ScopeSubject.ASSETS, null))
                .as("omitting the branch is a different act — scope it silently")
                .isEqualTo(BRANCH_KAMPALA);
    }

    // ── The unit officer ────────────────────────────────────────────────────

    @Test
    void aUnitOfficerReachesEveryBranch() {
        signedInAtViaUnit(BRANCH_KAMPALA,
                VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS, DASH_SCOPE_ALL);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE))
                .as("the whole point: an Admin Unit officer outranks a branch officer of the same title")
                .isEqualTo(DashboardScope.ALL);

        assertThatCode(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_GULU, SOMEONE_ELSE))
                .doesNotThrowAnyException();

        assertThat(accessScopeService.resolveBranch(ScopeSubject.ASSETS, null))
                .as("null means no branch restriction — the national listing")
                .isNull();
        assertThat(accessScopeService.resolveBranch(ScopeSubject.ASSETS, BRANCH_GULU))
                .as("and they may focus one branch at a time")
                .isEqualTo(BRANCH_GULU);
    }

    @Test
    void revokingTheUnitRolesPermissionRevokesTheReach() {
        // Same unit officer, with MANAGE_ALL_BRANCH_ASSETS revoked in Settings.
        signedInAtViaUnit(BRANCH_KAMPALA, VIEW_ALL_BRANCH_ASSETS, DASH_SCOPE_ALL);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .as("they keep sight of every branch")
                .isEqualTo(DashboardScope.ALL);

        assertThatThrownBy(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_GULU, SOMEONE_ELSE))
                .as("but may no longer change another branch's records")
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── The rules that hold across all of them ──────────────────────────────

    @Test
    void aMultiplierWidensOnlyItsOwnSubject() {
        signedInAtViaUnit(BRANCH_KAMPALA, VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE))
                .isEqualTo(DashboardScope.ALL);
        assertThat(accessScopeService.scopeFor(ScopeSubject.REQUESTS, ScopeAction.MANAGE))
                .as("cross-branch assets says nothing about cross-branch requests")
                .isNotEqualTo(DashboardScope.ALL);
    }

    @Test
    void seeingEveryBranchIsNotLicenceToEditIt() {
        // DASH_SCOPE_ALL alone: their dashboard totals the bank, so their listing should agree —
        // but a write still waits for the explicit multiplier.
        signedInAt(BRANCH_KAMPALA, READ_ASSET, UPDATE_ASSET, DASH_SCOPE_ALL);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .as("otherwise a widget counts 1,200 assets and the page it links to shows 90")
                .isEqualTo(DashboardScope.ALL);
        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE))
                .isNotEqualTo(DashboardScope.ALL);
    }

    @Test
    void theLegacyCrossBranchPermissionStillGrantsSight() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, VIEW_ALL_BRANCHES);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .as("nobody who could see the bank yesterday may be narrowed today")
                .isEqualTo(DashboardScope.ALL);
        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE))
                .as("it was never a write permission and does not become one")
                .isNotEqualTo(DashboardScope.ALL);
    }

    @Test
    void aRoleNobodyHasConfiguredKeepsItsBranch() {
        // No DASH_SCOPE_* at all — the state every role is in before an administrator visits
        // Settings. Falls back to the signal that governed reach before this model existed.
        signedInAt(BRANCH_KAMPALA, READ_ASSET);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .as("narrowing an unconfigured role on deploy day would look like an outage")
                .isEqualTo(DashboardScope.BRANCH);
    }

    @Test
    void someoneWithNoBranchAndNoReadPermissionIsConfinedToThemselves() {
        signedInAt(null);

        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .isEqualTo(DashboardScope.SELF);
    }

    @Test
    void anUnassignedRecordStaysReachable() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, DASH_SCOPE_BRANCH);

        assertThatCode(() -> accessScopeService.requireCanManage(ScopeSubject.ASSETS, null, null))
                .as("an asset belonging to no branch must not become invisible and unfixable to "
                        + "everyone but a cross-branch holder — that is how the nullable-association "
                        + "bugs in this codebase have repeatedly presented")
                .doesNotThrowAnyException();
    }

    /**
     * The bulk importer creates far more assets than the single-create form, and each row names its
     * own branch — so it is the same question asked two hundred times, and it was the path that went
     * unguarded longest.
     */
    @Test
    void importingIntoAnotherBranchIsRefused() {
        signedInAt(BRANCH_KAMPALA, READ_ASSET, CREATE_ASSET, IMPORT_ASSET, DASH_SCOPE_BRANCH);

        assertThatCode(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_KAMPALA, null))
                .as("a row placing an asset at their own branch is fine")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> accessScopeService.requireCanManage(
                ScopeSubject.ASSETS, BRANCH_GULU, null))
                .as("holding IMPORT_ASSET is not a licence to file assets in another branch")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnauthenticatedCallerReachesNothing() {
        // getCurrentUser() returns null outside a bound request — a scheduled job, a seeder.
        assertThat(accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW))
                .isEqualTo(DashboardScope.SELF);
        assertThat(accessScopeService.canManage(ScopeSubject.ASSETS, BRANCH_GULU, SOMEONE_ELSE))
                .isFalse();
    }
}
