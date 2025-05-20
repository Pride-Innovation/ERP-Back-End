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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.district.District;
import pride.bank.erp.district.DistrictRepository;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.region.Region;
import pride.bank.erp.region.RegionRepository;
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

import static pride.bank.erp.helper.Constants.*;

@Component
@RequiredArgsConstructor
public class DataInitializer {
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final ObjectMapper objectMapper;
    private final AssetTypeRepository assetTypeRepository;
    private final TitleRepository titleRepository;
    private final BranchRepository branchRepository;

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
            Branch headOfficeBranch = initializeBranch();
            Title superAdminTitle = initializeTitle("Super Admin Title", superAdminRole, headOfficeBranch);
            Title adminTitle = initializeTitle("Admin Title", adminRole, headOfficeBranch);

            initializeSuperAdminUser(superAdminTitle, headOfficeBranch);
            initializeAdminUser(adminTitle, headOfficeBranch);

            initializeRegions();
            initializeDistricts();
            initializeAssetTypes();
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
                ensurePermission(UPDATE_STATUS)
        );
    }

    private Permission ensurePermission(String permissionName) {
        return permissionRepository.findByName(permissionName)
                .orElseGet(() -> permissionRepository.save(
                        new Permission(null, permissionName, new HashSet<>()))
                );
    }

    private Role initializeSuperAdminRole(List<Permission> permissions) {
        return roleRepository.findByName(SUPER_ADMIN)
                .orElseGet(() -> roleRepository.save(
                        new Role(
                                null,
                                SUPER_ADMIN,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                1L,
                                1L,
                                null,
                                new HashSet<>(permissions))));
    }

    private Role initializeAdminRole(List<Permission> permissions) {
        return roleRepository.findByName(ADMIN)
                .orElseGet(() -> roleRepository.save(
                        new Role(
                                null,
                                ADMIN,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                1L,
                                1L,
                                null,
                                new HashSet<>(Arrays.asList(
                                        ensurePermission(READ_USER),
                                        ensurePermission(CREATE_USER),
                                        ensurePermission(UPDATE_USER),
                                        ensurePermission(READ_REQUEST),
                                        ensurePermission(CREATE_REQUEST),
                                        ensurePermission(UPDATE_REQUEST),
                                        ensurePermission(DELETE_REQUEST)
                                )))
                ));
    }

    private Branch initializeBranch() {
        return branchRepository.findByName(headOfficeName)
                .orElseGet(() -> branchRepository.save(
                        Branch.builder()
                                .name(headOfficeName)
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
                    .staffNumber("P00001C")
                    .branch(branch)
                    .gender("Male")
                    .availability("present")
                    .title(title)
                    .build();
            return userRepository.save(admin);
        });
    }

    private void initializeRegions() {
        List<Region> regions = Arrays.asList(
                new Region(null, Central_A, null),
                new Region(null, Central_B, null),
                new Region(null, Eastern, null),
                new Region(null, Northern, null),
                new Region(null, Western, null)
        );

        for (Region region : regions) {
            verifyRegion(region);
        }
    }

    private void verifyRegion(Region region) {
        regionRepository.findByName(region.getName()).ifPresentOrElse(
                existingRegion -> {
                },
                () -> regionRepository.save(region)
        );
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
            e.printStackTrace();
        }
    }

    public void initializeAssetTypes() {
        try {
            var resource = new ClassPathResource("assetTypes.json");
            List<AssetType> assetTypes = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
            List<AssetType> newAssets = assetTypes.stream()
                    .filter(assetType -> assetTypeRepository.findByName(assetType.getName()).isEmpty())
                    .toList();
            if (!newAssets.isEmpty()) {
                assetTypeRepository.saveAll(assetTypes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
