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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pride.bank.erp.issuedAsset.IssuedAssetLifecycleService;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

import java.util.List;

/**
 * Hands over, once, the issued assets of every request whose receipt was acknowledged before the
 * hand-over sat on the right step.
 *
 * <h2>What went wrong</h2>
 * The hand-over of issued assets to the requester ran on the Infra/Admin unit's acknowledgement of
 * the <em>request</em> - before anything was issued - so it never had anything to hand over. It now
 * runs on the requester's acknowledgement of receipt. Measured when this was written, over requests
 * with receipt acknowledged:
 * <ul>
 *   <li><b>Head Office, issued directly:</b> 18 assets on 5 requests "Issued", held by nobody, in no
 *       store - every Head Office request since the first.</li>
 *   <li><b>Branch, delivered by movement:</b> 141 assets on 40 requests held by the requester (the
 *       movement handed them over) but still reading "Issued" rather than "Assigned".</li>
 * </ul>
 *
 * <h2>What it does - exactly what a receipt does now</h2>
 * {@link IssuedAssetLifecycleService#markAssigned} per request, which only finishes what issuing
 * started: assets still "Issued" / "In Transit", never one somebody else holds, and for a movement
 * delivery only the status (the movement's assignment record is kept). Assets that went on to repair,
 * a store or disposal are untouched. A store restock's assets stay in the store.
 *
 * <p><b>Requests whose delivery has not completed are skipped</b>, as a receipt would now refuse them.
 * One was found: request 16, acknowledged while its delivery (movement 4) was still in transit - the
 * misplaced delivery check let that through too. Its three assets are with the courier, and completing
 * the movement is what hands them over.
 *
 * <h2>Once, each request on its own</h2>
 * Through {@link pride.bank.erp.config.seed.OneTimeSeed}. Each request is repaired in a transaction of
 * its own, so one bad record is logged and skipped rather than rolling back the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssuedAssetHandOverRepairSeeder {

    /** Permanent. Changing it re-runs the repair - harmless, as it only finishes what issuing began. */
    public static final String MARKER = "REPAIR_ISSUED_ASSET_HAND_OVER";

    private final RequestRepository requestRepository;
    private final StatusService statusService;
    private final IssuedAssetLifecycleService lifecycle;
    private final PlatformTransactionManager transactionManager;
    private final pride.bank.erp.config.seed.OneTimeSeed oneTimeSeed;

    public void repair() {
        oneTimeSeed.once(MARKER,
                "Handed issued assets to the requesters of requests whose receipt was acknowledged while "
                        + "the hand-over was misplaced on the unit's acknowledgement of the request. Skipped "
                        + "requests whose delivery movement had not completed.",
                this::run);
    }

    void run() {
        Status acknowledged;
        try {
            acknowledged = statusService.findByStatusCode("receiptAcknowledged");
        } catch (Exception e) {
            log.error("Hand-over repair: no receiptAcknowledged status - nothing repaired");
            return;
        }
        List<Long> requestIds = requestRepository.findAll().stream()
                .filter(r -> r.getStatus() != null && acknowledged.getId().equals(r.getStatus().getId()))
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()) && !r.isForStoreRestock())
                .map(Request::getId)
                .toList();

        TransactionTemplate perRequest = new TransactionTemplate(transactionManager);
        perRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int repaired = 0, undelivered = 0, failed = 0;
        for (Long id : requestIds) {
            try {
                Boolean done = perRequest.execute(status -> {
                    Request request = requestRepository.findById(id).orElse(null);
                    if (request == null) return false;
                    if (!lifecycle.deliveryComplete(request)) {
                        log.warn("Hand-over repair: request {} skipped - its delivery has not completed", id);
                        return null;
                    }
                    try {
                        lifecycle.markAssigned(request);
                    } catch (pride.bank.erp.exception.NotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                    return true;
                });
                if (done == null) undelivered++;
                else if (done) repaired++;
            } catch (Exception e) {
                failed++;
                log.error("Hand-over repair: request {} could not be repaired: {}", id, e.getMessage());
            }
        }
        log.info("Hand-over repair: {} request(s) handed over, {} still being delivered and skipped, {} failed",
                repaired, undelivered, failed);
    }
}
