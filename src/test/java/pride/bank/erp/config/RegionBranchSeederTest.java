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
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.region.Region;
import pride.bank.erp.region.RegionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionBranchSeederTest {
    @Mock
    private RegionRepository regionRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private RegionBranchSeeder regionBranchSeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedRegionsAndBranchesCreatesMissingRecords() {
        regionBranchSeeder = new RegionBranchSeeder(regionRepository, branchRepository, objectMapper);
        List<RegionBranchSeedNode> seedNodes = List.of(
                new RegionBranchSeedNode(
                        "Head Office",
                        List.of(new BranchSeedNode("0", "Head Office", "info@pridebank.co.ug", "+256(0)800 333 999"))
                )
        );

        Region savedRegion = Region.builder().id(1L).name("Head Office").build();

        when(regionRepository.findByNameIgnoreCase("Head Office")).thenReturn(Optional.empty());
        when(regionRepository.save(any(Region.class))).thenReturn(savedRegion);
        when(branchRepository.findByShortCode("0")).thenReturn(Optional.empty());
        when(branchRepository.findByNameIgnoreCase("Head Office")).thenReturn(Optional.empty());
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        regionBranchSeeder.seedRegionsAndBranches(seedNodes);

        ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository, atLeastOnce()).save(branchCaptor.capture());

        Branch savedBranch = branchCaptor.getValue();
        assertEquals("Head Office", savedBranch.getName());
        assertEquals("0", savedBranch.getShortCode());
        assertEquals("info@pridebank.co.ug", savedBranch.getEmail());
        assertEquals("+256(0)800 333 999", savedBranch.getTelephone());
        assertSame(savedRegion, savedBranch.getRegion());
        assertNull(savedBranch.getDistrict());
    }

    @Test
    void seedRegionsAndBranchesUpdatesExistingBranchWithoutDuplicating() {
        regionBranchSeeder = new RegionBranchSeeder(regionRepository, branchRepository, objectMapper);
        List<RegionBranchSeedNode> seedNodes = List.of(
                new RegionBranchSeedNode(
                        "Western",
                        List.of(new BranchSeedNode("201", "Mbarara", "info@pridebank.co.ug", "+256(0)800 333 999"))
                )
        );

        Region existingRegion = Region.builder().id(2L).name("Western").build();
        Branch existingBranch = Branch.builder()
                .id(10L)
                .name("Old Mbarara")
                .shortCode("201")
                .email("old@pridebank.co.ug")
                .telephone("000")
                .build();

        when(regionRepository.findByNameIgnoreCase("Western")).thenReturn(Optional.of(existingRegion));
        when(branchRepository.findByShortCode("201")).thenReturn(Optional.of(existingBranch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        regionBranchSeeder.seedRegionsAndBranches(seedNodes);

        assertEquals("Mbarara", existingBranch.getName());
        assertEquals("info@pridebank.co.ug", existingBranch.getEmail());
        assertEquals("+256(0)800 333 999", existingBranch.getTelephone());
        assertSame(existingRegion, existingBranch.getRegion());
        verify(branchRepository).findByShortCode(eq("201"));
    }
}