/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSeederTest {
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleSeeder roleSeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedRolesCreatesMissingRolesFromJson() {
        roleSeeder = new RoleSeeder(roleRepository, objectMapper);
        List<RoleSeedNode> roleSeeds = List.of(
            new RoleSeedNode("MANAGER", "Manager with access to team management and reporting features."),
            new RoleSeedNode("OFFICER", "Officer with access to their own tasks and limited system features.")
        );

        when(roleRepository.findByName(any())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roleSeeder.seedRoles(roleSeeds);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, atLeastOnce()).save(roleCaptor.capture());

        Role managerRole = roleCaptor.getAllValues().stream()
                .filter(role -> "MANAGER".equals(role.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("Manager with access to team management and reporting features.", managerRole.getDescription());
        assertFalse(managerRole.getPermissions().iterator().hasNext());
    }

    @Test
    void seedRolesUpdatesDescriptionWithoutReplacingPermissions() {
        roleSeeder = new RoleSeeder(roleRepository, objectMapper);
        List<RoleSeedNode> roleSeeds = List.of(
            new RoleSeedNode("MANAGER", "Manager with access to team management and reporting features.")
        );

        Role existingManager = Role.builder()
                .Id(1L)
                .name("MANAGER")
                .description("Old")
                .build();

        when(roleRepository.findByName(any())).thenReturn(Optional.empty());
        when(roleRepository.findByName(eq("MANAGER"))).thenReturn(Optional.of(existingManager));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roleSeeder.seedRoles(roleSeeds);

        assertEquals("Manager with access to team management and reporting features.", existingManager.getDescription());
    }
}