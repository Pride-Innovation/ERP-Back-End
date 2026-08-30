/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.district.District;
import pride.bank.erp.district.DistrictRepository;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static pride.bank.erp.helper.Constants.ADMIN;
import static pride.bank.erp.helper.Constants.VIEW_ALL_BRANCHES;
import static pride.bank.erp.helper.Constants.DASH_VIEW_ASSETS;
import static pride.bank.erp.helper.Constants.DASH_VIEW_REQUESTS;
import static pride.bank.erp.helper.Constants.DASH_VIEW_STOCK;
import static pride.bank.erp.helper.Constants.DASH_VIEW_MOVEMENTS;
import static pride.bank.erp.helper.Constants.DASH_SCOPE_SELF;
import static pride.bank.erp.helper.Constants.DASH_SCOPE_BRANCH;
import static pride.bank.erp.helper.Constants.DASH_SCOPE_ALL;
import static pride.bank.erp.helper.Constants.READ_STOCK_TAKE;
import static pride.bank.erp.helper.Constants.PERFORM_STOCK_TAKE;
import static pride.bank.erp.helper.Constants.APPROVE_STOCK_TAKE;
import static pride.bank.erp.helper.Constants.ACKNOWLEDGE_REQUEST;
import static pride.bank.erp.helper.Constants.APPROVE_ISSUANCE;
import static pride.bank.erp.helper.Constants.APPROVE_REQUEST;
import static pride.bank.erp.helper.Constants.CREATE_ASSET;
import static pride.bank.erp.helper.Constants.CREATE_AUDIT;
import static pride.bank.erp.helper.Constants.CREATE_INVENTORY;
import static pride.bank.erp.helper.Constants.CREATE_PERMISSION;
import static pride.bank.erp.helper.Constants.CREATE_REQUEST;
import static pride.bank.erp.helper.Constants.CREATE_ROLE;
import static pride.bank.erp.helper.Constants.CREATE_SETTING;
import static pride.bank.erp.helper.Constants.CREATE_STATUS;
import static pride.bank.erp.helper.Constants.CREATE_STORE;
import static pride.bank.erp.helper.Constants.CREATE_TRANSPORT;
import static pride.bank.erp.helper.Constants.CREATE_USER;
import static pride.bank.erp.helper.Constants.DELETE_ASSET;
import static pride.bank.erp.helper.Constants.DELETE_AUDIT;
import static pride.bank.erp.helper.Constants.DELETE_INVENTORY;
import static pride.bank.erp.helper.Constants.DELETE_PERMISSION;
import static pride.bank.erp.helper.Constants.DELETE_REQUEST;
import static pride.bank.erp.helper.Constants.DELETE_ROLE;
import static pride.bank.erp.helper.Constants.DELETE_SETTING;
import static pride.bank.erp.helper.Constants.DELETE_STATUS;
import static pride.bank.erp.helper.Constants.DELETE_STORE;
import static pride.bank.erp.helper.Constants.DELETE_TRANSPORT;
import static pride.bank.erp.helper.Constants.DELETE_USER;
import static pride.bank.erp.helper.Constants.ISSUE_ITEMS;
import static pride.bank.erp.helper.Constants.READ_ASSET;
import static pride.bank.erp.helper.Constants.READ_AUDIT;
import static pride.bank.erp.helper.Constants.READ_INVENTORY;
import static pride.bank.erp.helper.Constants.READ_PERMISSION;
import static pride.bank.erp.helper.Constants.READ_REQUEST;
import static pride.bank.erp.helper.Constants.READ_ROLE;
import static pride.bank.erp.helper.Constants.READ_SETTING;
import static pride.bank.erp.helper.Constants.READ_STORE;
import static pride.bank.erp.helper.Constants.READ_TRANSPORT;
import static pride.bank.erp.helper.Constants.READ_USER;
import static pride.bank.erp.helper.Constants.REJECT_REQUEST;
import static pride.bank.erp.helper.Constants.SUPER_ADMIN;
import static pride.bank.erp.helper.Constants.UPDATE_ASSET;
import static pride.bank.erp.helper.Constants.REASSIGN_ASSET;
import static pride.bank.erp.helper.Constants.REPAIR_ASSET;
import static pride.bank.erp.helper.Constants.RECEIVE_ASSET_IN_STORE;
import static pride.bank.erp.helper.Constants.DISPOSE_ASSET;
import static pride.bank.erp.helper.Constants.IMPORT_ASSET;
import static pride.bank.erp.helper.Constants.EXPORT_ASSET;
import static pride.bank.erp.helper.Constants.READ_MOVEMENT;
import static pride.bank.erp.helper.Constants.CREATE_MOVEMENT;
import static pride.bank.erp.helper.Constants.DISPATCH_MOVEMENT;
import static pride.bank.erp.helper.Constants.RECEIVE_MOVEMENT;
import static pride.bank.erp.helper.Constants.CANCEL_MOVEMENT;
import static pride.bank.erp.helper.Constants.APPROVE_MOVEMENT;
import static pride.bank.erp.helper.Constants.UPDATE_AUDIT;
import static pride.bank.erp.helper.Constants.UPDATE_INVENTORY;
import static pride.bank.erp.helper.Constants.UPDATE_PERMISSION;
import static pride.bank.erp.helper.Constants.UPDATE_REQUEST;
import static pride.bank.erp.helper.Constants.UPDATE_ROLE;
import static pride.bank.erp.helper.Constants.UPDATE_SETTING;
import static pride.bank.erp.helper.Constants.UPDATE_STATUS;
import static pride.bank.erp.helper.Constants.UPDATE_STORE;
import static pride.bank.erp.helper.Constants.UPDATE_TRANSPORT;
import static pride.bank.erp.helper.Constants.UPDATE_USER;

@Component
@RequiredArgsConstructor
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DistrictRepository districtRepository;
    private final ObjectMapper objectMapper;
    private final TitleRepository titleRepository;
    private final BranchRepository branchRepository;
    private final RegionBranchSeeder regionBranchSeeder;
    private final DepartmentSeeder departmentSeeder;
    private final UnitSeeder unitSeeder;
    private final AssetTypeCommoditySeeder assetTypeCommoditySeeder;
    private final SupplierCommoditySeeder supplierCommoditySeeder;
    private final RoleSeeder roleSeeder;
    private final RolePermissionSeeder rolePermissionSeeder;
    private final TitleSeeder titleSeeder;
    private final BranchHeadOfficeSeeder branchHeadOfficeSeeder;
    private final AssetCategoryFeatureSeeder assetCategoryFeatureSeeder;
    private final StatusSeeder statusSeeder;
    private final StoreSeeder storeSeeder;

    @Value("${application.super.admin.email}")
    private String email;
    @Value("${application.super.admin.first.name}")
    private String firstname;
    @Value("${application.super.admin.last.name}")
    private String lastname;
    @Value("${application.super.admin.password}")
    private String password;

    @Value("${application.admin.email}")
    private String emailAdmin;
    @Value("${application.admin.first.name}")
    private String firstnameAdmin;
    @Value("${application.admin.password}")
    private String passwordAdmin;

    @Value("${application.head.office.name}")
    private String headOfficeName;
    @Value("${application.head.office.email}")
    private String headOfficeEmail;
    @Value("${application.head.office.telephone}")
    private String headOfficeTelephone;


    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            List<Permission> permissions = initializePermissions();
            Role superAdminRole = initializeSuperAdminRole(permissions);
            Role adminRole = initializeAdminRole(permissions);
            regionBranchSeeder.seedRegionsAndBranches();
            departmentSeeder.seedDepartments();
            unitSeeder.seedUnits();
            Branch headOfficeBranch = initializeBranch();
            Title superAdminTitle = initializeTitle("Super Admin Title", superAdminRole, headOfficeBranch);
            Title adminTitle = initializeTitle("Admin Title", adminRole, headOfficeBranch);

            initializeSuperAdminUser(superAdminTitle, headOfficeBranch);
            initializeAdminUser(adminTitle, headOfficeBranch);

            initializeDistricts();
            assetTypeCommoditySeeder.seedAssetTypesAndCommodities();
            supplierCommoditySeeder.seedSuppliersAndCommodityLinks();
            roleSeeder.seedRoles();
            rolePermissionSeeder.seedRolePermissions();
            titleSeeder.seedTitles();

            // Phase F backfills (idempotent — only fill fields that are unset).
            branchHeadOfficeSeeder.seedHeadOfficeFlag();
            assetCategoryFeatureSeeder.seedFeatures();
            statusSeeder.seedStatuses();

            // Phase 1 (Store foundation): typed stores per location + legacy balance backfill.
            storeSeeder.seedStoresAndMigrateBalances();
        };
    }

    private List<Permission> initializePermissions() {
        return Arrays.asList(
                ensurePermission(READ_PERMISSION),
                ensurePermission(CREATE_PERMISSION),
                ensurePermission(UPDATE_PERMISSION),
                ensurePermission(DELETE_PERMISSION),
                ensurePermission(READ_ROLE),
                ensurePermission(CREATE_ROLE),
                ensurePermission(UPDATE_ROLE),
                ensurePermission(DELETE_ROLE),
                ensurePermission(CREATE_USER),
                ensurePermission(READ_USER),
                ensurePermission(UPDATE_USER),
                ensurePermission(DELETE_USER),
                ensurePermission(READ_REQUEST),
                ensurePermission(CREATE_REQUEST),
                ensurePermission(DELETE_REQUEST),
                ensurePermission(UPDATE_REQUEST),
                ensurePermission(CREATE_STATUS),
                ensurePermission(DELETE_STATUS),
                ensurePermission(UPDATE_STATUS),
                // Transport Permissions
                ensurePermission(READ_TRANSPORT),
                ensurePermission(CREATE_TRANSPORT),
                ensurePermission(DELETE_TRANSPORT),
                ensurePermission(UPDATE_TRANSPORT),
                // Inventory
                ensurePermission(READ_INVENTORY),
                ensurePermission(CREATE_INVENTORY),
                ensurePermission(DELETE_INVENTORY),
                ensurePermission(UPDATE_INVENTORY),
                // Settings
                ensurePermission(READ_SETTING),
                ensurePermission(CREATE_SETTING),
                ensurePermission(DELETE_SETTING),
                ensurePermission(UPDATE_SETTING),
                // Audit Trails
                ensurePermission(READ_AUDIT),
                ensurePermission(CREATE_AUDIT),
                ensurePermission(DELETE_AUDIT),
                ensurePermission(UPDATE_AUDIT),
                // Store
                ensurePermission(READ_STORE),
                ensurePermission(CREATE_STORE),
                ensurePermission(DELETE_STORE),
                ensurePermission(UPDATE_STORE),
                // Cross-branch visibility
                ensurePermission(VIEW_ALL_BRANCHES),
                // Stock take
                ensurePermission(READ_STOCK_TAKE),
                ensurePermission(PERFORM_STOCK_TAKE),
                ensurePermission(APPROVE_STOCK_TAKE),
                // Asset
                ensurePermission(READ_ASSET),
                ensurePermission(CREATE_ASSET),
                ensurePermission(DELETE_ASSET),
                ensurePermission(UPDATE_ASSET),
                // Asset lifecycle actions — see Constants for why these are not folded into
                // CREATE_ASSET / UPDATE_ASSET.
                ensurePermission(REASSIGN_ASSET),
                ensurePermission(REPAIR_ASSET),
                ensurePermission(RECEIVE_ASSET_IN_STORE),
                ensurePermission(DISPOSE_ASSET),
                ensurePermission(IMPORT_ASSET),
                ensurePermission(EXPORT_ASSET),
                // Movements and consignments — see Constants for why dispatch and receive are not
                // folded into create.
                ensurePermission(READ_MOVEMENT),
                ensurePermission(CREATE_MOVEMENT),
                ensurePermission(DISPATCH_MOVEMENT),
                ensurePermission(RECEIVE_MOVEMENT),
                ensurePermission(CANCEL_MOVEMENT),
                ensurePermission(APPROVE_MOVEMENT),
                // Request Approvals and Acknowledgement
                ensurePermission(APPROVE_REQUEST),
                ensurePermission(REJECT_REQUEST),
                ensurePermission(ISSUE_ITEMS),
                ensurePermission(ACKNOWLEDGE_REQUEST),
                ensurePermission(APPROVE_ISSUANCE),
                // Dashboard — subject and scope. Separate from the READ_* permissions above
                // because "may you open this page" and "how much may you see summarised" are
                // different questions; see Constants for the reasoning.
                ensurePermission(DASH_VIEW_ASSETS),
                ensurePermission(DASH_VIEW_REQUESTS),
                ensurePermission(DASH_VIEW_STOCK),
                ensurePermission(DASH_VIEW_MOVEMENTS),
                ensurePermission(DASH_SCOPE_SELF),
                ensurePermission(DASH_SCOPE_BRANCH),
                ensurePermission(DASH_SCOPE_ALL)
        );
    }

    private Permission ensurePermission(String permissionName) {
        return permissionRepository.findByName(permissionName)
                .orElseGet(() -> permissionRepository.save(
                        new Permission(null, permissionName, new HashSet<>()))
                );
    }

    /**
     * Ensures the super-administrator role exists <b>and holds every permission</b>.
     *
     * <p>Reconciled on every startup, not only at creation. Previously this was a plain
     * {@code orElseGet}, so any permission added after the role first existed was created in the
     * permissions table but never attached to it — the super administrator silently lacked each new
     * capability, and the feature guarded by it simply returned "forbidden" with no clue why.
     */
    private Role initializeSuperAdminRole(List<Permission> permissions) {
        Role role = roleRepository.findByName(SUPER_ADMIN)
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(SUPER_ADMIN)
                                .description("Super administrator role")
                                .createDate(LocalDateTime.now())
                                .lastModified(LocalDateTime.now())
                                .createdBy(1L)
                                .lastModifiedBy(1L)
                                .permissions(new HashSet<>(permissions))
                                .build()));

        Set<Permission> held = role.getPermissions() != null ? role.getPermissions() : new HashSet<>();
        List<Permission> missing = permissions.stream()
                .filter(p -> held.stream().noneMatch(h -> h.getId().equals(p.getId())))
                .toList();

        if (!missing.isEmpty()) {
            held.addAll(missing);
            role.setPermissions(held);
            role = roleRepository.save(role);
            log.info("Granted {} newly-added permission(s) to {}: {}",
                    missing.size(), SUPER_ADMIN, missing.stream().map(Permission::getName).toList());
        }
        return role;
    }

    private Role initializeAdminRole(List<Permission> permissions) {
        log.info("Permissions {}", permissions);
        return roleRepository.findByName(ADMIN)
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(ADMIN)
                                .description("Application administrator role")
                                .createDate(LocalDateTime.now())
                                .lastModified(LocalDateTime.now())
                                .createdBy(1L)
                                .lastModifiedBy(1L)
                                .permissions(new HashSet<>(Arrays.asList(
                                        ensurePermission(READ_USER),
                                        ensurePermission(CREATE_USER),
                                        ensurePermission(UPDATE_USER),
                                        ensurePermission(READ_REQUEST),
                                        ensurePermission(CREATE_REQUEST),
                                        ensurePermission(UPDATE_REQUEST),
                                        ensurePermission(DELETE_REQUEST)
                                )))
                                .build()
                ));
    }

    private Branch initializeBranch() {
        return branchRepository.findByName(headOfficeName)
                .orElseGet(() -> branchRepository.save(
                        Branch.builder()
                                .name(headOfficeName)
                                .shortCode("0")
                                .telephone(headOfficeTelephone)
                                .email(headOfficeEmail)
                                .build()
                ));
    }

    private Title initializeTitle(String titleName, Role role, Branch branch) {
        return titleRepository.findByName(titleName)
                .orElseGet(() -> titleRepository.save(
                        Title.builder()
                                .name(titleName)
                                .role(role)
                                .branch(branch)
                                .build()
                ));
    }

    private void initializeSuperAdminUser(Title title, Branch branch) {
        userRepository.findByEmail(email).orElseGet(() -> {
            User superAdmin = User.builder()
                    .firstName(firstname)
                    .lastName(lastname)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .staffNumber("P00001B")
                    .gender("Male")
                    .availability("present")
                    .branch(branch)
                    .isEnabled(true)
                    .isAccountNonLocked(true)
                    .blocked(false)
                    .title(title)
                    .build();
            return userRepository.save(superAdmin);
        });
    }

    private void initializeAdminUser(Title title, Branch branch) {
        userRepository.findByEmail(emailAdmin).orElseGet(() -> {
            User admin = User.builder()
                    .firstName(firstnameAdmin)
                    .lastName(firstnameAdmin)
                    .email(emailAdmin)
                    .password(passwordEncoder.encode(passwordAdmin))
                    .isEnabled(true)
                    .isAccountNonLocked(true)
                    .staffNumber("P00001C")
                    .branch(branch)
                    .gender("Male")
                    .availability("present")
                    .blocked(false)
                    .title(title)
                    .build();
            return userRepository.save(admin);
        });
    }

    public void initializeDistricts() {
        try {
            var resource = new ClassPathResource("districts.json");
            List<District> districts = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
            List<District> newDistricts = districts.stream()
                    .filter(d -> districtRepository.findByName(d.getName()).isEmpty())
                    .toList();

            if (!newDistricts.isEmpty()) {
                districtRepository.saveAll(newDistricts);
            }
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

}
