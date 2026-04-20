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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleSeeder {
    private static final String ROLES_RESOURCE = "roles.json";

    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedRoles() {
        try {
            ClassPathResource resource = new ClassPathResource(ROLES_RESOURCE);
            List<RoleSeedNode> roles = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });

            seedRoles(roles);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed roles from " + ROLES_RESOURCE, exception);
        }
    }

    void seedRoles(List<RoleSeedNode> roles) {
        for (RoleSeedNode roleNode : roles) {
            seedRole(roleNode);
        }
    }

    private void seedRole(RoleSeedNode roleNode) {
        roleRepository.findByName(roleNode.getName())
                .ifPresentOrElse(existingRole -> {
                    if (existingRole.getDescription() == null || !existingRole.getDescription().equals(roleNode.getDescription())) {
                        existingRole.setDescription(roleNode.getDescription());
                        roleRepository.save(existingRole);
                    }
                }, () -> roleRepository.save(
                        Role.builder()
                                .name(roleNode.getName())
                                .description(roleNode.getDescription())
                                .createDate(LocalDateTime.now())
                                .lastModified(LocalDateTime.now())
                                .createdBy(1L)
                                .lastModifiedBy(1L)
                                .permissions(new HashSet<>())
                                .build()
                ));
    }
}