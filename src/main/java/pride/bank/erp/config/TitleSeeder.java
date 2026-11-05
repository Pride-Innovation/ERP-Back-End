/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.seed.OneTimeSeed;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.user.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Seeds the title tree from {@code titles.json}.
 *
 * <p>Two passes, and the split is the point. {@link #seedTitles()} is a <b>reconciliation</b>: every
 * startup it creates the titles that do not exist yet and nothing else. A title that already exists
 * by name is reused where it stands, even if the file now places it elsewhere - so a title an
 * administrator moves in Settings stays moved, and a title the file moves does not get a second
 * row. (Before this, the lookup was name + short code + parent, so moving one title in the file
 * created a duplicate of it and of every title beneath it, while the old rows kept their holders.)
 *
 * <p>Moving existing titles to where the file puts them is a <b>migration</b>, and runs once through
 * {@link OneTimeSeed} - see {@link #STRUCTURE_MARKER}. A later restructuring of the file needs a new
 * marker, or existing databases will never see the moves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TitleSeeder {
    private static final String TITLES_RESOURCE = "titles.json";

    /**
     * Places every title where the May 2026 HR policy organograms put it, and removes the duplicate
     * rows the old parent-keyed lookup had created. Never rename: a changed marker re-runs the step.
     */
    static final String STRUCTURE_MARKER = "TITLE_STRUCTURE_HR_POLICY_MAY_2026";

    private final TitleRepository titleRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final OneTimeSeed oneTimeSeed;

    @Transactional
    public void seedTitles() {
        TitleSeedNode rootTitle = readTitles();
        seedTitles(rootTitle);
        oneTimeSeed.once(STRUCTURE_MARKER,
                "Moved titles to the May 2026 organograms; removed duplicate title rows nobody holds",
                () -> applyStructure(rootTitle));
    }

    private TitleSeedNode readTitles() {
        try {
            return objectMapper.readValue(new ClassPathResource(TITLES_RESOURCE).getInputStream(), TitleSeedNode.class);
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
        Optional<Title> existingTitle = findAtPosition(node, reportsTo);

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

        // Exists, but not where the file puts it: reuse it as it stands. Moving it is the one-time
        // step's job, and creating it again is how the duplicates came about.
        List<Title> sameName = titleRepository.findAllByNameIgnoreCaseOrderByIdAsc(node.getName());
        if (!sameName.isEmpty()) {
            return sameName.get(0);
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

    private Optional<Title> findAtPosition(TitleSeedNode node, Title reportsTo) {
        return reportsTo == null
                ? titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(node.getName(), node.getShortCode())
                : titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(node.getName(), node.getShortCode(), reportsTo.getId());
    }

    /**
     * Puts every title in the file under the parent the file names, with its role and short code, then
     * deletes the other rows sharing a name with one the file places - but only those nobody holds or
     * acts in and that have no subordinates left, so no person and no UI-made title is touched.
     */
    void applyStructure(TitleSeedNode rootTitle) {
        Set<Long> placed = new HashSet<>();
        placeHierarchy(rootTitle, null, placed);
        removeDuplicates(rootTitle, placed);
    }

    private void placeHierarchy(TitleSeedNode node, Title reportsTo, Set<Long> placed) {
        Title title = findAtPosition(node, reportsTo)
                .or(() -> titleRepository.findAllByNameIgnoreCaseOrderByIdAsc(node.getName()).stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("Title '" + node.getName() + "' was not seeded"));

        Role role = resolveRole(node);
        Long currentParentId = title.getReportsTo() == null ? null : title.getReportsTo().getId();
        Long targetParentId = reportsTo == null ? null : reportsTo.getId();
        if (!Objects.equals(currentParentId, targetParentId)
                || !role.getName().equals(title.getRole() == null ? null : title.getRole().getName())
                || !Objects.equals(node.getShortCode(), title.getShortCode())) {
            log.info("Title '{}' now reports to '{}'", title.getName(), reportsTo == null ? "nobody" : reportsTo.getName());
            title.setReportsTo(reportsTo);
            title.setRole(role);
            title.setShortCode(node.getShortCode());
            title = titleRepository.save(title);
        }
        placed.add(title.getId());

        if (node.getSubordinates() != null) {
            for (TitleSeedNode subordinate : node.getSubordinates()) {
                placeHierarchy(subordinate, title, placed);
            }
        }
    }

    private void removeDuplicates(TitleSeedNode rootTitle, Set<Long> placed) {
        Set<String> names = new HashSet<>();
        collectNames(rootTitle, names);

        // Repeat until nothing moves: a duplicate's own duplicate children go first, then it can.
        boolean removed = true;
        while (removed) {
            removed = false;
            for (String name : names) {
                for (Title title : titleRepository.findAllByNameIgnoreCaseOrderByIdAsc(name)) {
                    if (placed.contains(title.getId())) {
                        continue;
                    }
                    if (userRepository.existsByTitle_Id(title.getId()) || userRepository.existsByActingTitle_Id(title.getId())) {
                        log.warn("Duplicate title '{}' (id {}) is held by somebody - left in place", title.getName(), title.getId());
                        continue;
                    }
                    if (!titleRepository.findSubordinateTitleIds(title.getId()).isEmpty()) {
                        continue;
                    }
                    log.info("Removing duplicate title '{}' (id {})", title.getName(), title.getId());
                    titleRepository.delete(title);
                    removed = true;
                }
            }
        }
    }

    private void collectNames(TitleSeedNode node, Set<String> names) {
        names.add(node.getName());
        if (node.getSubordinates() != null) {
            node.getSubordinates().forEach(subordinate -> collectNames(subordinate, names));
        }
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
