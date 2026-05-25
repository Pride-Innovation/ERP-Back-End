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
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.issuedCommodity.IssuedCommodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.role.Role;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stock.CommodityWithDetailsDao;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssuanceService {
    private static final Logger logger = Logger.getLogger(IssuanceService.class.getName());
    private static final Long HEAD_OFFICE_ID = 1L;
    /** Status ID of newly-stocked assets that still need their details filled in. */
    private static final Long STATUS_REQUIRE_UPDATE = 9L;

    private final UserService userService;
    private final IssuanceRepository issuanceRepository;
    private final StatusService statusService;
    private final CommodityService commodityService;
    private final IssuedCommodityRepository issuedCommodityRepository;
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
    private final StoreRepository storeRepository;

    @Transactional
    public IssuanceDAO issueCommodities(IssuanceDTO request) throws NotFoundException {
        User requester = userService.findUser(request.getRequester());
        Status status = statusService.findOneStatus(5L);
        Request requestDetails = requestService.findRequest(request.getRequestId());
        User currentUser = currentUserService.getCurrentUser();
        Long issuerBranchId = currentUser.getBranch().getId();
        Long requesterBranchId = requester.getBranch().getId();

        logger.info("Processing issuance request from branch " + issuerBranchId +
                " for user in branch " + requesterBranchId);

        // Handle asset assignments if present
        if (request.getAssets() != null && !request.getAssets().isEmpty()) {
            updateAssetAssignedToStatusAndStatus(request.getAssets(), requester);
        }

        // Process commodities if present
        if (request.getCommodities() != null && !request.getCommodities().isEmpty()) {

            // Check if the request contains mixed item types and validate according to business rules
            validateRequestItemTypes(request.getCommodities(), requesterBranchId);

            // CASE 1: Branch issuing to regular users (only stationery allowed)
            if (!Objects.equals(issuerBranchId, HEAD_OFFICE_ID)) {
                logger.info("Branch issuing items to user - branch ID: " + issuerBranchId);

                // Ensure that all items being issued are stationary
                ensureThatAllItemsAreStationery(request.getCommodities());

                // Validate that items are in branch store
                validateCommoditiesInStore(request.getCommodities(), issuerBranchId);

                // Reduce items from branch store
                reduceCommoditiesFromStore(request.getCommodities(), issuerBranchId);
            }
            // CASE 2: Head Office issuing items
            else {
                logger.info("Head office issuing items - requester is BOM: " +
                        isBranchOperationManager(requester));

                // If head office is issuing to a BOM, add to branch stock
                if (isBranchOperationManager(requester) && !Objects.equals(requesterBranchId, HEAD_OFFICE_ID)) {
                    logger.info("Processing BOM request for branch: " + requesterBranchId);

                    // First validate items are available at head office
                    validateCommoditiesInStore(request.getCommodities(), HEAD_OFFICE_ID);

                    // Convert DTOs to the format needed by store service
                    List<CommodityWithDetailsDao> commoditiesWithDetails =
                            commodityWithDetailsDao(request.getCommodities());

                    // Add to requester's branch (for BOM)
                    storeService.addCommoditiesToStore(commoditiesWithDetails, requesterBranchId);

                    // Reduce from head office
                    storeService.reduceCommoditiesFromStore(commoditiesWithDetails, HEAD_OFFICE_ID);
                }
                // Head office issuing directly to users (any type of item)
                else {
                    logger.info("Head office issuing directly to user");

                    // Validate items are available at head office
                    validateCommoditiesInStore(request.getCommodities(), HEAD_OFFICE_ID);

                    // Reduce from head office
                    List<CommodityWithDetailsDao> commoditiesWithDetails =
                            commodityWithDetailsDao(request.getCommodities());
                    storeService.reduceCommoditiesFromStore(commoditiesWithDetails, HEAD_OFFICE_ID);
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

        // Update request status
        updateRequestStatus(request);

        // Send notification
        notifyIndividual(
                request.getComment() != null ? request.getComment() : "Please approve this issued request",
                requestDetails
        );

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
    @Transactional(readOnly = true)
    private void validateCommoditiesInStore(List<IssuanceCommodityDTO> commodities, Long branchId)
            throws NotFoundException {

        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());

            Store store = storeRepository.findByBranchIdAndCommodityId(branchId, commodity.getId())
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Commodity '%s' not found in store for branch ID %d",
                                    commodity.getName(), branchId)));

            if (store.getQuantity() < dto.getQuantity()) {
                throw new NotFoundException(String.format(
                        "Insufficient stock for commodity '%s'. Available: %d, Requested: %d",
                        commodity.getName(), store.getQuantity(), dto.getQuantity()));
            }
        }
        logger.info("All commodities verified as available in branch " + branchId);
    }

    /**
     * Reduces the quantity of commodities in the specified branch store
     *
     * @param commodities The commodities to reduce
     * @param branchId    The branch ID
     * @throws NotFoundException If a commodity is not found
     */
    @Transactional
    private void reduceCommoditiesFromStore(List<IssuanceCommodityDTO> commodities, Long branchId)
            throws NotFoundException {

        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());

            Store store = storeRepository.findByBranchIdAndCommodityId(branchId, commodity.getId())
                    .orElseThrow(() -> new IllegalStateException(String.format(
                            "Unexpected error: Store record for commodity '%s' not found during stock reduction.",
                            commodity.getName())));

            int updatedQuantity = store.getQuantity() - dto.getQuantity();
            store.setQuantity(updatedQuantity);
            storeRepository.save(store);
        }
        logger.info("Stock quantities reduced in branch " + branchId);
    }

    /**
     * Creates issued commodity records for the issuance
     *
     * @param issuance    The issuance record
     * @param commodities The commodities that were issued
     * @throws NotFoundException If a commodity is not found
     */
    @Transactional
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
     * Updates the assignment and status of assets for a user
     *
     * @param assets The assets to update
     * @param user   The user to assign the assets to
     * @throws NotFoundException If an asset or status is not found
     */
    @Transactional
    private void updateAssetAssignedToStatusAndStatus(List<IssueAssetsDTO> assets, User user) throws NotFoundException {
        Status assignedStatus = statusService.findOneStatus(4L);
        if (assignedStatus == null) {
            throw new NotFoundException("Status with ID 4 not found.");
        }

        // Block issuance of any asset still flagged "requires update after stocking" —
        // i.e. the responsible team has not yet filled in the engraved number and
        // remaining details. Surface every offending asset in one error so the
        // issuer doesn't have to retry one-by-one.
        List<String> incomplete = new ArrayList<>();
        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());
            Status currentStatus = asset.getAssetStatus();
            boolean stillNeedsCompletion = currentStatus != null
                    && currentStatus.getId() != null
                    && currentStatus.getId().equals(STATUS_REQUIRE_UPDATE);
            boolean missingEngravedNumber = asset.getEngravedNumber() == null
                    || asset.getEngravedNumber().isBlank();
            if (stillNeedsCompletion || (missingEngravedNumber && stillNeedsCompletion)) {
                incomplete.add(asset.getAssetName()
                        + (missingEngravedNumber ? " (no engraved number)" : "")
                        + " [id=" + asset.getId() + "]");
            }
        }
        if (!incomplete.isEmpty()) {
            throw new BadRequestException(
                    "Cannot issue assets that have not been completed after stocking. "
                            + "Please ask the responsible team to add the engraved number and "
                            + "mark the following ready for issuance: "
                            + String.join("; ", incomplete));
        }

        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());

            User oldUser = asset.getAssignedTo();
            Status oldStatus = asset.getAssetStatus();

            if (oldUser != null && oldUser.getId().equals(user.getId()) && oldStatus != null && oldStatus.getId() == 4) {
                continue;
            }

            if (oldUser == null || !oldUser.getId().equals(user.getId())) {
                trackAssetAssignmentHistory(asset, user, oldStatus, assignedStatus);
            }

            asset.setAssignedTo(user);
            asset.setAssetStatus(assignedStatus);

            assetRepository.save(asset);
        }
        logger.info("Updated asset assignments for " + assets.size() + " assets");
    }

    /**
     * Updates the status of a request
     *
     * @param request The request information
     * @throws NotFoundException If the request is not found
     */
    @Transactional
    private void updateRequestStatus(IssuanceDTO request)
            throws NotFoundException {
        Request requestDetails = requestService.findRequest(request.getRequestId());
        Status status = statusService.findOneStatus(5L); // Get The Request Issued for Approval Status
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
    @Transactional
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

        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(getAdminManager().getEmail());
        String msg = "Please review this issuance request for your approval.";

        emailService.sendEmailVerification(
                cleanedEmail.getCleanedEmail(), // This must be Admin Manager Email or Branch Manager Email
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