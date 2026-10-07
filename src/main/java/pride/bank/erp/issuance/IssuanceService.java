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
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssuanceService {
    private static final Logger logger = Logger.getLogger(IssuanceService.class.getName());
    private static final Long HEAD_OFFICE_ID = 1L;
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

    @Transactional
    public IssuanceDAO issueCommodities(IssuanceDTO request) throws NotFoundException {
        User requester = userService.findUser(request.getRequester());
        Status status = statusService.findByStatusCode(CODE_ISSUED);
        Request requestDetails = requestService.findRequest(request.getRequestId());
        User currentUser = currentUserService.getCurrentUser();
        Long issuerBranchId = currentUser.getBranch().getId();
        Long requesterBranchId = requester.getBranch().getId();

        logger.info("Processing issuance request from branch " + issuerBranchId +
                " for user in branch " + requesterBranchId);

        boolean sameLocationAssets = Objects.equals(issuerBranchId, requesterBranchId);

        // Handle asset assignments if present — validate up front (fail fast, before the
        // Issuance record exists); the actual assignment/link is applied after the Issuance
        // is saved (see below), same ordering as commodities.
        if (request.getAssets() != null && !request.getAssets().isEmpty()) {
            validateAssetsReadyForIssuance(request.getAssets());
        }

        // Process commodities if present
        if (request.getCommodities() != null && !request.getCommodities().isEmpty()) {

            // Check if the request contains mixed item types and validate according to business rules
            validateRequestItemTypes(request.getCommodities(), requesterBranchId);

            // CASE 1: Branch issuing to its own users (stationery only) — same location, direct issuance.
            if (!Objects.equals(issuerBranchId, HEAD_OFFICE_ID)) {
                logger.info("Branch issuing items to user - branch ID: " + issuerBranchId);

                // Ensure that all items being issued are stationary
                ensureThatAllItemsAreStationery(request.getCommodities());

                // Validate that items are in branch store
                validateCommoditiesInStore(request.getCommodities(), issuerBranchId);

                // Direct issuance: debit the branch's ADMIN store through the inventory ledger.
                debitFromAdminStore(request.getCommodities(), issuerBranchId);
            }
            // CASE 2: Head Office issuing items
            else {
                boolean crossLocation = !Objects.equals(requesterBranchId, HEAD_OFFICE_ID);
                logger.info("Head office issuing items - crossLocation: " + crossLocation
                        + ", requester is BOM: " + isBranchOperationManager(requester));

                // Validate availability at head office regardless of destination.
                validateCommoditiesInStore(request.getCommodities(), HEAD_OFFICE_ID);

                if (crossLocation) {
                    // Inter-location fulfilment: the Movement created at issuance approval owns the
                    // HO→destination transfer (debits HO, credits the destination ADMIN store on
                    // receipt). No eager move here — that would double-count against the movement.
                    logger.info("Cross-location issuance; stock transfer deferred to fulfilment movement");
                } else {
                    // Same location: direct issuance from the HO ADMIN store.
                    debitFromAdminStore(request.getCommodities(), HEAD_OFFICE_ID);
                }
            }
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
    private void validateRequestItemTypes(List<IssuanceCommodityDTO> commodities, Long requesterBranchId)
            throws NotFoundException {

        // If requester is from head office, no restrictions on mixed items
        if (Objects.equals(requesterBranchId, HEAD_OFFICE_ID)) {
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
     * Ensures that all commodities in the list are stationary items
     *
     * @param commodities The list of commodities to check
     * @throws NotFoundException        If a commodity is not found
     * @throws IllegalArgumentException If any commodity is not stationery
     */
    private void ensureThatAllItemsAreStationery(List<IssuanceCommodityDTO> commodities)
            throws NotFoundException {

        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            if (!isStationery(commodity)) {
                throw new IllegalArgumentException(String.format(
                        "Commodity '%s' is not Stationery. Only Stationery items can be issued from branch stores.",
                        commodity.getName()));
            }
        }
        logger.info("All items verified as stationery");
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

        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());

            // Scope to the branch's ADMIN store. A plain branch-scoped lookup is unsafe: a courier
            // custody store is anchored at Head Office, so once a consumable has passed through a
            // courier the branch holds two balance rows for it and a single-result query blows up.
            StoreBalance balance = storeBalanceRepository
                    .findByBranchIdAndStore_StoreTypeAndCommodity_Id(branchId, StoreType.ADMIN, commodity.getId())
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Commodity '%s' not found in store for branch ID %d",
                                    commodity.getName(), branchId)));

            if (balance.getQuantity() < dto.getQuantity()) {
                throw new NotFoundException(String.format(
                        "Insufficient stock for commodity '%s'. Available: %d, Requested: %d",
                        commodity.getName(), balance.getQuantity(), dto.getQuantity()));
            }
        }
        logger.info("All commodities verified as available in branch " + branchId);
    }

    /**
     * Direct issuance: debits the commodities from a branch's ADMIN store through the inventory
     * ledger ({@link InventoryService}), the single authority over balances. Used only for
     * same-location issuance — cross-location transfers are owned by the fulfilment movement.
     *
     * @param commodities The commodities to debit
     * @param branchId    The branch whose ADMIN store is debited
     * @throws NotFoundException If the branch or a commodity is not found
     */
    private void debitFromAdminStore(List<IssuanceCommodityDTO> commodities, Long branchId)
            throws NotFoundException {
        Store adminStore = storeService.resolveAdminStore(branchId);
        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            inventoryService.debitConsumable(adminStore, commodity, dto.getQuantity(),
                    LedgerContext.of(StockLedgerReason.ISSUANCE, "ISSUANCE", null));
        }
        logger.info("Stock quantities reduced in branch " + branchId + " ADMIN store");
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

        Role role = currentUser.getTitle().getRole();
        Long supervisorTitleId = currentUser.getTitle().getReportsTo().getId();

        List<User> supervisors;

        if (Objects.equals(role.getName(), "Branch Operations Manager")) {
            supervisors = userRepository.findByTitleIdAndBranchId(supervisorTitleId, currentUser.getBranch().getId());
        } else {
            supervisors = userRepository.findByTitleId(supervisorTitleId);
        }

        if (supervisors.isEmpty()) {
            throw new NotFoundException("No supervisor found for title ID: " + supervisorTitleId);
        }
        return supervisors.get(0);
    }
}