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

import static pride.bank.erp.helper.Constants.RESTOCK_STORE;

/**
 * Gives the store-restock checkbox to the Branch Manager and Branch Operations Manager roles, once.
 *
 * <h2>Why a grant at all</h2>
 * Restocking used to be guessed: any request from a branch's designated BOM was treated as a
 * replenishment. That guess is gone (V30) and a restock is now asked for with RESTOCK_STORE. Shipping
 * the permission empty would leave every branch unable to restock its store on the day this deploys,
 * so the two roles the decision named (WORKFLOW.md) receive it.
 *
 * <h2>Why once</h2>
 * Through {@link pride.bank.erp.config.seed.OneTimeSeed}: after this, who holds it is the
 * administrator's decision in Settings, and a revocation there must stay revoked across restarts.
 * Runs after {@code initializePermissions()}, which creates the row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestockPermissionSeeder {

    /** Permanent. Changing this string re-runs the grant and undoes whatever Settings has since said. */
    public static final String MARKER = "RESTOCK_STORE_TO_BM_AND_BOM";

    static final Set<String> ROLES = Set.of("BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER");

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    public void seedRestockPermission() {
        oneTimeSeed.once(
                MARKER,
                "Granted RESTOCK_STORE to BRANCH_MANAGER and BRANCH_OPERATIONS_MANAGER on the startup "
                        + "that replaced the BOM-equals-replenishment guess with an explicit restock "
                        + "request. Who holds it now is the administrator's decision in Settings.",
                this::grant);
    }

    private void grant() {
        Permission restock = permissionRepository.findByName(RESTOCK_STORE).orElse(null);
        if (restock == null) {
            log.error("{} does not exist as a permission row, so it cannot be granted. Check that "
                    + "seedRestockPermission() still runs after initializePermissions().", RESTOCK_STORE);
            return;
        }
        int granted = 0;
        for (Role role : roleRepository.findAll()) {
            if (!ROLES.contains(role.getName())) continue;
            Set<Permission> held = role.getPermissions() != null ? role.getPermissions() : new HashSet<>();
            if (held.stream().anyMatch(p -> RESTOCK_STORE.equals(p.getName()))) continue;
            held.add(restock);
            role.setPermissions(held);
            roleRepository.save(role);
            granted++;
        }
        log.info("Granted {} to {} role(s)", RESTOCK_STORE, granted);
    }
}
