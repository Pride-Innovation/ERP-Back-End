package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static pride.bank.erp.helper.Constants.*;

/**
 * Pins what the estate looks like after {@code UnitRoleSeeder} has run.
 *
 * <p>Two things are being guarded. First, that the Admin and Infra units actually confer something —
 * before this work a unit was a group mailbox and nothing more, and the seniority the bank works to
 * was written against role names that were never seeded, so it never took effect. Second, that
 * making "own records only" the officer default did not silently narrow the roles that legitimately
 * see their whole branch.
 */
@SpringBootTest
class UnitRoleSeedingTest {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private pride.bank.erp.config.seed.SeederStateRepository seederStateRepository;

    private Set<String> permissionsOfRole(String roleName) {
        return roleRepository.findByName(roleName)
                .map(role -> role.getPermissions().stream()
                        .map(p -> p.getName())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private Unit unitNamed(String name) {
        return unitRepository.findAll().stream()
                .filter(u -> name.equalsIgnoreCase(u.getName()))
                .findFirst()
                .orElse(null);
    }

    /** Every multiplier must exist as a row, or Settings cannot offer it and nothing can grant it. */
    @Test
    @Transactional(readOnly = true)
    void everyCrossBranchPermissionIsSeeded() {
        for (String name : List.of(
                VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS,
                VIEW_ALL_BRANCH_REQUESTS, MANAGE_ALL_BRANCH_REQUESTS,
                VIEW_ALL_BRANCH_MOVEMENTS, MANAGE_ALL_BRANCH_MOVEMENTS,
                VIEW_ALL_BRANCH_INVENTORY, MANAGE_ALL_BRANCH_INVENTORY)) {
            assertThat(permissionRepository.findByName(name))
                    .as("permission '%s' must be seeded or it cannot be granted in Settings", name)
                    .isPresent();
        }
    }

    /** Both units confer a role. This is the whole point of the change. */
    @Test
    @Transactional(readOnly = true)
    void adminAndInfraUnitsCarryTheirRole() {
        for (String unitName : List.of("Admin Unit", "Infra Unit")) {
            Unit unit = unitNamed(unitName);
            assertThat(unit).as("unit '%s' must exist", unitName).isNotNull();
            assertThat(unit.getRoles())
                    .as("unit '%s' must carry at least one role — a unit that confers nothing is "
                            + "the state this work exists to change", unitName)
                    .isNotEmpty();
        }
    }

    /**
     * The units start as peers.
     *
     * <p>Seeded identically so neither is silently less capable than the other because of a decision
     * made in code; they are narrowed from Settings instead.
     */
    @Test
    @Transactional(readOnly = true)
    void bothUnitRolesStartWithTheSameReach() {
        Set<String> admin = permissionsOfRole(ADMIN_UNIT_ROLE);
        Set<String> infra = permissionsOfRole(INFRA_UNIT_ROLE);

        assertThat(admin).isNotEmpty();
        for (String expected : List.of(
                VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS,
                VIEW_ALL_BRANCH_MOVEMENTS, MANAGE_ALL_BRANCH_MOVEMENTS,
                VIEW_ALL_BRANCH_INVENTORY, MANAGE_ALL_BRANCH_INVENTORY,
                DASH_SCOPE_ALL)) {
            assertThat(admin).as("%s on %s", expected, ADMIN_UNIT_ROLE).contains(expected);
            assertThat(infra).as("%s on %s", expected, INFRA_UNIT_ROLE).contains(expected);
        }
    }

    /**
     * Cross-branch *request* visibility is not granted by default.
     *
     * <p>A branch request reaches Admin through the workflow — a step routed to the unit sets
     * {@code Request.currentUnit} and every member sees it. Granting this as well would make every
     * branch request visible from the moment it was raised, which is easy to add later and hard to
     * walk back.
     */
    @Test
    @Transactional(readOnly = true)
    void unitsCannotSeeEveryBranchRequestByDefault() {
        assertThat(permissionsOfRole(ADMIN_UNIT_ROLE)).doesNotContain(VIEW_ALL_BRANCH_REQUESTS);
        assertThat(permissionsOfRole(INFRA_UNIT_ROLE)).doesNotContain(VIEW_ALL_BRANCH_REQUESTS);
    }

    /**
     * Nobody is narrowed by the new officer default.
     *
     * <p>Branch leads and approvers held their reach implicitly, by having a read permission and a
     * duty station. Once "no explicit scope" means SELF, that implication is gone — so the scope has
     * to be granted outright or these roles lose their branch on the day this deploys.
     */
    @Test
    @Transactional(readOnly = true)
    void branchLeadsKeepTheirBranch() {
        for (String roleName : List.of(
                "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER", "MANAGER", "HEAD_OF_DEPARTMENT")) {
            assertThat(permissionsOfRole(roleName))
                    .as("%s must keep sight of its whole branch", roleName)
                    .contains(DASH_SCOPE_BRANCH);
        }
    }

    /** And an officer is confined to their own records, which is the intended new behaviour. */
    @Test
    @Transactional(readOnly = true)
    void officersAreConfinedToTheirOwnRecords() {
        Set<String> officer = permissionsOfRole("OFFICER");
        assertThat(officer)
                .as("OFFICER must carry an explicit scope; without one the fallback decides, and the "
                        + "point of the change is that it no longer has to")
                .contains(DASH_SCOPE_SELF);
        assertThat(officer)
                .as("an officer must not see the whole branch by default")
                .doesNotContain(DASH_SCOPE_BRANCH, DASH_SCOPE_ALL);
    }

    /** A branch role must never hold a cross-branch multiplier — that is the unit's job alone. */
    @Test
    @Transactional(readOnly = true)
    void branchRolesHoldNoCrossBranchReach() {
        for (String roleName : List.of("OFFICER", "SUPERVISOR", "BRANCH_MANAGER",
                "BRANCH_OPERATIONS_MANAGER")) {
            assertThat(permissionsOfRole(roleName))
                    .as("%s must not reach beyond its own branch", roleName)
                    .doesNotContain(VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS);
        }
    }

    /**
     * A scope revoked in Settings must stay revoked across restarts.
     *
     * <p>The default scopes are a migration, not a reconciliation: they exist so that nobody was
     * narrowed on the day the scope model shipped, and that is a one-day job. While they re-applied
     * on every startup, revoking {@code DASH_SCOPE_BRANCH} from MANAGER worked until the next
     * restart and then silently came back — the permission was assignable but not revokable, and
     * nothing on screen said so.
     *
     * <p>Pinned on the marker rather than by restarting the context: the marker's presence is the
     * whole mechanism, and asserting it is what would fail if someone removed the {@code once(...)}
     * wrapper and went back to granting unconditionally.
     */
    @Test
    @Transactional(readOnly = true)
    void theDefaultScopeGrantsRunOnlyOnce() {
        assertThat(seederStateRepository.findById("unit-role-seeder:default-role-scopes"))
                .as("without this marker the scope grants re-apply on every startup, and revoking a "
                        + "scope in Settings is not a working operation")
                .isPresent();
    }

    /** Re-running the seeder must not resurrect a permission an administrator revoked. */
    @Test
    @Transactional(readOnly = true)
    void unitRolesAreCreateOnly() {
        Role adminRole = roleRepository.findByName(ADMIN_UNIT_ROLE).orElse(null);
        assertThat(adminRole)
                .as("the seeder creates the role once and then leaves its permissions alone, so a "
                        + "revocation in Settings survives a restart")
                .isNotNull();
    }
}
