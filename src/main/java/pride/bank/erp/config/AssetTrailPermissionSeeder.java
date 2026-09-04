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
import java.util.List;
import java.util.Set;

import static pride.bank.erp.helper.Constants.READ_ASSET;
import static pride.bank.erp.helper.Constants.READ_ASSIGNMENT_HISTORY;
import static pride.bank.erp.helper.Constants.READ_REPAIR_HISTORY;

/**
 * Splits an asset's trail out from {@code READ_ASSET}, without narrowing anyone on deploy day.
 *
 * <h2>The problem this solves</h2>
 * An asset's assignment history and its repair history used to answer to {@code READ_ASSET}, so
 * every holder of that permission could read the chain of custody for every asset in reach. Those
 * are now separate permissions, which is the right model — but flipping the endpoints alone would
 * take both tabs away from everybody who has them today, and it would look like a fault rather than
 * a policy change.
 *
 * <h2>Granted once, at the moment of the split</h2>
 * The permission row's own absence is the marker. If {@code READ_ASSIGNMENT_HISTORY} does not exist
 * yet, this is the first startup after the change: every role already holding {@code READ_ASSET}
 * gets it, so nothing visible moves. If it does exist, the split has already happened and this does
 * nothing at all — so a permission an administrator revokes in Settings <b>stays revoked</b> across
 * every restart afterwards.
 *
 * <p>That is the same create-only discipline as {@code UnitRoleSeeder}, for the same reason: a
 * seeder that re-applies grants makes revocation impossible and nobody notices until an audit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetTrailPermissionSeeder {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    /** The two trail permissions, and the permission each is being carved out of. */
    private static final List<String> TRAIL_PERMISSIONS =
            List.of(READ_ASSIGNMENT_HISTORY, READ_REPAIR_HISTORY);

    @Transactional
    public void seedAssetTrailPermissions() {
        for (String name : TRAIL_PERMISSIONS) {
            if (permissionRepository.findByName(name).isPresent()) {
                // Already split. Whatever the roles hold now is the administrator's decision.
                continue;
            }

            Permission created = permissionRepository.save(Permission.builder().name(name).build());
            log.info("Created permission {} — carving the asset trail out of {}", name, READ_ASSET);
            backfillOntoAssetReaders(created);
        }
    }

    /**
     * Gives the new permission to every role that already reads assets.
     *
     * <p>Runs only on the startup that creates it. The intent is "nobody loses a tab they had
     * yesterday", not "these roles should always have this" — which is why it never runs again.
     */
    private void backfillOntoAssetReaders(Permission trailPermission) {
        int granted = 0;

        for (Role role : roleRepository.findAll()) {
            Set<Permission> held = role.getPermissions() != null
                    ? role.getPermissions() : new HashSet<>();

            boolean readsAssets = held.stream().anyMatch(p -> READ_ASSET.equals(p.getName()));
            if (!readsAssets) continue;

            held.add(trailPermission);
            role.setPermissions(held);
            roleRepository.save(role);
            granted++;
        }

        log.info("Granted {} to {} role(s) already holding {} — no one is narrowed by the split",
                trailPermission.getName(), granted, READ_ASSET);
    }
}
