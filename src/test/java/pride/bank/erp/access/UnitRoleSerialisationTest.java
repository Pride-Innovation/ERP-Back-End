package pride.bank.erp.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pride.bank.erp.authentication.AuthenticationResponse;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.role.Role;
import pride.bank.erp.unit.Unit;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pride.bank.erp.helper.Constants.ADMIN_UNIT_ROLE;
import static pride.bank.erp.helper.Constants.VIEW_ALL_BRANCH_ASSETS;

/**
 * The login response must carry the unit's permissions, or the frontend cannot mirror the backend.
 *
 * <p>{@code usePermissions} builds its set from what the sign-in payload contains. The backend
 * already grants a unit's permissions in {@code User#getAuthorities()}, so if they do not survive
 * serialisation the two halves disagree in the worst direction: the page hides a control the user is
 * entitled to, the endpoint would have allowed it, and nothing on screen explains the mismatch.
 *
 * <p>Cheap to break by accident — a {@code @JsonIgnore} added to {@code Unit.roles}, or a DTO
 * introduced between the entity and the response — and silent when it happens, which is why it is
 * pinned rather than assumed.
 */
class UnitRoleSerialisationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aUnitsPermissionsReachTheClient() throws Exception {
        Permission crossBranch = Permission.builder().id(1L).name(VIEW_ALL_BRANCH_ASSETS).build();

        Role unitRole = Role.builder()
                .name(ADMIN_UNIT_ROLE)
                .permissions(new HashSet<>(Set.of(crossBranch)))
                .build();

        Unit unit = Unit.builder()
                .id(1L)
                .name("Admin Unit")
                .roles(new HashSet<>(Set.of(unitRole)))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                AuthenticationResponse.builder().unit(unit).build()));

        JsonNode roles = json.path("unit").path("roles");
        assertThat(roles.isArray())
                .as("unit.roles must be serialised — the frontend union reads it by that path")
                .isTrue();
        assertThat(roles).hasSize(1);

        JsonNode permissions = roles.get(0).path("permissions");
        assertThat(permissions.isArray()).isTrue();
        assertThat(permissions.get(0).path("name").asText())
                .as("the permission name is what usePermissions collects")
                .isEqualTo(VIEW_ALL_BRANCH_ASSETS);
    }

    /** A user in no unit must serialise cleanly rather than blowing up or inventing an empty one. */
    @Test
    void aUserWithNoUnitIsUnaffected() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                AuthenticationResponse.builder().build()));

        assertThat(json.path("unit").isNull() || json.path("unit").isMissingNode()).isTrue();
    }
}
