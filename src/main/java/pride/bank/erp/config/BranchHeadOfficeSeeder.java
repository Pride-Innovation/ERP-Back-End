/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;

import java.util.Optional;

/**
 * Flags the configured head office branch with {@code isHeadOffice = true}
 * so workflow resolution can derive {@code branchScope} from data rather
 * than a hardcoded branch id. Idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BranchHeadOfficeSeeder {

    private final BranchRepository branchRepository;

    @Value("${application.head.office.name}")
    private String headOfficeName;

    @Transactional
    public void seedHeadOfficeFlag() {
        if (headOfficeName == null || headOfficeName.isBlank()) {
            log.warn("application.head.office.name is not configured; skipping head-office flag seed");
            return;
        }

        Optional<Branch> match = branchRepository.findByName(headOfficeName);
        if (match.isEmpty()) {
            log.warn("Head office branch '{}' not found yet; skipping head-office flag seed", headOfficeName);
            return;
        }

        Branch branch = match.get();
        if (!branch.isHeadOffice()) {
            branch.setHeadOffice(true);
            branchRepository.save(branch);
            log.info("Flagged branch '{}' (id={}) as head office", branch.getName(), branch.getId());
        }
    }
}
