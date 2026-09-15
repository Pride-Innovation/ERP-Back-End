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

import java.util.HashSet;
import java.util.Set;

import static pride.bank.erp.helper.Constants.CREATE_USER;
import static pride.bank.erp.helper.Constants.MANAGE_USER_ACCESS;

/**
 * Splits the account actions out of {@code CREATE_USER}, without locking anyone out on deploy day.
 *
 * <h2>What is being separated</h2>
 * Enable, disable, block, unblock and return-from-leave answered to {@code CREATE_USER} — not by
 * anybody's decision, but because they happen to be POSTs under {@code /users/**} and inherited the
 * matcher. Creating a member of staff and suspending one are different acts: the second is the one
 * taken in a hurry, the moment somebody leaves or is suspended, and it should be grantable to
 * whoever handles that without also handing them the ability to mint accounts.
 *
 * <h2>Why it has to be backfilled, and only once</h2>
 * A new permission is held by nobody. Shipping the split without this would take all five actions
 * away from every role at once — which is exactly what happened with {@code READ_STOCK_TAKE}: it is
 * created in {@code initializePermissions()}, granted by no seeder, and so the Stock Take page was
 * offered to people every one of whose endpoints refused them. That was not role-specific; it hit
 * everybody.
 *
 * <p>The permission row's own absence is the marker, exactly as in {@link AssetTrailPermissionSeeder}.
 * On the first startup after the change, every role already holding {@code CREATE_USER} gets it and
 * nothing visible moves. On every startup afterwards this does nothing — so a permission an
 * administrator revokes in Settings <b>stays revoked</b>.
 *
 * <p><b>Ordering matters.</b> This must run <em>before</em> {@code initializePermissions()}, which
 * would otherwise create the row itself and erase the marker — leaving nobody backfilled and all
 * five actions silently unavailable. Same constraint, and same reason, as the asset-trail seeder it
 * sits beside in {@code DataInitializer}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccessPermissionSeeder {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public void seedUserAccessPermission() {
        if (permissionRepository.findByName(MANAGE_USER_ACCESS).isPresent()) {
            // Already split. Whatever the roles hold now is the administrator's decision.
            return;
        }

        Permission created = permissionRepository.save(
                Permission.builder().name(MANAGE_USER_ACCESS).build());
        log.info("Created permission {} — carving the account actions out of {}",
                MANAGE_USER_ACCESS, CREATE_USER);

        int granted = 0;
        for (Role role : roleRepository.findAll()) {
            Set<Permission> held = role.getPermissions() != null
                    ? role.getPermissions() : new HashSet<>();

            boolean createsUsers = held.stream().anyMatch(p -> CREATE_USER.equals(p.getName()));
            if (!createsUsers) continue;

            held.add(created);
            role.setPermissions(held);
            roleRepository.save(role);
            granted++;
        }

        log.info("Granted {} to {} role(s) already holding {} — nobody loses an action they had "
                + "yesterday", MANAGE_USER_ACCESS, granted, CREATE_USER);
    }
}
