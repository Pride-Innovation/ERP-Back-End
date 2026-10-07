/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.config.seed.OneTimeSeed;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusRepository;

/**
 * Moves loaners already on the shelf onto their own status, once.
 *
 * <p>Pool stock used to be stamped "Available for Issuance", the status of stock waiting to be issued
 * against a request - so a loaner was indistinguishable from it on the register, and issuance, which
 * checks only that status, would issue one permanently. Loaners now read {@code loanAvailable}. This
 * relabels the ones that existed before that: flagged as pool, held by nobody, and still reading
 * {@code issuanceAvailable}. A loaner out with somebody reads "Asset Assigned" and is left alone; it
 * comes back as {@code loanAvailable} when returned.
 *
 * <p>Through {@link OneTimeSeed} because it is a migration, not a reconciliation: re-running it would
 * relabel an asset somebody had deliberately set back to Available for Issuance by hand. A seeder
 * rather than SQL because the status row it points at is created by {@link StatusSeeder} at startup,
 * after Flyway - so it must run after {@code seedStatuses()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanerStatusSeeder {

    /** Permanent. Changing it re-runs the relabel. */
    private static final String MARKER = "LOANERS_ON_LOAN_AVAILABLE_STATUS";

    private final OneTimeSeed oneTimeSeed;
    private final AssetRepository assetRepository;
    private final StatusRepository statusRepository;

    @Transactional
    public void seedLoanerStatus() {
        oneTimeSeed.once(MARKER,
                "Relabelled unassigned pool stock from Available for Issuance to Available for Loan, "
                        + "so a loaner can no longer be mistaken for, or issued as, stock awaiting issuance.",
                this::relabel);
    }

    private void relabel() {
        Status loan = statusRepository.findByStatus("loanAvailable").orElse(null);
        if (loan == null) {
            log.error("Status loanAvailable does not exist; run seedStatuses() before seedLoanerStatus().");
            return;
        }
        int moved = 0;
        for (Asset asset : assetRepository.findAll()) {
            boolean shelvedLoaner = asset.isTemporaryPool()
                    && asset.getAssignedTo() == null
                    && !Boolean.TRUE.equals(asset.getDeleted())
                    && asset.getAssetStatus() != null
                    && "issuanceAvailable".equals(asset.getAssetStatus().getStatus());
            if (!shelvedLoaner) continue;
            asset.setAssetStatus(loan);
            assetRepository.save(asset);
            moved++;
        }
        log.info("Relabelled {} shelved loaner(s) to Available for Loan", moved);
    }
}
