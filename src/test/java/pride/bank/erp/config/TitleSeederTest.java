/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pride.bank.erp.config.seed.OneTimeSeed;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.atLeast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleSeederTest {
    @Mock
    private TitleRepository titleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OneTimeSeed oneTimeSeed;

    @InjectMocks
    private TitleSeeder titleSeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TitleSeeder seeder() {
        return new TitleSeeder(titleRepository, roleRepository, objectMapper, userRepository, oneTimeSeed);
    }

    @Test
    void seedTitlesCreatesHierarchyWithReportsToRelationships() {
        titleSeeder = seeder();
        TitleSeedNode rootSeed = new TitleSeedNode(
                "BOD",
                "Board of Director",
                "DIRECTOR",
                new ArrayList<>(List.of(
                        new TitleSeedNode(
                                "MD",
                                "Managing Director",
                                "DIRECTOR",
                                new ArrayList<>(List.of(
                                        new TitleSeedNode("ED", "Executive Director", "DIRECTOR", new ArrayList<>())
                                ))
                        )
                ))
        );

        Role directorRole = Role.builder()
                .Id(99L)
                .name("DIRECTOR")
                .permissions(new HashSet<>())
                .build();

        when(roleRepository.findByName("DIRECTOR")).thenReturn(Optional.of(directorRole));
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(any(), any()))
                .thenReturn(Optional.empty());
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        when(titleRepository.save(any(Title.class))).thenAnswer(invocation -> {
            Title title = invocation.getArgument(0);
            if (title.getId() == null) {
                title.setId((long) title.getName().hashCode());
            }
            return title;
        });

        titleSeeder.seedTitles(rootSeed);

        ArgumentCaptor<Title> titleCaptor = ArgumentCaptor.forClass(Title.class);
        verify(titleRepository, atLeast(3)).save(titleCaptor.capture());

        List<Title> savedTitles = titleCaptor.getAllValues();
        Title root = savedTitles.get(0);
        Title managingDirector = savedTitles.stream()
                .filter(title -> "Managing Director".equals(title.getName()))
                .findFirst()
                .orElseThrow();
        Title executiveDirector = savedTitles.stream()
                .filter(title -> "Executive Director".equals(title.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("Board of Director", root.getName());
        assertNull(root.getReportsTo());
        assertSame(root, managingDirector.getReportsTo());
        assertSame(managingDirector, executiveDirector.getReportsTo());
        assertEquals("BOD", root.getShortCode());
        assertEquals("MD", managingDirector.getShortCode());
                assertSame(directorRole, root.getRole());
        verify(titleRepository).findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(eq("Board of Director"), eq("BOD"));
    }

        @Test
        void seedTitlesFailsWhenReferencedRoleDoesNotExist() {
                titleSeeder = seeder();
                TitleSeedNode rootSeed = new TitleSeedNode("BOD", "Board of Director", "DIRECTOR", new ArrayList<>());

                when(roleRepository.findByName("DIRECTOR")).thenReturn(Optional.empty());

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> titleSeeder.seedTitles(rootSeed));

                assertEquals("Role 'DIRECTOR' referenced by title 'Board of Director' was not found", exception.getMessage());
        }

    /**
     * The file moves Head Credit from the ED to the MD. The reconciliation must reuse the existing row
     * where it stands - the old parent-keyed lookup created a second Head Credit here, and a second
     * copy of everything beneath it.
     */
    @Test
    void aTitleTheFileMovesIsReusedNotDuplicated() {
        titleSeeder = seeder();
        Role head = role(1L, "HEAD_OF_DEPARTMENT");
        Title md = title(10L, "Managing Director", "MD", null, head);
        Title ed = title(11L, "Executive Director", "ED", md, head);
        Title headCredit = title(12L, "Head Credit", "HC", ed, head);

        when(roleRepository.findByName(any())).thenReturn(Optional.of(head));
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull("Managing Director", "MD"))
                .thenReturn(Optional.of(md));
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        when(titleRepository.findAllByNameIgnoreCaseOrderByIdAsc("Head Credit")).thenReturn(List.of(headCredit));
        when(titleRepository.findAllByNameIgnoreCaseOrderByIdAsc("Manager Credit Recovery")).thenReturn(List.of());
        when(titleRepository.save(any(Title.class))).thenAnswer(invocation -> invocation.getArgument(0));

        titleSeeder.seedTitles(node("MD", "Managing Director",
                node("HC", "Head Credit", node("MCR", "Manager Credit Recovery"))));

        ArgumentCaptor<Title> saved = ArgumentCaptor.forClass(Title.class);
        verify(titleRepository).save(saved.capture());
        assertEquals("Manager Credit Recovery", saved.getValue().getName());
        assertSame(headCredit, saved.getValue().getReportsTo());
        assertSame(ed, headCredit.getReportsTo(), "the reconciliation does not move titles");
    }

    /**
     * The one-time step moves a title to the file's parent and removes the duplicate rows nobody holds,
     * but keeps a duplicate somebody is in.
     */
    @Test
    void theStructureStepMovesTitlesAndRemovesOnlyUnheldDuplicates() {
        titleSeeder = seeder();
        Role head = role(1L, "HEAD_OF_DEPARTMENT");
        Title md = title(10L, "Managing Director", "MD", null, head);
        Title ed = title(11L, "Executive Director", "ED", md, head);
        Title headCredit = title(12L, "Head Credit", "HC", ed, head);
        Title unheldCopy = title(13L, "Head Credit", "HC", ed, head);
        Title heldCopy = title(14L, "Head Credit", "HC", ed, head);

        when(roleRepository.findByName(any())).thenReturn(Optional.of(head));
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull("Managing Director", "MD"))
                .thenReturn(Optional.of(md));
        when(titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id("Head Credit", "HC", 10L))
                .thenReturn(Optional.empty());
        when(titleRepository.findAllByNameIgnoreCaseOrderByIdAsc("Head Credit"))
                .thenReturn(List.of(headCredit, unheldCopy, heldCopy))
                .thenReturn(List.of(headCredit, unheldCopy, heldCopy))
                .thenReturn(List.of(headCredit, heldCopy));
        when(titleRepository.findAllByNameIgnoreCaseOrderByIdAsc("Managing Director")).thenReturn(List.of(md));
        when(titleRepository.save(any(Title.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.existsByTitle_Id(13L)).thenReturn(false);
        when(userRepository.existsByActingTitle_Id(13L)).thenReturn(false);
        when(userRepository.existsByTitle_Id(14L)).thenReturn(true);
        when(titleRepository.findSubordinateTitleIds(13L)).thenReturn(List.of());

        titleSeeder.applyStructure(node("MD", "Managing Director", node("HC", "Head Credit")));

        assertSame(md, headCredit.getReportsTo());
        verify(titleRepository).delete(unheldCopy);
        verify(titleRepository, never()).delete(heldCopy);
        verify(titleRepository, never()).delete(headCredit);
    }

    /** The file itself: one place per name, and the reporting lines the May 2026 organograms draw. */
    @Test
    void titlesJsonFollowsTheMay2026Organograms() throws Exception {
        TitleSeedNode root = objectMapper.readValue(
                new org.springframework.core.io.ClassPathResource("titles.json").getInputStream(), TitleSeedNode.class);
        Map<String, String> parentOf = new HashMap<>();
        List<String> names = new ArrayList<>();
        walk(root, null, parentOf, names);

        Set<String> distinct = new HashSet<>();
        List<String> repeated = names.stream().filter(n -> !distinct.add(n.trim().toLowerCase())).toList();
        assertEquals(List.of(), repeated, "the user import finds a title by name, so each name needs one place");

        for (String head : List.of("Head Strategy, Data & Projects", "Head People & Culture", "Company Secretary/Head Legal",
                "Head Finance", "Head Risk Management", "Head Credit", "Chief Information Security Officer", "Executive Secretary")) {
            assertEquals("Managing Director", parentOf.get(head), head);
        }
        for (String head : List.of("Head Business Technology", "Head Business", "Head Operations",
                "Head Marketing and Communications", "Head Treasury", "Head Administration & Procurement")) {
            assertEquals("Executive Director", parentOf.get(head), head);
        }
        assertEquals("Board of Director", parentOf.get("Head Compliance"));
        assertEquals("Board of Director", parentOf.get("Head Internal Audit"));
        for (String rm : List.of("Relationship Manager Workplace Banking", "Relationship Manager Public Sector",
                "Relationship Manager Women & Youth")) {
            assertEquals("Manager Personal & Institutional Banking", parentOf.get(rm), rm);
        }
        for (String rm : List.of("Relationship Manager Community Banking", "Relationship Manager Group Banking",
                "Relationship Manager Micro Finance")) {
            assertEquals("Manager Micro Finance", parentOf.get(rm), rm);
        }
        assertEquals("Manager Digital Products & Bank Assurance", parentOf.get("Supervisor Bank Assurance"));
        assertEquals("Senior Procurement Officer", parentOf.get("Procurement Officer"));
        assertEquals("Senior Procurement Officer", parentOf.get("Procurement Support Officer"));
        assertEquals("Customer Experience Officers", parentOf.get("Tellers"));
        assertFalse(parentOf.containsKey("Head Cashier"), "cashiers are tellers since 2026");
        assertFalse(parentOf.containsKey("Relationship Officer Group/Comm Banking"), "two titles now");
        assertEquals("Branch Operations Manager", parentOf.get("Office Assistant"));
        assertEquals("Administration Officer", parentOf.get("Office Assistant - Head Office"));
        assertEquals("Branch Operations Manager", parentOf.get("Driver - Branch"));
        assertEquals("Fleet Officer", parentOf.get("Driver - Head Office"));
    }

    private static void walk(TitleSeedNode node, String parent, Map<String, String> parentOf, List<String> names) {
        names.add(node.getName());
        parentOf.put(node.getName(), parent);
        if (node.getSubordinates() != null) {
            node.getSubordinates().forEach(child -> walk(child, node.getName(), parentOf, names));
        }
    }

    private static TitleSeedNode node(String code, String name, TitleSeedNode... children) {
        return new TitleSeedNode(code, name, "HEAD_OF_DEPARTMENT", new ArrayList<>(List.of(children)));
    }

    private static Role role(Long id, String name) {
        return Role.builder().Id(id).name(name).permissions(new HashSet<>()).build();
    }

    private static Title title(Long id, String name, String code, Title reportsTo, Role role) {
        return Title.builder().id(id).name(name).shortCode(code).reportsTo(reportsTo).role(role).build();
    }
}