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
import static pride.bank.erp.helper.Constants.REPAIR_ASSET;

/**
 * Gives the repair movement flows back to the people who were already using them.
 *
 * <h2>What changed, and why it needs a backfill</h2>
 * {@code POST /movements/repair-transfer}, {@code /temp-replacement} and {@code /return-after-repair}
 * asked for {@code CREATE_MOVEMENT} — not by anybody's decision, but because they were written as
 * movement endpoints and took the movement permission. They are asset actions implemented as
 * movements, exactly as disposal is, and disposal already answered to {@code DISPOSE_ASSET}. So they
 * now answer to {@code REPAIR_ASSET}.
 *
 * <p>Left alone, that would take repair away from people doing it today. Measured before the change:
 * <b>8 accounts held {@code CREATE_MOVEMENT} and 4 held {@code REPAIR_ASSET}</b>, and four accounts
 * at Gulu and Mbarara held the first without the second — including the branch that raised every one
 * of the thirteen repair transfers in the database. Shipping the tightening bare would have stopped
 * the only people using the feature from using it, with a 403 as the explanation.
 *
 * <h2>Why this cannot be the absence of the permission row</h2>
 * {@link AssetTrailPermissionSeeder} and {@link UserAccessPermissionSeeder} both use their new
 * permission's own absence as the marker, because the permission is genuinely new. {@code REPAIR_ASSET}
 * has existed all along and four roles already hold it, so there is no absence to read. This goes
 * through {@link pride.bank.erp.config.seed.OneTimeSeed} instead — the mechanism that exists for
 * precisely this: a migration wearing a seeder's clothes, which must run once and never again.
 *
 * <p>Re-running it would be a real bug rather than a wasted cycle. An administrator who decides a
 * storekeeper should raise movements but not send assets away for repair would revoke
 * {@code REPAIR_ASSET} in Settings and find it back on the next restart, silently — the failure
 * CLAUDE.md records against the default dashboard scopes.
 *
 * <p><b>Ordering:</b> unlike its two neighbours this runs <em>after</em> {@code initializePermissions()},
 * and must. Both permissions have to exist as rows before a role can be given one, and that method is
 * what guarantees they do.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepairPermissionSeeder {

    /** Permanent. Changing this string re-runs the grant and undoes whatever Settings has since said. */
    private static final String MARKER = "REPAIR_ASSET_ON_MOVEMENT_REPAIR_FLOWS";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    public void seedRepairPermission() {
        oneTimeSeed.once(
                MARKER,
                "Granted REPAIR_ASSET to every role already holding CREATE_MOVEMENT, on the startup "
                        + "that moved the repair movement flows off CREATE_MOVEMENT. Nobody lost an "
                        + "action they had the day before; who holds it now is the administrator's "
                        + "decision in Settings.",
                this::grantToMovementRaisers);
    }

    private void grantToMovementRaisers() {
        Permission repairAsset = permissionRepository.findByName(REPAIR_ASSET).orElse(null);
        if (repairAsset == null) {
            // initializePermissions() creates it, and this runs after. If it is somehow absent,
            // granting nothing is the safe answer: the marker is still recorded, so this does not
            // silently re-run later and re-grant against an estate an administrator has since
            // curated. Loud, because it means the ordering in DataInitializer has been changed.
            log.error("{} does not exist as a permission row, so the repair movement flows cannot be "
                    + "backfilled. Check that seedRepairPermission() still runs after "
                    + "initializePermissions() in DataInitializer.", REPAIR_ASSET);
            return;
        }

        int granted = 0;
        for (Role role : roleRepository.findAll()) {
            Set<Permission> held = role.getPermissions() != null
                    ? role.getPermissions() : new HashSet<>();

            boolean raisesMovements = held.stream().anyMatch(p -> CREATE_MOVEMENT.equals(p.getName()));
            boolean alreadyRepairs = held.stream().anyMatch(p -> REPAIR_ASSET.equals(p.getName()));
            if (!raisesMovements || alreadyRepairs) continue;

            held.add(repairAsset);
            role.setPermissions(held);
            roleRepository.save(role);
            granted++;
        }

        log.info("Granted {} to {} role(s) already holding {} — the repair movement flows keep "
                + "exactly the audience they had", REPAIR_ASSET, granted, CREATE_MOVEMENT);
    }
}
