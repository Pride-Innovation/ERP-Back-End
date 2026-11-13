/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuedAsset.IssuedAsset;
import pride.bank.erp.issuedAsset.IssuedAssetRepository;
import pride.bank.erp.issuedCommodity.IssuedCommodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.role.Role;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.inventory.StockAvailabilityService;
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.stock.CommodityWithDetailsDao;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssuanceService {
    private static final Logger logger = Logger.getLogger(IssuanceService.class.getName());
    // Status codes (stable across environments/re-seeds — never reference by id).
    /** Newly-stocked assets that still need their details filled in. */
    private static final String CODE_REQUIRE_UPDATE = "requireUpdate";
    /** An asset that has been issued and assigned to its requester. */
    private static final String CODE_ASSET_ASSIGNED = "assetAssigned";
    /** A request whose items have been issued (pending issuance approval). */
    private static final String CODE_ISSUED = "issued";
    /** Available for issuance — the only state from which an asset may be issued. */
    private static final String CODE_ISSUANCE_AVAILABLE = "issuanceAvailable";
    /** In transit with a courier (cross-location leg). */
    private static final String CODE_IN_TRANSIT = "inTransit";
    /** Asset-level "issued, awaiting delivery/acknowledgement" — keeps it out of the issuable pool. */
    private static final String CODE_ASSET_ISSUED = "assetIssued";

    private final UserService userService;
    private final IssuanceRepository issuanceRepository;
    private final StatusService statusService;
    private final CommodityService commodityService;
    private final IssuedCommodityRepository issuedCommodityRepository;
    private final IssuedAssetRepository issuedAssetRepository;
    private final StoreService storeService;
    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final RequestService requestService;
    private final RequestRepository requestRepository;
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    private final CurrentUserService currentUserService;
    private final Email emailService;
    private final UserRepository userRepository;
    private final UserBuilder userBuilder;
    private final StoreBalanceRepository storeBalanceRepository;
    private final WorkflowEngineService workflowEngineService;
    private final InventoryService inventoryService;
    private final StockAvailabilityService stockAvailabilityService;

    /** Head Office by its flag. A branch id of 1 is Head Office on one database and nothing elsewhere. */
    private static boolean isHeadOffice(User user) {
        return user != null && user.getBranch() != null && user.getBranch().isHeadOffice();
    }

    @Transactional
    public IssuanceDAO issueCommodities(IssuanceDTO request) throws NotFoundException {
        Status status = statusService.findByStatusCode(CODE_ISSUED);
        Request requestDetails = requestService.findRequest(request.getRequestId());
        User currentUser = currentUserService.getCurrentUser();

        // Whose request it is comes from the request, not the payload. The client sends a requester id
        // too, and trusting it let an issuance be made out to somebody other than who asked.
        User requester = requestDetails.getRequester() != null
                ? requestDetails.getRequester() : userService.findUser(request.getRequester());
        if (request.getRequester() != null && !request.getRequester().equals(requester.getId())) {
            throw new IllegalArgumentException("This issuance names a different person from the one who raised the request.");
        }

        // Maker-checker: nobody issues their own request, whatever they hold. The workflow routes the
        // step to someone else (MakerChecker); this is the guarantee behind the routing.
        if (currentUser != null && currentUser.getId().equals(requester.getId())) {
            throw new IllegalArgumentException("You cannot issue your own request. Another issuer has to issue it.");
        }
        Long issuerBranchId = currentUser.getBranch().getId();
        Long requesterBranchId = requester.getBranch().getId();

        logger.info("Processing issuance request from branch " + issuerBranchId +
                " for user in branch " + requesterBranchId);

        boolean sameLocationAssets = Objects.equals(issuerBranchId, requesterBranchId);

        // A store restock fills the requester's branch store, so it cannot be issued out of that same
        // store - the goods would leave the shelf to arrive back on it.
        if (requestDetails.isForStoreRestock() && sameLocationAssets) {
            throw new IllegalArgumentException("A store restock is issued from another branch's store - "
                    + "usually Head Office - not from the store it restocks.");
        }

        // Handle asset assignments if present — validate up front (fail fast, before the
        // Issuance record exists); the actual assignment/link is applied after the Issuance
        // is saved (see below), same ordering as commodities.
        if (request.getAssets() != null && !request.getAssets().isEmpty()) {
            validateAssetsReadyForIssuance(request.getAssets());
        }

        /*
         * Commodities are checked here and debited at approval, not here.
         *
         * Issuing used to debit the issuer's Admin store on the spot, before anyone approved the
         * issuance, and a rejection afterwards put nothing back - the store then read lower than the
         * shelf and the next stock take reported it lost. Now issuing only proves the stock is
         * available (on hand less what is already promised), and the issuance itself is the promise
         * until approval debits it (settleDirectIssuance) or hands it to the fulfilment movement.
         *
         * Head Office is told by its flag. This used to compare the branch id with 1, which is right
         * on one database and wrong on any other (trap #4).
         */
        if (request.getCommodities() != null && !request.getCommodities().isEmpty()) {

            // Check if the request contains mixed item types and validate according to business rules
            validateRequestItemTypes(request.getCommodities(), requester);

            // A branch issues whatever its own store holds. It used to be "Stationery only", matched by
            // the category's display name, which left everything else a branch stocks - printing,
            // kitchen and cleaning supplies, and anything Head Office replenished to it - impossible to
            // issue at all. What a branch handles is now decided by its workflow (Phase 3,
            // WORKFLOW.md); availability below decides whether the shelf can cover it.
            if (!isHeadOffice(currentUser)) {
                logger.info("Branch issuing items to user - branch ID: " + issuerBranchId);
            } else {
                logger.info("Head office issuing items - crossLocation: " + !isHeadOffice(requester)
                        + ", requester is BOM: " + isBranchOperationManager(requester));
            }

            // Whoever issues draws from their own branch's Admin store - directly at approval for the
            // same location, through the fulfilment movement for another.
            validateCommoditiesInStore(request.getCommodities(), issuerBranchId);
        }

        // Create and save the issuance record
        Issuance issuance = Issuance.builder()
                .status(status)
                .requester(requester)
                .comment(request.getComment())
                .request(requestDetails)
                .issuer(currentUser)
                .build();

        Issuance savedIssuance = issuanceRepository.save(issuance);
        logger.info("Created issuance record with ID: " + savedIssuance.getId());

        // Add issued commodities record if commodities exist
        if (request.getCommodities() != null && !request.getCommodities().isEmpty()) {
            addIssuedCommodities(savedIssuance, request.getCommodities());
        }

        /*
         * Link every issued asset and stamp it Issued — cross-location included.
         *
         * This comment used to say cross-location assets were "left untouched here" and picked up
         * by the fulfilment movement on receipt. That was the old behaviour and it is no longer what
         * applyAssetIssuance does, for a good reason: an asset left at "Available for Issuance"
         * while it sat on a courier's bike was still in the issuable pool, so it could be issued a
         * second time before the first delivery arrived. Live data still carries four assets in that
         * state from before the change — see the method's javadoc.
         *
         * Assignment to the requester is what defers to receipt, not the removal from the pool.
         */
        if (request.getAssets() != null && !request.getAssets().isEmpty()) {
            applyAssetIssuance(request.getAssets(), requester, savedIssuance, sameLocationAssets);
        }

        if (workflowEngineService.hasActiveInstance(requestDetails.getId())) {
            // Workflow mode: engine sets status and notifies the next approver
            workflowEngineService.processStepAction(
                    requestDetails.getId(),
                    currentUser.getId(),
                    "ISSUED",
                    request.getComment() != null ? request.getComment() : "Items have been issued."
            );
        } else {
            // Legacy mode: set status directly and notify manually
            updateRequestStatus(request);
            notifyIndividual(
                    request.getComment() != null ? request.getComment() : "Please approve this issued request",
                    requestDetails
            );
        }

        return buildIssuanceResponse(savedIssuance);
    }


    /**
     * Validates that the request follows the business rules regarding item types:
     * - Head office users can request any combination of items
     * - Branch users cannot mix stationery and non-stationery items in a single request
     */
    private void validateRequestItemTypes(List<IssuanceCommodityDTO> commodities, User requester)
            throws NotFoundException {

        // If requester is from head office, no restrictions on mixed items
        if (isHeadOffice(requester)) {
            return;
        }

        boolean hasStationery = false;
        boolean hasNonStationery = false;

        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());

            if (isStationery(commodity)) {
                hasStationery = true;
            } else {
                hasNonStationery = true;
            }

            // If we found both types in a branch user's request, throw an exception
            if (hasStationery && hasNonStationery) {
                throw new IllegalArgumentException(
                        "Branch users cannot request stationery and non-stationery items in the same request. " +
                                "Please create separate requests for different item types."
                );
            }
        }
    }

    /**
     * Checks if a commodity is of type Stationery
     *
     * @param commodity The commodity to check
     * @return true if the commodity is stationary, false otherwise
     */
    private boolean isStationery(Commodity commodity) {
        return commodity != null &&
                commodity.getAssetType() != null &&
                "Stationery".equalsIgnoreCase(commodity.getAssetType().getName());
    }

    /**
     * Check if a user is a Branch Operation Manager
     *
     * @param user The user to check
     * @return true if the user is a BOM, false otherwise
     */
    private boolean isBranchOperationManager(User user) {
        return user != null &&
                user.getTitle() != null &&
                user.getTitle().getName() != null &&
                user.getTitle().getName().contains("Branch Operation Manager");
    }


    /**
     * Validates that all commodities are available in the specified branch store
     *
     * @param commodities The commodities to validate
     * @param branchId    The branch ID to check
     * @throws NotFoundException If a commodity is not found in the store
     */
    private void validateCommoditiesInStore(List<IssuanceCommodityDTO> commodities, Long branchId)
            throws NotFoundException {
        /*
         * Against what is available, not what is on hand: stock other issuances and movements have
         * already promised is still in the balance until they are debited. The store is the branch's
         * own Admin store - the one approval debits and the fulfilment movement draws from - so the
         * check and the debit can never look at different shelves.
         *
         * A shortfall is an IllegalArgumentException (a 400). It was a NotFoundException, which this
         * application's handler answers with 200 and a bare message - a refusal the browser could
         * read as success.
         */
        Store adminStore = storeService.resolveAdminStore(branchId);
        List<Map.Entry<Commodity, Integer>> lines = new ArrayList<>();
        for (IssuanceCommodityDTO dto : commodities) {
            lines.add(Map.entry(commodityService.findCommodity(dto.getCommodityId()), dto.getQuantity()));
        }
        stockAvailabilityService.requireAvailable(adminStore, StockAvailabilityService.sumByCommodity(lines));
        logger.info("All commodities verified as available in branch " + branchId);
    }

    /**
     * Settles an issuance's stock at approval: a same-location issuance debits the issuer's branch
     * Admin store now, and a cross-location one is marked settled because its fulfilment movement
     * owns the transfer. Idempotent through {@code stockSettled}, so an issuance settled under the old
     * rule (debited at issue) is never debited again.
     *
     * @param direct whether the issuance is fulfilled in place (FulfillmentService DIRECT_ISSUANCE)
     */
    @Transactional
    public void settleIssuanceStock(Issuance issuance, boolean direct) throws NotFoundException {
        if (issuance.isStockSettled()) return;
        if (direct) {
            List<IssuedCommodity> lines = issuedCommodityRepository.findByIssuance_Id(issuance.getId());
            if (!lines.isEmpty()) {
                if (issuance.getIssuer() == null || issuance.getIssuer().getBranch() == null) {
                    throw new IllegalArgumentException("This issuance has no issuer branch, so there is no store to debit.");
                }
                Store adminStore = storeService.resolveAdminStore(issuance.getIssuer().getBranch());
                for (IssuedCommodity line : lines) {
                    inventoryService.debitConsumable(adminStore, line.getCommodity(), line.getQuantity(),
                            LedgerContext.of(StockLedgerReason.ISSUANCE, "ISSUANCE", issuance.getId()));
                }
                logger.info("Issuance " + issuance.getId() + " approved: " + lines.size()
                        + " line(s) debited from " + adminStore.getName());
            }
        }
        issuance.setStockSettled(true);
        issuanceRepository.save(issuance);
    }


    /**
     * Creates issued commodity records for the issuance
     *
     * @param issuance    The issuance record
     * @param commodities The commodities that were issued
     * @throws NotFoundException If a commodity is not found
     */
    private void addIssuedCommodities(Issuance issuance, List<IssuanceCommodityDTO> commodities)
            throws NotFoundException {
        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            IssuedCommodity stockCommodity = IssuedCommodity
                    .builder()
                    .commodity(commodity)
                    .quantity(dto.getQuantity())
                    .issuance(issuance)
                    .build();
            issuedCommodityRepository.save(stockCommodity);
        }
        logger.info("Added " + commodities.size() + " issued commodities to issuance record");
    }

    /**
     * Converts IssuanceCommodityDTO objects to CommodityWithDetailsDao objects
     *
     * @param issuanceCommodityDTOS The DTOs to convert
     * @return A list of CommodityWithDetailsDao objects
     * @throws NotFoundException If a commodity is not found
     */
    private List<CommodityWithDetailsDao> commodityWithDetailsDao(List<IssuanceCommodityDTO> issuanceCommodityDTOS)
            throws NotFoundException {
        List<CommodityWithDetailsDao> commoditiesWithDetails = new ArrayList<>();

        for (IssuanceCommodityDTO dto : issuanceCommodityDTOS) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            CommodityWithDetailsDao details = CommodityWithDetailsDao
                    .builder()
                    .commodity(commodity)
                    .deliveredQuantity(dto.getQuantity())
                    .build();
            commoditiesWithDetails.add(details);
        }

        return commoditiesWithDetails;
    }

    /**
     * Gets the last issued commodity for the specified commodity ID
     *
     * @param commodityId The commodity ID
     * @return The last issued commodity, or null if none exists
     */
    public IssuedCommodity getLastIssuedCommodity(Long commodityId) {
        List<IssuedCommodity> result = issuedCommodityRepository.findLastIssuedCommodityByCommodityId(
                commodityId,
                PageRequest.of(0, 1)
        );
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    /**
     * Blocks issuance of any asset still flagged "requires update after stocking" — i.e. the
     * responsible team has not yet filled in the engraved number and remaining details. Surfaces
     * every offending asset in one error so the issuer doesn't have to retry one-by-one. Called
     * before the Issuance record is created so an incomplete asset fails the whole request fast.
     *
     * @param assets The assets about to be issued
     * @throws NotFoundException If an asset is not found
     */
    private void validateAssetsReadyForIssuance(List<IssueAssetsDTO> assets) throws NotFoundException {
        // The authoritative guard against double-issuance: an asset may be issued only while it is
        // "Available for Issuance". Anything else — already issued, in transit, assigned, under
        // maintenance, still needing completion — is rejected here, so a stale frontend list or a
        // concurrent issuance can never hand out the same asset twice.
        List<String> notReady = new ArrayList<>();
        // The status check alone cannot see a repeat *within one payload*: the same id twice both
        // reads "Available for Issuance" here, because nothing is stamped until applyAssetIssuance
        // runs later — and two IssuedAsset rows would then be written for one asset. The frontend
        // rejects duplicates across lines, but that is not a guarantee the API can rely on.
        Set<Long> seenAssetIds = new HashSet<>();
        List<String> duplicated = new ArrayList<>();

        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());
            String code = asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null;
            String label = asset.getAssetName() + " [id=" + asset.getId() + "]";

            if (!seenAssetIds.add(asset.getId())) {
                duplicated.add(label);
                continue;
            }

            if (!CODE_ISSUANCE_AVAILABLE.equals(code)) {
                notReady.add(label + " — " + reasonNotIssuable(code));
            } else if (asset.isTemporaryPool()) {
                // Checked beside the status, not instead of it. Loaners now read Available for Loan,
                // but they used to read Available for Issuance, and this check was the only thing
                // that looked - so a loaner could be issued against a request and leave the pool for
                // good. A pool asset still carrying the old status must not slip through either.
                notReady.add(label + " — a temporary-replacement loaner, not stock for issuance");
            } else if (asset.getEngravedNumber() == null || asset.getEngravedNumber().isBlank()) {
                notReady.add(label + " — missing engraved number");
            }
        }

        if (!duplicated.isEmpty()) {
            throw new BadRequestException(
                    "These assets were listed more than once on the same issuance: "
                            + String.join("; ", duplicated));
        }
        if (!notReady.isEmpty()) {
            throw new BadRequestException(
                    "These assets cannot be issued: " + String.join("; ", notReady));
        }
    }

    /** Human-readable reason an asset in the given status code cannot be issued. */
    private String reasonNotIssuable(String code) {
        if (CODE_REQUIRE_UPDATE.equals(code)) return "not yet completed after stocking";
        if (CODE_ASSET_ISSUED.equals(code)) return "already issued and awaiting delivery";
        if (CODE_IN_TRANSIT.equals(code)) return "currently in transit";
        if (CODE_ASSET_ASSIGNED.equals(code)) return "already assigned to a user";
        if (pride.bank.erp.asset.AssetService.CODE_LOAN_AVAILABLE.equals(code)) {
            return "a temporary-replacement loaner, not stock for issuance";
        }
        return "not available for issuance" + (code != null ? " (status: " + code + ")" : "");
    }

    /**
     * Links every issued asset to this {@link Issuance} (audit trail, and what
     * {@link pride.bank.erp.movement.FulfillmentService} reads to build a cross-location fulfilment
     * movement) and stamps it <b>Issued</b> — for same- and cross-location issuance alike.
     *
     * <p>Stamping "Issued" immediately removes the asset from the "Available for Issuance" pool the
     * moment it is issued, so it cannot be issued a second time while it is awaiting approval,
     * dispatch, transit or delivery. Assignment to the requester (status "Assigned") is deferred to
     * acknowledgement of receipt — see {@link pride.bank.erp.issuedAsset.IssuedAssetLifecycleService}
     * — so "Assigned" only ever means the requester has actually confirmed receipt.
     *
     * @param assets       The assets being issued
     * @param user         The requester the assets are ultimately for
     * @param issuance     The just-saved issuance record to link against
     * @param sameLocation Whether the issuer and requester share a branch (kept for logging)
     * @throws NotFoundException If an asset or status is not found
     */
    private void applyAssetIssuance(List<IssueAssetsDTO> assets, User user, Issuance issuance, boolean sameLocation)
            throws NotFoundException {
        Status issuedStatus = statusService.findByStatusCode(CODE_ASSET_ISSUED);

        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());

            issuedAssetRepository.save(IssuedAsset.builder().issuance(issuance).asset(asset).build());

            // Out of the issuable pool now; assignment happens on acknowledgement of receipt.
            asset.setAssetStatus(issuedStatus);
            assetRepository.save(asset);
        }
        logger.info("Marked " + assets.size() + " asset(s) as issued (sameLocation=" + sameLocation + ")");
    }

    /**
     * Updates the status of a request
     *
     * @param request The request information
     * @throws NotFoundException If the request is not found
     */
    private void updateRequestStatus(IssuanceDTO request)
            throws NotFoundException {
        Request requestDetails = requestService.findRequest(request.getRequestId());
        Status status = statusService.findByStatusCode(CODE_ISSUED); // Request issued, pending issuance approval
        requestDetails.setStatus(status);
        requestRepository.save(requestDetails);
        logger.info("Updated status for request ID: " + request.getRequestId());
    }

    /**
     * Tracks the assignment history of an asset
     *
     * @param asset        The asset being assigned
     * @param newUser      The new user
     * @param statusBefore The status before assignment
     * @param statusAfter  The status after assignment
     */
    private void trackAssetAssignmentHistory(Asset asset, User newUser, Status statusBefore, Status statusAfter) {
        AssetAssignmentHistory activeHistory = assetAssignmentHistoryRepository
                .findActiveByAssetId(asset.getId());

        LocalDateTime now = LocalDateTime.now();

        if (activeHistory != null) {
            activeHistory.setEndDate(now);
            activeHistory.setStatusAfter(statusBefore);
            assetAssignmentHistoryRepository.save(activeHistory);
        }

        AssetAssignmentHistory newHistory = AssetAssignmentHistory.builder()
                .asset(asset)
                .user(newUser)
                .startDate(now)
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .build();

        assetAssignmentHistoryRepository.save(newHistory);
    }

    /**
     * Finds an issuance by ID
     *
     * @param id The issuance ID
     * @return The issuance
     * @throws NotFoundException If the issuance is not found
     */
    public Issuance findOne(Long id)
            throws NotFoundException {
        return issuanceRepository.findById(id).orElseThrow(
                () -> new NotFoundException(format("Issuance with Id %s is not found", id))
        );
    }

    /**
     * Finds an issuance by request ID
     *
     * @param requestId The request ID
     * @return The issuance DAO, or null if not found
     * @throws NotFoundException If there's an error finding the issuance
     */
    public IssuanceDAO findByRequestId(Long requestId) throws NotFoundException {
        Issuance data = issuanceRepository.findByRequestId(requestId).orElse(null);
        if (data == null) {
            return null;
        }

        return buildIssuanceResponse(data);
    }

    /**
     * Builds an IssuanceDAO response from an Issuance entity
     *
     * @param issuance The issuance entity
     * @return The issuance DAO
     */
    private IssuanceDAO buildIssuanceResponse(Issuance issuance) {
        return IssuanceDAO.builder()
                .id(issuance.getId())
                .comment(issuance.getComment())
                .requester(issuance.getRequester() != null ?
                        userBuilder.builder(issuance.getRequester()) : null)
                .status(issuance.getStatus() != null ?
                        Status.builder()
                                .id(issuance.getStatus().getId())
                                .name(issuance.getStatus().getName())
                                .description(issuance.getStatus().getDescription())
                                .code(issuance.getStatus().getCode())
                                .status(issuance.getStatus().getStatus())
                                .build() : null)
                .issuer(issuance.getIssuer() != null ?
                        userBuilder.builder(issuance.getIssuer()) : null)
                .request(issuance.getRequest() != null ?
                        Request.builder().id(issuance.getRequest().getId()).build() : null)
                .createDate(issuance.getCreateDate())
                .lastModified(issuance.getLastModified())
                .createdBy(issuance.getCreatedBy())
                .lastModifiedBy(issuance.getLastModifiedBy())
                .build();
    }

    /**
     * Notifies an individual about an issuance
     *
     * @param comment The comment to include
     * @param request The request details
     * @throws NotFoundException If the admin manager is not found
     */
    private void notifyIndividual(
            String comment,
            Request request
    ) throws NotFoundException {

        String msg = "Please review this issuance request for your approval.";

        emailService.sendEmailVerification(
                getAdminManager().getEmail(), // This must be Admin Manager Email or Branch Manager Email
                format("Stores Request ID: %s", request.getId()),
                null,
                getAdminManager().getFirstName(),
                "View Request",
                msg,
                "requests",
                request,
                null,
                null,
                null,
                null
        );
        logger.info("Notification sent to admin manager for request ID: " + request.getId());
    }

    /**
     * Gets the admin manager
     *
     * @return The admin manager user
     * @throws NotFoundException If the admin manager is not found
     */
    private User getAdminManager() throws NotFoundException {
        User currentUser = currentUserService.getCurrentUser();

        Long supervisorTitleId = currentUser.getTitle().getReportsTo().getId();

        /*
         * Always the issuer's own branch. Everyone but a BOM used to be looked up across the whole
         * bank, so with the supervisor title held at several branches this mail could go to a
         * manager somewhere else entirely.
         */
        List<User> supervisors = currentUser.getBranch() == null ? List.of()
                : userRepository.findActiveHoldersInBranch(supervisorTitleId, currentUser.getBranch().getId());

        if (supervisors.isEmpty()) {
            throw new NotFoundException("No supervisor found for title ID: " + supervisorTitleId);
        }
        return supervisors.get(0);
    }
}