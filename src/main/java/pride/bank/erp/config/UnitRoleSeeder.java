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
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pride.bank.erp.helper.Constants.*;

/**
 * Gives the Admin and Infra units their standing, and pins every existing role to an explicit scope.
 *
 * <h2>Why this is Java and not SQL</h2>
 * The natural place for seed data is a Flyway migration, and that was the first attempt. It cannot
 * work here: {@code permissions} and {@code roles} both use {@code GenerationType.AUTO}, so
 * Hibernate hands out their ids from its own sequence table rather than the column being
 * AUTO_INCREMENT. A plain INSERT fails outright ("Field 'id' doesn't have a default value"), and
 * supplying ids by hand would let Hibernate's sequence drift until it later reused one and collided.
 *
 * <p>So V18 carries the DDL and this carries the data. Nothing is lost by the split — this runs from
 * {@code DataInitializer}, a {@code CommandLineRunner}, which starts after every migration has been
 * applied.
 *
 * <h2>Idempotent, and create-only where it matters</h2>
 * Safe on every startup. It adds what is missing and never removes: revoking a permission from
 * {@code ADMIN_UNIT} in Settings must stay revoked, so a grant already considered is not re-applied.
 * The marker for "already considered" is the unit-role link itself — once Admin Unit is linked to
 * ADMIN_UNIT, the permission set behind that role is the administrator's to shape.
 *
 * <p>The <b>default role scopes</b> are different again: they are a migration, run through
 * {@link pride.bank.erp.config.seed.OneTimeSeed} so they apply once and never again. Until that was
 * true, revoking a scope in Settings survived only until the next restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnitRoleSeeder {

    private final UnitRepository unitRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    /**
     * What a unit role starts with.
     *
     * <p>Both units get the same set, so Admin and Infra begin as peers and are then narrowed from
     * Settings — easier to get right than widening one that started too narrow, and it means neither
     * unit is silently less capable than the other because of a decision made here.
     *
     * <p>{@code VIEW_ALL_BRANCH_REQUESTS} is absent on purpose; see its javadoc in Constants.
     * {@code DASH_SCOPE_ALL} rides along so the dashboard and the module pages agree about a
     * member's reach — without it an Admin officer would see every branch's assets on the assets
     * page and only Head Office's in the headline figures.
     */
    private static final List<String> UNIT_ROLE_PERMISSIONS = List.of(
            VIEW_ALL_BRANCH_ASSETS,
            MANAGE_ALL_BRANCH_ASSETS,
            VIEW_ALL_BRANCH_MOVEMENTS,
            MANAGE_ALL_BRANCH_MOVEMENTS,
            VIEW_ALL_BRANCH_INVENTORY,
            MANAGE_ALL_BRANCH_INVENTORY,
            DASH_SCOPE_ALL
    );

    /** Every multiplier, seeded whether or not anything holds it yet, so Settings can offer them. */
    private static final List<String> ALL_MULTIPLIERS = List.of(
            VIEW_ALL_BRANCH_ASSETS, MANAGE_ALL_BRANCH_ASSETS,
            VIEW_ALL_BRANCH_REQUESTS, MANAGE_ALL_BRANCH_REQUESTS,
            VIEW_ALL_BRANCH_MOVEMENTS, MANAGE_ALL_BRANCH_MOVEMENTS,
            VIEW_ALL_BRANCH_INVENTORY, MANAGE_ALL_BRANCH_INVENTORY
    );

    /**
     * Roles that keep sight of their whole duty station.
     *
     * <p>Seeded because the officer default becomes "own records only". Anyone whose reach was
     * previously *implied* — by holding a read permission and having a branch — must be granted it
     * explicitly, or they are narrowed on the day this deploys having changed nothing themselves.
     */
    private static final List<String> BRANCH_SCOPED_ROLES = List.of(
            "BRANCH_MANAGER", "BRANCH_OPERATIONS_MANAGER", "MANAGER", "HEAD_OF_DEPARTMENT");

    /** Roles confined to their own records. The intended new default for an officer. */
    private static final List<String> SELF_SCOPED_ROLES = List.of("OFFICER", "SUPERVISOR");

    /**
     * Marker for the one-time default-scope grant. Permanent — changing it re-runs the migration and
     * undoes every scope decision an administrator has made since.
     */
    private static final String DEFAULT_ROLE_SCOPES = "unit-role-seeder:default-role-scopes";

    @Transactional
    public void seedUnitRoles() {
        ALL_MULTIPLIERS.forEach(this::ensurePermission);

        Role adminUnitRole = ensureUnitRole(ADMIN_UNIT_ROLE, "Cross-branch reach for members of the Admin Unit");
        Role infraUnitRole = ensureUnitRole(INFRA_UNIT_ROLE, "Cross-branch reach for members of the Infra Unit");

        attachRoleToUnit("Admin Unit", adminUnitRole);
        attachRoleToUnit("Infra Unit", infraUnitRole);

        /*
         * The default scopes are a migration, not a reconciliation, so they run exactly once.
         *
         * These grants exist to stop anyone being narrowed on the day the scope model shipped:
         * reach used to be implied by holding a read permission and having a duty station, and once
         * "no explicit scope" came to mean SELF, that implication was gone. Granting the scope
         * outright preserved what each role had.
         *
         * That is a one-day job. Re-applying it on every startup meant revoking DASH_SCOPE_BRANCH
         * from MANAGER in Settings worked until the next restart and then quietly came back — the
         * permission was assignable but not revokable, and nothing said so.
         */
        oneTimeSeed.once(
                DEFAULT_ROLE_SCOPES,
                "Granted each seeded role its default dashboard scope so the scope model could ship "
                        + "without narrowing anyone. Scopes are the administrator's to change from "
                        + "Settings after this.",
                () -> {
                    BRANCH_SCOPED_ROLES.forEach(role -> grantIfRoleExists(role, DASH_SCOPE_BRANCH));
                    SELF_SCOPED_ROLES.forEach(role -> grantIfRoleExists(role, DASH_SCOPE_SELF));
                });

        log.info("Unit role seeding complete");
    }

    private Permission ensurePermission(String name) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    log.info("Creating permission: {}", name);
                    return permissionRepository.save(Permission.builder().name(name).build());
                });
    }

    /**
     * The role a unit confers, created with its starting permissions the first time only.
     *
     * <p>If it already exists its permissions are left exactly as they are. That is the whole point
     * of create-only here: an administrator who revoked {@code MANAGE_ALL_BRANCH_ASSETS} from Infra
     * must not find it back after the next restart.
     */
    private Role ensureUnitRole(String name, String description) {
        Optional<Role> existing = roleRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        Set<Permission> permissions = new HashSet<>();
        UNIT_ROLE_PERMISSIONS.forEach(p -> permissions.add(ensurePermission(p)));

        log.info("Creating unit role {} with {} permission(s)", name, permissions.size());
        return roleRepository.save(Role.builder()
                .name(name)
                .description(description)
                .permissions(permissions)
                .createDate(LocalDateTime.now())
                .lastModified(LocalDateTime.now())
                .createdBy(1L)
                .lastModifiedBy(1L)
                .build());
    }

    /** Links a role to a unit by name. No-op when the unit is absent or already linked. */
    private void attachRoleToUnit(String unitName, Role role) {
        List<Unit> units = unitRepository.findAll().stream()
                .filter(u -> unitName.equalsIgnoreCase(u.getName()))
                .toList();

        if (units.isEmpty()) {
            log.warn("Unit '{}' not found — skipping role attachment", unitName);
            return;
        }

        for (Unit unit : units) {
            Set<Role> roles = unit.getRoles() != null ? unit.getRoles() : new HashSet<>();
            boolean alreadyLinked = roles.stream()
                    .anyMatch(r -> r.getId() != null && r.getId().equals(role.getId()));
            if (alreadyLinked) continue;

            roles.add(role);
            unit.setRoles(roles);
            unitRepository.save(unit);
            log.info("Attached role {} to unit '{}'", role.getName(), unit.getName());
        }
    }

    /**
     * Adds one permission to a role that already exists.
     *
     * <p>Additive only, like {@code RolePermissionSeeder}: it never removes, so an administrator's
     * revocation survives a restart.
     */
    private void grantIfRoleExists(String roleName, String permissionName) {
        roleRepository.findByName(roleName).ifPresent(role -> {
            Set<Permission> held = role.getPermissions() != null ? role.getPermissions() : new HashSet<>();
            boolean alreadyHas = held.stream().anyMatch(p -> permissionName.equals(p.getName()));
            if (alreadyHas) return;

            held.add(ensurePermission(permissionName));
            role.setPermissions(held);
            roleRepository.save(role);
            log.info("Granted {} to role {}", permissionName, roleName);
        });
    }
}
