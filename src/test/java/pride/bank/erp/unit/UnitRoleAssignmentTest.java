package pride.bank.erp.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static pride.bank.erp.helper.Constants.ADMIN_UNIT_ROLE;

/**
 * Assigning roles to a unit — the screen that can hand a branch officer the whole bank.
 *
 * <p>Two properties matter here and neither is visible from the happy path. The call must be
 * <b>guarded by the permission that matches its consequence</b>: it changes who holds what, so it
 * answers to {@code UPDATE_ROLE} and not to whoever may rename a unit. And it must be a
 * <b>replacement</b>, because the requirement is that every permission be revokable — if granting
 * and revoking were different operations, revocation would be the one that silently didn't happen.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnitRoleAssignmentTest {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UnitService unitService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Unit adminUnit;
    private List<Long> originalRoleIds;

    private Unit findAdminUnit() {
        return unitRepository.findAll().stream()
                .filter(u -> "Admin Unit".equalsIgnoreCase(u.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Admin Unit is seeded; it must exist"));
    }

    @BeforeEach
    void rememberWhatWasThere() {
        adminUnit = findAdminUnit();
        originalRoleIds = adminUnit.getRoles().stream().map(Role::getId).collect(Collectors.toList());
    }

    /** These tests write to the shared development database, so they put it back as they found it. */
    @AfterEach
    void restore() throws NotFoundException {
        unitService.replaceUnitRoles(adminUnit.getId(), originalRoleIds);
    }

    @Test
    @WithMockUser(authorities = "UPDATE_SETTING")
    void renamingAUnitDoesNotQualifySomeoneToChangeWhatItGrants() throws Exception {
        int status = mockMvc.perform(put(API + "/units/" + adminUnit.getId() + "/roles")
                        .contentType("application/json")
                        .content("{\"roleIds\":[]}"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("this call grants permissions; it must answer to UPDATE_ROLE")
                .isEqualTo(403);
    }

    @Test
    @WithMockUser(authorities = "UPDATE_ROLE")
    void whoeverMayEditRolesMayDecideWhichOnesAUnitConfers() throws Exception {
        int status = mockMvc.perform(put(API + "/units/" + adminUnit.getId() + "/roles")
                        .contentType("application/json")
                        .content("{\"roleIds\":" + originalRoleIds + "}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    void everyRoleIsRevokable() throws NotFoundException {
        assertThat(adminUnit.getRoles())
                .as("precondition: the seeder gave Admin Unit a role")
                .isNotEmpty();

        unitService.replaceUnitRoles(adminUnit.getId(), List.of());

        assertThat(findAdminUnit().getRoles())
                .as("an empty list strips the unit back to a group mailbox with a department — "
                        + "the state it was in before units conferred anything")
                .isEmpty();
    }

    @Test
    void aRoleLeftOutOfTheListIsRevoked() throws NotFoundException {
        Role adminUnitRole = roleRepository.findByName(ADMIN_UNIT_ROLE).orElseThrow();
        Role other = roleRepository.findByName("OFFICER").orElseThrow();

        unitService.replaceUnitRoles(adminUnit.getId(), List.of(adminUnitRole.getId(), other.getId()));
        assertThat(roleNamesOfAdminUnit()).contains(ADMIN_UNIT_ROLE, "OFFICER");

        unitService.replaceUnitRoles(adminUnit.getId(), List.of(other.getId()));

        assertThat(roleNamesOfAdminUnit())
                .as("a replacement, not a merge — otherwise nothing could ever be taken away")
                .containsExactly("OFFICER");
    }

    @Test
    void theRolesAndTheirPermissionsAreReadableFromTheUnit() throws Exception {
        UnitResponse response = unitService.findOneUnitResponse(adminUnit.getId());

        assertThat(response.getRoles())
                .as("Settings has to show what a unit grants before anyone is added to it")
                .isNotEmpty();
        assertThat(response.getRoles().get(0).getPermissions())
                .as("and the confirmation dialog has to spell out the consequences without a "
                        + "second round trip that could disagree with what was approved")
                .isNotEmpty();
    }

    @Test
    void namingARoleThatDoesNotExistChangesNothing() {
        Set<String> before = roleNamesOfAdminUnit();

        try {
            unitService.replaceUnitRoles(adminUnit.getId(), List.of(999_999_999L));
        } catch (NotFoundException expected) {
            // The transaction rolls back with it.
        }

        assertThat(roleNamesOfAdminUnit())
                .as("a bad id must not leave the unit half-assigned")
                .isEqualTo(before);
    }

    private Set<String> roleNamesOfAdminUnit() {
        return findAdminUnit().getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
