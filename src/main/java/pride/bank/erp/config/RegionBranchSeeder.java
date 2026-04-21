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
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.region.Region;
import pride.bank.erp.region.RegionRepository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegionBranchSeeder {
    private static final String REGIONS_AND_BRANCHES_RESOURCE = "regionsAndBranches.json";

    private final RegionRepository regionRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedRegionsAndBranches() {
        try {
            ClassPathResource resource = new ClassPathResource(REGIONS_AND_BRANCHES_RESOURCE);
            List<RegionBranchSeedNode> regions = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });

            seedRegionsAndBranches(regions);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed regions and branches from " + REGIONS_AND_BRANCHES_RESOURCE, exception);
        }
    }

    void seedRegionsAndBranches(List<RegionBranchSeedNode> regions) {
        for (RegionBranchSeedNode regionNode : regions) {
            Region region = findOrCreateRegion(regionNode.getRegion());

            if (regionNode.getBranches() == null) {
                continue;
            }

            for (BranchSeedNode branchNode : regionNode.getBranches()) {
                try {
                    Branch branch = findOrCreateBranch(branchNode, region);
                    log.info("Seeding region {} branch {}", regionNode.getRegion(), branch.getName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Region findOrCreateRegion(String regionName) {
        return regionRepository.findByNameIgnoreCase(regionName)
                .orElseGet(() -> regionRepository.save(
                        Region.builder()
                                .name(regionName)
                                .build()
                ));
    }

    private Branch findOrCreateBranch(BranchSeedNode node, Region region) {
        Optional<Branch> existingBranch = findExistingBranch(node);

        if (existingBranch.isPresent()) {
            Branch branch = existingBranch.get();
            boolean requiresUpdate = false;

            if (!Objects.equals(branch.getName(), node.getName())) {
                branch.setName(node.getName());
                requiresUpdate = true;
            }

            if (!Objects.equals(branch.getShortCode(), node.getShortCode())) {
                branch.setShortCode(node.getShortCode());
                requiresUpdate = true;
            }

            if (!Objects.equals(branch.getEmail(), node.getEmail())) {
                branch.setEmail(node.getEmail());
                requiresUpdate = true;
            }

            if (!Objects.equals(branch.getTelephone(), node.getTelephone())) {
                branch.setTelephone(node.getTelephone());
                requiresUpdate = true;
            }

            if (branch.getRegion() == null || !Objects.equals(branch.getRegion().getId(), region.getId())) {
                branch.setRegion(region);
                requiresUpdate = true;
            }

            if (requiresUpdate) {
                return branchRepository.save(branch);
            }

            return branch;
        }

        return branchRepository.save(
                Branch.builder()
                        .name(node.getName())
                        .shortCode(node.getShortCode())
                        .email(node.getEmail())
                        .telephone(node.getTelephone())
                        .region(region)
                        .district(null)
                        .branchManager(null)
                        .branchOperationsManager(null)
                        .relationshipManager(null)
                        .creditAdministrator(null)
                        .build()
        );
    }

    private Optional<Branch> findExistingBranch(BranchSeedNode node) {
        if (node.getShortCode() != null && !node.getShortCode().isBlank()) {
            Optional<Branch> branchByShortCode = branchRepository.findByShortCode(node.getShortCode());
            if (branchByShortCode.isPresent()) {
                return branchByShortCode;
            }
        }

        if (node.getName() != null && !node.getName().isBlank()) {
            return branchRepository.findByNameIgnoreCase(node.getName());
        }

        return Optional.empty();
    }
}