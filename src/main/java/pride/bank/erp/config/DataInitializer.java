/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
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

    /*
     * Configurations for Super Admin
     */
    @Value("${application.super.admin.email}")
    private String email;
    @Value("${application.super.admin.first.name}")
    private String firstname;
    @Value("${application.super.admin.last.name}")
    private String lastname;
    @Value("${application.super.admin.password}")
    private String password;
    @Value("${application.super.admin.phone}")
    private String phone;
    @Value("${application.super.admin.address}")
    private String address;


    /*
     * Configurations for Admin
     */
    @Value("${application.admin.email}")
    private String emailAdmin;
    @Value("${application.admin.first.name}")
    private String firstnameAdmin;
    @Value("${application.admin.password}")
    private String passwordAdmin;
    @Value("${application.admin.phone}")
    private String phoneAdmin;
    @Value("${application.admin.address}")
    private String addressAdmin;


    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            List<Permission> permissions = initializePermissions();
            Role superAdminRole = initializeSuperAdminRole(permissions);
            initializeSuperAdminUser(superAdminRole);

            Role adminRole = initializeAdminRole(permissions);
            initializeAdminUser(adminRole);
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
                        )
                );
    }

    private void initializeSuperAdminUser(Role adminRole) {
        userRepository.findByEmail(email).orElseGet(() -> {
            User admin = User.builder()
                    .firstName(firstname)
                    .lastName(lastname)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .phone(phone)
                    .address(address)
                    .isEnabled(true)
                    .role(adminRole)
                    .branch(null)
                    .build();
            return userRepository.save(admin);
        });
    }

    private void initializeAdminUser(Role adminRole) {
        userRepository.findByEmail(emailAdmin).orElseGet(() -> {
            User admin = User.builder()
                    .firstName(firstnameAdmin)
                    .lastName(firstnameAdmin)
                    .email(emailAdmin)
                    .password(passwordEncoder.encode(passwordAdmin))
                    .phone(phoneAdmin)
                    .address(addressAdmin)
                    .isEnabled(true)
                    .role(adminRole)
                    .branch(null)
                    .build();
            return userRepository.save(admin);
        });
    }
}
