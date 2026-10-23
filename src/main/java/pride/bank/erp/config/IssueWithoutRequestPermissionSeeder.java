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
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.HashSet;
import java.util.Set;

import static pride.bank.erp.helper.Constants.CREATE_MOVEMENT;
import static pride.bank.erp.helper.Constants.ISSUE_WITHOUT_REQUEST;

/**
 * Gives ISSUE_WITHOUT_REQUEST, once, to every role holding CREATE_MOVEMENT.
 *
 * <h2>Why</h2>
 * Handing stock from a store straight to a person used to need only CREATE_MOVEMENT. That is now the
 * exception path, behind its own permission and a required reason (Phase 3, WORKFLOW.md). Shipping
 * the permission empty would take the path away from everyone who uses it today, so the roles that
 * could do it receive it - and from here on, who holds it is decided in Settings.
 *
 * <h2>Why once</h2>
 * Through {@link pride.bank.erp.config.seed.OneTimeSeed}: re-running would bring back a permission an
 * administrator had revoked. Runs after {@code initializePermissions()}, which creates the row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueWithoutRequestPermissionSeeder {

    /** Permanent. Changing this string re-runs the grant and undoes whatever Settings has since said. */
    public static final String MARKER = "ISSUE_WITHOUT_REQUEST_TO_CREATE_MOVEMENT_HOLDERS";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    public void seed() {
        oneTimeSeed.once(
                MARKER,
                "Granted ISSUE_WITHOUT_REQUEST to every role holding CREATE_MOVEMENT on the startup that "
                        + "put giving stock to a person without a request behind its own permission. "
                        + "Who holds it now is the administrator's decision in Settings.",
                this::grant);
    }

    private void grant() {
        Permission permission = permissionRepository.findByName(ISSUE_WITHOUT_REQUEST).orElse(null);
        if (permission == null) {
            log.error("{} does not exist as a permission row, so it cannot be granted. Check that "
                    + "this seeder still runs after initializePermissions().", ISSUE_WITHOUT_REQUEST);
            return;
        }
        int granted = 0;
        for (Role role : roleRepository.findAll()) {
            Set<Permission> held = role.getPermissions() != null ? role.getPermissions() : new HashSet<>();
            if (held.stream().noneMatch(p -> CREATE_MOVEMENT.equals(p.getName()))) continue;
            if (held.stream().anyMatch(p -> ISSUE_WITHOUT_REQUEST.equals(p.getName()))) continue;
            held.add(permission);
            role.setPermissions(held);
            roleRepository.save(role);
            granted++;
        }
        log.info("Granted {} to {} role(s)", ISSUE_WITHOUT_REQUEST, granted);
    }
}
