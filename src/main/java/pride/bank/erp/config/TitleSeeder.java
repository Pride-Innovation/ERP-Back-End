/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TitleSeeder {
    private static final String TITLES_RESOURCE = "titles.json";

    private final TitleRepository titleRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedTitles() {
        try {
            ClassPathResource resource = new ClassPathResource(TITLES_RESOURCE);
            TitleSeedNode rootTitle = objectMapper.readValue(resource.getInputStream(), TitleSeedNode.class);

            seedTitles(rootTitle);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed titles from " + TITLES_RESOURCE, exception);
        }
    }

    void seedTitles(TitleSeedNode rootTitle) {
        seedTitleHierarchy(rootTitle, null);
    }

    private void seedTitleHierarchy(TitleSeedNode node, Title reportsTo) {
        Title currentTitle = findOrCreateTitle(node, reportsTo);

        if (node.getSubordinates() == null) {
            return;
        }

        for (TitleSeedNode subordinate : node.getSubordinates()) {
            seedTitleHierarchy(subordinate, currentTitle);
        }
    }

    private Title findOrCreateTitle(TitleSeedNode node, Title reportsTo) {
        Role role = resolveRole(node);
        Optional<Title> existingTitle = reportsTo == null
                ? titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(node.getName(), node.getShortCode())
                : titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(node.getName(), node.getShortCode(), reportsTo.getId());

        if (existingTitle.isPresent()) {
            Title title = existingTitle.get();
            boolean requiresUpdate = false;

            if (title.getRole() == null || !title.getRole().getName().equals(role.getName())) {
                title.setRole(role);
                requiresUpdate = true;
            }

            if (title.getShortCode() == null || !title.getShortCode().equals(node.getShortCode())) {
                title.setShortCode(node.getShortCode());
                requiresUpdate = true;
            }

            if (requiresUpdate) {
                return titleRepository.save(title);
            }

            return title;
        }

        return titleRepository.save(
                Title.builder()
                        .name(node.getName())
                        .shortCode(node.getShortCode())
                        .reportsTo(reportsTo)
                .role(role)
                        .build()
        );
    }

        private Role resolveRole(TitleSeedNode node) {
        if (node.getRole() == null || node.getRole().isBlank()) {
            throw new IllegalStateException("Title '" + node.getName() + "' is missing a role in " + TITLES_RESOURCE);
        }

        return roleRepository.findByName(node.getRole())
            .orElseThrow(() -> new IllegalStateException(
                "Role '" + node.getRole() + "' referenced by title '" + node.getName() + "' was not found"
            ));
    }
}