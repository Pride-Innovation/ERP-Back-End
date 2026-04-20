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
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private TitleSeeder titleSeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedTitlesCreatesHierarchyWithReportsToRelationships() {
        titleSeeder = new TitleSeeder(titleRepository, roleRepository, objectMapper);
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
                titleSeeder = new TitleSeeder(titleRepository, roleRepository, objectMapper);
                TitleSeedNode rootSeed = new TitleSeedNode("BOD", "Board of Director", "DIRECTOR", new ArrayList<>());

                when(roleRepository.findByName("DIRECTOR")).thenReturn(Optional.empty());

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> titleSeeder.seedTitles(rootSeed));

                assertEquals("Role 'DIRECTOR' referenced by title 'Board of Director' was not found", exception.getMessage());
        }
}