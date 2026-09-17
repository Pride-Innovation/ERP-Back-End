/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pride.bank.erp.helper.Constants.*;

/**
 * Idempotent seeder that ensures each role has the minimum required permissions.
 * Safe to run on every startup — it only adds missing permissions, never removes existing ones.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RolePermissionSeeder {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public void seedRolePermissions() {
        assignPermissions("MANAGER", List.of(
                READ_REQUEST,
                CREATE_REQUEST,
                UPDATE_REQUEST,
                DELETE_REQUEST,
                APPROVE_REQUEST,
                REJECT_REQUEST
        ));

        assignPermissions("OFFICER", List.of(
                READ_REQUEST,
                CREATE_REQUEST,
                UPDATE_REQUEST
        ));
    }

    private void assignPermissions(String roleName, List<String> permissionNames) {
        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        if (roleOpt.isEmpty()) {
            log.warn("RolePermissionSeeder: role '{}' not found — skipping", roleName);
            return;
        }

        Role role = roleOpt.get();
        Set<Permission> existing = role.getPermissions();
        boolean changed = false;

        for (String permName : permissionNames) {
            boolean alreadyHas = existing.stream()
                    .anyMatch(p -> permName.equals(p.getName()));
            if (alreadyHas) continue;

            permissionRepository.findByName(permName).ifPresentOrElse(
                    perm -> {
                        existing.add(perm);
                        log.info("RolePermissionSeeder: granted '{}' to role '{}'", permName, roleName);
                    },
                    () -> log.warn("RolePermissionSeeder: permission '{}' not found — skipping", permName)
            );
            changed = true;
        }

        if (changed) {
            role.setPermissions(existing);
            roleRepository.save(role);
        }
    }
}
