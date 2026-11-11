/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pride.bank.erp.appnotification.AppNotificationRepository;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.appnotification.NotificationTarget;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.workflowinstance.RequestWorkflowInstanceRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the store a request will be issued from can cover it, and telling people when it cannot
 * or when it now can (Phase 3, WORKFLOW.md).
 *
 * <h2>Why</h2>
 * A request handled at its branch waits there until the branch store holds everything on it - it
 * never escalates to Head Office. A request waiting for stock looks exactly like an issuer who has not
 * acted, so the wait has to be said out loud: on the request page, to the requester and the issuer
 * when the request reaches its Issue step short, and to the issuer once a delivery covers it.
 *
 * <h2>Which store</h2>
 * The issuer's: the branch Admin store of the person the Issue step is routed to, or Head Office's
 * when it is routed to a unit. That is the store the issuance will check and debit.
 *
 * <h2>Notices go out once, after commit</h2>
 * Each notice is sent at most once per request and person, and only after the transaction that
 * caused it commits - a step that rolls back must not have told anybody, and a failing notice must
 * not undo a delivery or a step.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueStockCheckService {

    static final String WAITING = "STOCK_WAITING";
    static final String READY = "STOCK_READY";

    private final RequestRepository requestRepository;
    private final RequestCommodityRepository requestCommodityRepository;
    private final RequestWorkflowInstanceRepository instanceRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final AssetRepository assetRepository;
    private final StockAvailabilityService availability;
    private final AppNotificationService notificationService;
    private final AppNotificationRepository notificationRepository;
    private final PlatformTransactionManager transactionManager;

    public record Line(Long commodityId, String commodityName, int needed, int available) {
        public boolean shortfall() { return available < needed; }
    }

    /** What the issuing store can cover. {@code storeId} is null when no store can be determined. */
    public record StockCheck(Long storeId, String storeName, List<Line> lines, boolean waiting) {}

    /** The check for a request by id, loaded inside this transaction so its associations resolve. */
    @Transactional(readOnly = true)
    public StockCheck check(Long requestId) {
        return requestRepository.findById(requestId).map(this::check)
                .orElse(new StockCheck(null, null, List.of(), false));
    }

    @Transactional(readOnly = true)
    public StockCheck check(Request request) {
        Store store = issuingStore(request);
        if (store == null) return new StockCheck(null, null, List.of(), false);
        List<Line> lines = new ArrayList<>();
        for (RequestCommodity rc : requestCommodityRepository.findByRequestId(request.getId())) {
            Commodity c = rc.getCommodity();
            if (c == null || rc.getQuantity() <= 0) continue;
            lines.add(new Line(c.getId(), c.getName(), rc.getQuantity(),
                    availability.available(store, c) + issuableAssets(store, c)));
        }
        return new StockCheck(store.getId(), store.getName(), lines, lines.stream().anyMatch(Line::shortfall));
    }

    /**
     * The request has just reached an Issue step. If the issuing store cannot cover it, the requester
     * learns it will be handled once the store is restocked, and the issuer learns why it waits.
     */
    public void onIssueStepReached(Long requestId) {
        afterCommit(() -> {
            Request request = requestRepository.findById(requestId).orElse(null);
            if (request == null) return;
            StockCheck check = check(request);
            if (!check.waiting()) return;
            String missing = describeShortfall(check);
            notifyOnce(request.getRequester(), WAITING, request.getId(), "Request waiting for stock",
                    "Request #" + request.getId() + " is approved and will be issued once " + check.storeName()
                            + " is restocked. Short: " + missing + ".");
            notifyOnce(request.getCurrentApprover(), WAITING, request.getId(), "Request waiting for stock",
                    "Request #" + request.getId() + " is waiting for stock in " + check.storeName()
                            + ". Short: " + missing + ". Restock the store to issue it.");
        });
    }

    /**
     * Stock has landed in a store. Every request at its branch waiting at an Issue step that the store
     * can now cover is announced to its issuer, once.
     */
    public void onStockArrived(Long storeId) {
        afterCommit(() -> {
            Store store = storeRepository.findById(storeId).orElse(null);
            if (store == null || store.getStoreType() != StoreType.ADMIN || store.getDepartment() != null
                    || store.getLocation() == null) return;
            for (Request request : instanceRepository.findAtIssueStepRoutedToBranch(store.getLocation().getId())) {
                StockCheck check = check(request);
                if (check.waiting() || check.lines().isEmpty()) continue;
                notifyOnce(request.getCurrentApprover(), READY, request.getId(), "Request ready to issue",
                        "Everything on request #" + request.getId() + " is now in " + check.storeName()
                                + ". It can be issued.");
            }
        });
    }

    private Store issuingStore(Request request) {
        Branch branch = null;
        if (request.getCurrentApprover() != null) {
            branch = request.getCurrentApprover().getBranch();
        } else if (request.getCurrentUnit() != null) {
            branch = branchRepository.findFirstByIsHeadOfficeTrue().orElse(null);
        } else if (request.getRequester() != null) {
            branch = request.getRequester().getBranch();
        }
        if (branch == null) return null;
        return storeRepository.findByLocation_IdAndStoreTypeAndDepartmentIsNull(branch.getId(), StoreType.ADMIN)
                .orElse(null);
    }

    /** Serialized assets of this commodity on the shelf and free to issue. */
    private int issuableAssets(Store store, Commodity commodity) {
        int n = 0;
        for (Asset a : assetRepository.findByCurrentStore_Id(store.getId())) {
            if (a.getCommodity() == null || !a.getCommodity().getId().equals(commodity.getId())) continue;
            if (a.getAssignedTo() != null || a.isTemporaryPool()) continue;
            if (Boolean.TRUE.equals(a.getDeleted()) || Boolean.TRUE.equals(a.getDisposed())) continue;
            if (a.getAssetStatus() == null || !"issuanceAvailable".equals(a.getAssetStatus().getStatus())) continue;
            n++;
        }
        return n;
    }

    private static String describeShortfall(StockCheck check) {
        return String.join(", ", check.lines().stream().filter(Line::shortfall)
                .map(l -> l.commodityName() + " (need " + l.needed() + ", store has " + l.available() + ")")
                .toList());
    }

    private void notifyOnce(User to, String type, Long requestId, String title, String message) {
        if (to == null) return;
        if (notificationRepository.existsByRecipientIdAndTypeAndEntityId(to.getId(), type, requestId)) return;
        notificationService.createAndPush(to, title, message, type, NotificationTarget.REQUEST, requestId);
    }

    /** Runs once the current transaction commits, in a transaction of its own. Never throws. */
    private void afterCommit(Runnable work) {
        Runnable guarded = () -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(s -> work.run());
            } catch (Exception e) {
                log.warn("Stock notice could not be sent: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }
}
