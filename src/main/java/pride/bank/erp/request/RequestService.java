/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeCustomAttribute;
import pride.bank.erp.assetType.AssetTypeRefDTO;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.assetType.AttributeDataType;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.exception.ValidationException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.requestReport.RequestReport;
import pride.bank.erp.requestReport.RequestReportDAO;
import pride.bank.erp.requestReport.RequestReportRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import pride.bank.erp.workflowinstance.WorkflowEngineService;
import pride.bank.erp.workflowinstance.RequestWorkflowInstance;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

/**
 * Service class for handling Asset Request operations.
 * Manages creation, updates, searches and other operations for asset requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {
    private static final String STATIONERY_TYPE = "Stationery";

    private final RequestRepository requestRepository;
    private final FileUpload fileUpload;
    private final RequestSearchDao requestSearchDao;
    private final StatusService statusService;
    private final CommodityService commodityService;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final Email emailService;
    private final CurrentUserService currentUserService;
    private final TitleRepository titleRepository;
    private final UserBuilder userBuilder;
    private final WorkflowEngineService workflowEngineService;
    private final AssetTypeService assetTypeService;
    private final RequestReportRepository requestReportRepository;

    /**
     * Creates a new request with commodities while enforcing business rules:
     * - Head office users can request mixed item types
     * - Branch users cannot mix stationery and non-stationery items
     *
     * @param body                      The request details
     * @param file                      Optional signature file
     * @param requestCommoditiesRequest List of commodities to request
     * @return The created request
     * @throws IOException           If file handling fails
     * @throws NotFoundException     If related entities cannot be found
     * @throws ValidationException   If request data is invalid
     * @throws BusinessRuleException If business rules are violated
     */
    @Transactional
    public RequestDTO createRequest(
            Request body,
            MultipartFile file,
            List<RequestCommodityDTO> requestCommoditiesRequest,
            Long assetTypeId,
            Map<String, Object> attributes
    ) throws IOException, NotFoundException, ValidationException, BusinessRuleException {
        log.info("Creating new request: {}", body != null ? body.getName() : "null");

        // Basic validation
        validateRequestData(body, requestCommoditiesRequest);

        // Category + attribute validation (back-compat: skipped when assetTypeId is null)
        AssetType resolvedAssetType = resolveAssetTypeAndValidateAttributes(assetTypeId, attributes);
        Map<String, Object> sanitisedAttributes = sanitiseAttributes(attributes);

        // Get current user and determine branch
        User requester = currentUserService.getCurrentUser();
        if (requester == null) {
            throw new IllegalStateException("Current user could not be identified.");
        }

        // A request cannot be routed without a branch: the workflow resolver keys
        // off the requester's branch (Head Office vs branch) and branch-scoped
        // approvers (supervisor, BOM, branch manager) can't be resolved otherwise.
        if (requester.getBranch() == null) {
            throw new BusinessRuleException(
                    "Your branch has not been configured. Please contact the system administrator "
                            + "to set up your branch before making a request.");
        }

        Long requesterBranchId = requester.getBranch().getId();
        boolean isHeadOfficeUser = requester.getBranch().isHeadOffice();

        log.info("Request from user at branch ID: {}, isHeadOfficeUser: {}", requesterBranchId, isHeadOfficeUser);

        // Track Branch Stationery and Non stationery request
        boolean isBranchStationeryRequest = false;
        boolean isBranchNonStationeryRequest = false;

        // Enforce business rules for mixed item types
        if (!isHeadOfficeUser) {
            validateNoMixedItemTypesForBranchUser(requestCommoditiesRequest);
            isBranchStationeryRequest = containsStationeryItems(requestCommoditiesRequest);

            // Check if the Branch Request is Not Stationery request
            if (!containsStationeryItems(requestCommoditiesRequest)) {
                isBranchNonStationeryRequest = true;
            }
        }

        // Handle file upload
        String filePath = handleFileUpload(file);

        // Get initial request status
        Status status = getInitialRequestStatus();

        // Create and save the request
        Request request = buildInitialRequest(
                body,
                filePath,
                status,
                requester,
                isBranchStationeryRequest,
                isBranchNonStationeryRequest
        );
        request.setAssetType(resolvedAssetType);
        request.setAttributes(sanitisedAttributes);
        Request savedRequest = saveRequest(request);

        // Add commodities to request
        addCommoditiesToRequest(savedRequest, requestCommoditiesRequest);

        // Load commodities for response
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        savedRequest.setCommodities(commodities);

        String emailStatus = "Notification pending";

        // Start workflow engine (handles approver assignment + email notifications)
        RequestWorkflowInstance workflowInstance = workflowEngineService.startWorkflow(savedRequest);

        if (workflowInstance == null) {
            // Fallback: legacy supervisor routing if no workflow configured
            User supervisor = findSupervisorToApproveRequest();
            if (supervisor != null) {
                savedRequest.setCurrentApprover(supervisor);
                savedRequest = saveRequest(savedRequest);
                if (!supervisor.isBlocked() || supervisor.isAccountNonLocked()) {
                    emailStatus = notifySupervisor(supervisor.getEmail(), savedRequest, supervisor.getFirstName());
                    if (supervisor.getDepartment() != null) {
                        notifySupervisor(supervisor.getDepartment().getManagersGroupEmail(), savedRequest, "Team");
                    }
                } else if (supervisor.getDepartment() != null) {
                    emailStatus = notifySupervisor(
                            supervisor.getDepartment().getManagersGroupEmail(), savedRequest, "Managers");
                }
            }
        } else {
            emailStatus = "Workflow started – notifications sent";
        }
        savedRequest.setEmailMessage(emailStatus);

        log.info("Request created successfully with ID: {}", savedRequest.getId());
        RequestCommodityReports data = generateRequestReportsAndCommodities(savedRequest);
        return buildDetailedRequestDTO(savedRequest, data.getCommodities(), data.getReports());
    }


    /**
     * Checks if any commodity in the request is a stationery item
     *
     * @param commodities List of commodities in the request
     * @return true if at least one item is stationary, false otherwise
     */
    public boolean containsStationeryItems(List<RequestCommodityDTO> commodities) {
        if (commodities == null || commodities.isEmpty()) {
            return false;
        }

        for (RequestCommodityDTO dto : commodities) {
            try {
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                if (commodity != null && commodity.getAssetType() != null &&
                        STATIONERY_TYPE.equalsIgnoreCase(commodity.getAssetType().getName())) {
                    System.out.println("contains Stationery");
                    return true;
                }
            } catch (Exception e) {
                // Log error but continue checking other commodities
            }
        }

        return false;
    }


    /**
     * Updates an existing request with new data and commodities.
     *
     * @param requestId                 The ID of the request to update
     * @param body                      Updated request details
     * @param file                      Optional new signature file
     * @param updatedRequestCommodities Updated list of commodities
     * @return The updated request
     * @throws IOException         If file handling fails
     * @throws NotFoundException   If request or related entities cannot be found
     * @throws ValidationException If updated data is invalid
     */
    @Transactional
    public RequestDTO updateRequest(
            Long requestId,
            Request body,
            MultipartFile file,
            List<RequestCommodityDTO> updatedRequestCommodities,
            Long assetTypeId,
            Map<String, Object> attributes
    ) throws IOException, NotFoundException, ValidationException {
        log.info("Updating request with ID: {}", requestId);

        // Validate input data
        validateRequestData(body, updatedRequestCommodities);

        // Category + attribute validation (back-compat: skipped when assetTypeId is null)
        AssetType resolvedAssetType = resolveAssetTypeAndValidateAttributes(assetTypeId, attributes);
        Map<String, Object> sanitisedAttributes = sanitiseAttributes(attributes);

        // Find the existing request
        Request existingRequest = findRequest(requestId);
        Status status = statusService.findByStatusCode("requestCreated");

        // Track whether the request is being resubmitted after a rejection
        boolean isResubmission = existingRequest.getStatus() != null
                && "requestRejected".equalsIgnoreCase(existingRequest.getStatus().getStatus());

        // Get current user and determine branch
        User requester = currentUserService.getCurrentUser();
//        Long requesterBranchId = requester.getBranch() != null ? requester.getBranch().getId() : null;
        boolean isHeadOfficeUser = requester.getBranch() != null && requester.getBranch().isHeadOffice();

        // Enforce business rules for mixed item types
        if (!isHeadOfficeUser) {
            validateNoMixedItemTypesForBranchUser(updatedRequestCommodities);
        }

        // Handle file update
        String newFilePath = handleFileUpdate(file, body.getSignaturePath(), existingRequest.getSignaturePath());
        if (newFilePath != null) {
            existingRequest.setSignaturePath(newFilePath);
        }

        // Update other fields
        existingRequest.setName(body.getName());
        existingRequest.setPriority(body.getPriority());
        existingRequest.setTimeOfSubmissionOfRequest(body.getTimeOfSubmissionOfRequest());
        existingRequest.setStatus(status);
        existingRequest.setDescription(body.getDescription());
        existingRequest.setAssetType(resolvedAssetType);
        existingRequest.setAttributes(sanitisedAttributes);

        Request savedRequest = requestRepository.save(existingRequest);
        log.info("Updated request basic details for ID: {}", requestId);

        // Replace commodities
        requestCommodityRepository.deleteByRequestId(requestId);
        log.info("Removed existing commodities for request ID: {}", requestId);

        addCommoditiesToRequest(savedRequest, updatedRequestCommodities);
        log.info("Added {} updated commodities to request ID: {}",
                updatedRequestCommodities.size(), requestId);

        // Reload commodities for response
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        savedRequest.setCommodities(commodities);

        // Restart the workflow engine for resubmitted requests so the approval chain begins fresh
        if (isResubmission) {
            log.info("Request {} is being resubmitted after rejection — restarting workflow", requestId);

            // Record the resubmission in the approval history so the audit trail keeps
            // both the earlier rejection and the requester's follow-up update. Without
            // this, restarting the workflow would leave no trace of the user's action.
            requestReportRepository.save(RequestReport.builder()
                    .approver(requester)
                    .request(savedRequest)
                    .status(status) // requestCreated
                    .comment("Request updated and resubmitted by the requester.")
                    .build());

            RequestWorkflowInstance resubmitInstance = workflowEngineService.resetAndRestartWorkflow(savedRequest);
            if (resubmitInstance == null) {
                // No workflow configured — fall back to legacy supervisor routing
                try {
                    User supervisor = findSupervisorToApproveRequest();
                    if (supervisor != null) {
                        savedRequest.setCurrentApprover(supervisor);
                        savedRequest = requestRepository.save(savedRequest);
                        log.info("Legacy fallback: set currentApprover to {} for resubmitted request {}",
                                supervisor.getEmail(), requestId);
                    }
                } catch (Exception ex) {
                    log.warn("Could not resolve supervisor for resubmitted request {}: {}", requestId, ex.getMessage());
                }
            }
        }

        log.info("Request updated successfully with ID: {}", savedRequest.getId());
        RequestCommodityReports data = generateRequestReportsAndCommodities(savedRequest);
        return buildDetailedRequestDTO(savedRequest, data.getCommodities(), data.getReports());
    }

    /**
     * Marks a request as deleted (soft delete)
     *
     * @param id The ID of the request to delete
     * @return Response indicating success or failure
     * @throws NotFoundException If the request is not found
     */
    @Transactional
    public DeleteResponse deleteRequest(Long id) throws NotFoundException {
        log.info("Attempting to delete request with ID: {}", id);

        if (requestRepository.existsById(id)) {
            Request request = findRequest(id);
            request.setDeleted(true);
            request.setDeletionDate(LocalDateTime.now());
            requestRepository.save(request);
            log.info("Successfully marked request ID {} as deleted", id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            log.warn("Failed to delete request - ID {} not found", id);
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    /**
     * Fetches requests with filtering and pagination.
     *
     * @param pageSize                  Size of each page
     * @param sortDirection             Sort direction (ASC/DESC)
     * @param pageNumber                Page number to retrieve
     * @param sortBy                    Field to sort by
     * @param name                      Filter by request name
     * @param priority                  Filter by priority
     * @param description               Filter by description
     * @param timeOfSubmissionOfRequest Filter by submission time
     * @param statusIds                 Filter by specific status IDs
     * @param status                    Filter by status name
     * @param requestedBy               Filter by requester
     * @param approver                  Filter by approver
     * @param deleted                   Filter by deletion status
     * @return Paginated result of requests
     */
    @Transactional(readOnly = true)
    public Page<RequestDTO> fetchAllRequests(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name,
            String priority,
            String description,
            LocalDateTime timeOfSubmissionOfRequest,
            List<Long> statusIds,
            String status,
            String requestedBy,
            String approver,
            Boolean deleted,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long currentApproverId
    ) {
        try {
            log.debug("Fetching requests with filters - page: {}, status: {}", pageNumber, status);

            RequestPage requestPage = RequestPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            RequestSearchCriteria requestSearchCriteria = RequestSearchCriteria
                    .builder()
                    .name(name)
                    .priority(priority)
                    .description(description)
                    .statusIds(statusIds)
                    .requesterIds(requestersList(status))
                    .requestedBy(requestedBy)
                    .isBranchStationery(canSeeBranchStationaryRequests())
                    .isBranchNonStationery(canSeeBranchNonStationaryRequests())
                    .approver(approver)
                    .deleted(deleted)
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .startDate(startDate)
                    .endDate(endDate)
                    .currentUserId(currentUserService.getCurrentUser().getId())
                    .isAdmin(determineIfUserIsAdmin())
                    .currentApproverId(currentApproverId)
                    .currentUnitId(currentUserService.getCurrentUser().getUnit() != null
                            ? currentUserService.getCurrentUser().getUnit().getId() : null)
                    .build();

            Page<Request> requestPageResult = requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
            List<RequestDTO> dtoList = convertToRequestDTOList(requestPageResult.getContent());

            log.debug("Retrieved {} requests out of {} total",
                    dtoList.size(), requestPageResult.getTotalElements());

            return new PageImpl<>(dtoList, requestPageResult.getPageable(), requestPageResult.getTotalElements());

        } catch (Exception e) {
            log.error("Error fetching requests: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Finds a request by ID and loads its commodities
     *
     * @param requestId The request ID
     * @return The request with loaded commodities
     * @throws NotFoundException If the request is not found
     */
    @Transactional(readOnly = true)
    public Request findRequest(Long requestId) throws NotFoundException {
        log.debug("Finding request with ID: {}", requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.warn("Request with ID {} not found", requestId);
                    return new NotFoundException("Request with ID " + requestId + " not found");
                });

        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(requestId);
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        request.setCommodities(commodities);

        return request;
    }

    /**
     * Generates and aggregates request-related data, including its commodities and reports.
     * <p>
     * This method performs the following:
     * <ul>
     *   <li>Retrieves all commodities linked to the given request.</li>
     *   <li>Converts these commodities into DTOs containing quantity details.</li>
     *   <li>Retrieves all reports associated with the request.</li>
     *   <li>Maps the reports into data access objects (DAOs) for transfer and response building.</li>
     * </ul>
     *
     * @param request The {@link Request} entity for which reports and commodities are to be generated.
     * @return A {@link RequestCommodityReports} object containing the list of commodities and reports.
     */
    private RequestCommodityReports generateRequestReportsAndCommodities(Request request) {
        // Fetch all commodities associated with the given request
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(request.getId());

        // Convert fetched commodities into DTOs that include quantity and related details
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);

        // Attach the commodity details to the request entity for context consistency
        request.setCommodities(commodities);

        // Fetch all reports linked to this request from the repository
        List<RequestReport> reports = requestCommodityRepository.findReportByRequestId(request.getId());

        // Transform each report entity into a DAO representation suitable for external use
        List<RequestReportDAO> requestReportDAOS = reports.stream()
                .map(this::buildObject)
                .toList();

        // Build and return the aggregated response object containing both commodities and reports
        return RequestCommodityReports.builder()
                .commodities(commodities)
                .reports(requestReportDAOS)
                .build();
    }


    /**
     * Fetches a single request with full details including commodities and reports
     *
     * @param requestId The request ID
     * @return DTO with complete request details
     * @throws NotFoundException If the request is not found
     */
    @Transactional(readOnly = true)
    public RequestDTO fetchSingleRequest(Long requestId) throws NotFoundException {
        log.debug("Fetching detailed request with ID: {}", requestId);

        Request request = findRequest(requestId);
        RequestCommodityReports data = generateRequestReportsAndCommodities(request);
        return buildDetailedRequestDTO(request, data.getCommodities(), data.getReports());
    }

    /**
     * Gets a summary of asset requests for the current year and branch
     *
     * @return List of summary DTOs
     */
    @Transactional(readOnly = true)
    public List<AssetRequestSummaryDTO> getCurrentYearRequestSummary() {
        Long receiptAcknowledged = 7L; // ID for Receipt Acknowledged Status
        Long requestRejected = 2L;     // ID for Request Rejected Status
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();

        log.debug("Fetching asset request summaries for branch ID: {}", branchId);

        try {
            List<AssetRequestSummaryDTO> summaries = requestRepository
                    .fetchAssetRequestSummaries(receiptAcknowledged, requestRejected, branchId)
                    .stream()
                    .map(r -> new AssetRequestSummaryDTO(
                            (String) r[0],
                            ((Number) r[1]).longValue(),
                            ((Number) r[2]).longValue()))
                    .toList();

            log.debug("Retrieved {} summary records", summaries.size());
            return summaries;

        } catch (Exception ex) {
            log.error("Error fetching asset request summaries: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Finds the latest pending requests with filtering
     *
     * @param pageSize                  Size of each page
     * @param sortDirection             Sort direction
     * @param pageNumber                Page number
     * @param sortBy                    Field to sort by
     * @param name                      Filter by name
     * @param priority                  Filter by priority
     * @param description               Filter by description
     * @param timeOfSubmissionOfRequest Filter by submission time
     * @param statusIds                 Filter by status IDs
     * @param status                    Filter by status name
     * @return Paginated request results
     */
    @Transactional(readOnly = true)
    public Page<RequestDTO> findLatestPendingRequestsWithDetails(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name,
            String priority,
            String description,
            LocalDateTime timeOfSubmissionOfRequest,
            List<Long> statusIds,
            String status
    ) {
        log.debug("Finding latest pending requests with status: {}", status);

        // Determine the status of the request to be shown to different roles
        List<Long> updatedStatusIds = determineStatusIds(statusIds);

        try {
            RequestPage requestPage = RequestPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            RequestSearchCriteria requestSearchCriteria = RequestSearchCriteria.builder()
                    .name(name)
                    .priority(priority)
                    .description(description)
                    .statusIds(updatedStatusIds)
                    .requesterIds(requestersList(status))
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .build();

            Page<Request> requestPageResult = requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
            List<RequestDTO> dtoList = convertToRequestDTOList(requestPageResult.getContent());

            log.debug("Retrieved {} pending requests", dtoList.size());
            return new PageImpl<>(dtoList, requestPageResult.getPageable(), requestPageResult.getTotalElements());

        } catch (Exception e) {
            log.error("Error finding pending requests: {}", e.getMessage());
            return Page.empty();
        }
    }

    // Helper methods

    /**
     * Validates that branch users are not mixing stationery and non-stationery items in the same request.
     *
     * @param requestCommodities The list of commodities in the request
     * @throws NotFoundException     If a commodity is not found
     * @throws BusinessRuleException If validation fails
     */
    private void validateNoMixedItemTypesForBranchUser(List<RequestCommodityDTO> requestCommodities)
            throws NotFoundException, BusinessRuleException {

        boolean hasStationery = false;
        boolean hasNonStationery = false;
        Set<String> commodityTypes = new HashSet<>();

        for (RequestCommodityDTO dto : requestCommodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());

            if (commodity == null || commodity.getAssetType() == null) {
                throw new NotFoundException("Commodity or asset type not found for ID: " + dto.getCommodityId());
            }

            String assetTypeName = commodity.getAssetType().getName();
            commodityTypes.add(assetTypeName);

            if (STATIONERY_TYPE.equalsIgnoreCase(assetTypeName)) {
                hasStationery = true;
            } else {
                hasNonStationery = true;
            }

            // If we found both types in a branch user's request, throw an exception
            if (hasStationery && hasNonStationery) {
                log.warn("Branch user attempted to mix stationery and non-stationery items in a single request. " +
                        "Types found: {}", commodityTypes);
                throw new BusinessRuleException(
                        "Branch users cannot request stationery and non-stationery items in the same request. " +
                                "Please create separate requests for different item types."
                );
            }
        }

        log.info("Request item types validation passed: hasStationery={}, hasNonStationery={}",
                hasStationery, hasNonStationery);
    }

    /**
     * Resolves the chosen asset category (if any) and validates the supplied attribute values
     * against its custom attribute definitions. Returns the AssetType entity to attach to the
     * Request, or null when no category was supplied.
     *
     * <p>Back-compat: when {@code assetTypeId} is null, attributes are accepted as-is and not
     * validated (older clients may submit attributes without a category).
     */
    private AssetType resolveAssetTypeAndValidateAttributes(Long assetTypeId, Map<String, Object> attributes)
            throws NotFoundException, ValidationException {
        if (assetTypeId == null) {
            return null;
        }

        AssetType assetType = assetTypeService.findOneAssetType(assetTypeId);
        Map<String, Object> values = attributes == null ? Collections.emptyMap() : attributes;
        Map<String, AssetTypeCustomAttribute> defs = assetType.getCustomAttributes() == null
                ? Collections.emptyMap()
                : assetType.getCustomAttributes().stream()
                .collect(Collectors.toMap(AssetTypeCustomAttribute::getKey, Function.identity()));

        for (String key : values.keySet()) {
            if (!defs.containsKey(key)) {
                throw new ValidationException(
                        "Unknown attribute key '" + key + "' for asset category '" + assetType.getName() + "'");
            }
        }

        for (AssetTypeCustomAttribute def : defs.values()) {
            Object value = values.get(def.getKey());
            boolean missing = value == null || (value instanceof String s && s.isBlank());
            if (def.isRequired() && missing) {
                throw new ValidationException(
                        "Required attribute '" + def.getKey() + "' is missing for asset category '" + assetType.getName() + "'");
            }
            if (!missing) {
                validateAttributeValue(def, value);
            }
        }

        return assetType;
    }

    private void validateAttributeValue(AssetTypeCustomAttribute def, Object value) throws ValidationException {
        AttributeDataType type = def.getDataType();
        switch (type) {
            case TEXT -> {
                if (!(value instanceof String)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' must be a string (TEXT)");
                }
            }
            case NUMBER -> {
                if (!(value instanceof Number)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' must be a number");
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' must be a boolean");
                }
            }
            case DATE -> {
                if (!(value instanceof String s) || !isIsoDate(s)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' must be an ISO date string (YYYY-MM-DD)");
                }
            }
            case SELECT -> {
                if (!(value instanceof String s)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' must be a string (one of the configured options)");
                }
                if (def.getOptions() == null || !def.getOptions().contains(s)) {
                    throw new ValidationException(
                            "Attribute '" + def.getKey() + "' value '" + s + "' is not one of the configured options "
                                    + def.getOptions());
                }
            }
        }
    }

    private boolean isIsoDate(String s) {
        try {
            LocalDate.parse(s);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Drops null-valued entries; returns null if the resulting map is empty. */
    private Map<String, Object> sanitiseAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Validates basic request data
     */
    private void validateRequestData(Request body, List<RequestCommodityDTO> requestCommodities)
            throws ValidationException {
        if (body == null) {
            throw new ValidationException("Request body cannot be null.");
        }

        if (body.getName() == null || body.getName().trim().isEmpty()) {
            throw new ValidationException("Request name is required.");
        }

        if (CollectionUtils.isEmpty(requestCommodities)) {
            throw new ValidationException("At least one commodity is required.");
        }

        // Validate commodity quantities
        for (RequestCommodityDTO commodity : requestCommodities) {
            if (commodity.getQuantity() <= 0) {
                throw new ValidationException("Commodity quantity must be greater than zero.");
            }
        }
    }

    /**
     * Handles file upload for new requests
     */
    private String handleFileUpload(MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            try {
                String filePath = fileUpload.uploadFileToLocalDirectory(file);
                log.debug("File uploaded successfully to: {}", filePath);
                return filePath;
            } catch (IOException ex) {
                log.error("File upload failed: {}", ex.getMessage());
                throw new IOException("Failed to upload file: " + ex.getMessage(), ex);
            }
        }
        return null;
    }

    /**
     * Handles file update for existing requests
     */
    private String handleFileUpdate(
            MultipartFile newFile,
            String bodyFilePath,
            String existingFilePath
    ) throws IOException {
        // CASE 1: User uploaded a new file
        if (newFile != null && !newFile.isEmpty()) {
            deleteExistingFile(existingFilePath);
            return fileUpload.uploadFileToLocalDirectory(newFile);
        }
        // CASE 2: User explicitly wants to remove the file
        else if (bodyFilePath == null && existingFilePath != null) {
            deleteExistingFile(existingFilePath);
            return null;
        }
        // CASE 3: User didn't change the file
        return existingFilePath;
    }

    /**
     * Deletes an existing file if present
     */
    private void deleteExistingFile(String filePath) {
        if (filePath != null) {
            File oldFile = new File(filePath);
            if (oldFile.exists() && !oldFile.delete()) {
                log.warn("Failed to delete old file: {}", oldFile.getAbsolutePath());
            }
        }
    }

    /**
     * Gets the initial status for new requests
     */
    private Status getInitialRequestStatus() throws NotFoundException {
        try {
            Status status = statusService.findByStatusCode("requestCreated");
            if (status == null) {
                throw new NotFoundException("Status 'requestCreated' not found.");
            }
            return status;
        } catch (Exception ex) {
            log.error("Error retrieving initial status: {}", ex.getMessage());
            throw new NotFoundException("Error retrieving status: " + ex.getMessage());
        }
    }

    /**
     * Builds initial request object
     */
    private Request buildInitialRequest(
            Request body,
            String filePath,
            Status status,
            User requester,
            boolean isBranchStationeryRequest,
            boolean isBranchNonStationeryRequest
    ) {

        return Request.builder()
                .name(body.getName())
                .priority(body.getPriority())
                .timeOfSubmissionOfRequest(body.getTimeOfSubmissionOfRequest())
                .signaturePath(filePath)
                .status(status)
                .requester(requester)
                .description(body.getDescription())
                .isBranchStationery(isBranchStationeryRequest)
                .isBranchNonStationery(isBranchNonStationeryRequest)
                .deleted(false)
                .build();
    }

    /**
     * Saves request with error handling
     */
    private Request saveRequest(Request request) {
        try {
            return requestRepository.save(request);
        } catch (Exception ex) {
            log.error("Error saving request: {}", ex.getMessage());
            throw new RuntimeException("Failed to save request: " + ex.getMessage(), ex);
        }
    }

    /**
     * Adds commodities to a request
     */
    private void addCommoditiesToRequest(Request request, List<RequestCommodityDTO> requestCommodities)
            throws NotFoundException {
        try {
            List<RequestCommodity> commoditiesToSave = new ArrayList<>();

            for (RequestCommodityDTO dto : requestCommodities) {
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                RequestCommodity requestCommodity = RequestCommodity.builder()
                        .request(request)
                        .commodity(commodity)
                        .quantity(dto.getQuantity())
                        .build();
                commoditiesToSave.add(requestCommodity);
            }

            // Batch save
            requestCommodityRepository.saveAll(commoditiesToSave);
            log.debug("Added {} commodities to request ID: {}", commoditiesToSave.size(), request.getId());

        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error adding commodities to request: {}", ex.getMessage());
            throw new RuntimeException("Failed to add commodities to request", ex);
        }
    }

    /**
     * Converts request commodities to DTOs
     */
    private List<CommodityWithQuantityDTO> commodityWithQuantityDTO(List<RequestCommodity> requestCommodities) {
        return requestCommodities.stream()
                .map(rc -> new CommodityWithQuantityDTO(rc.getCommodity(), rc.getQuantity()))
                .collect(Collectors.toList());
    }

    /**
     * Finds the supervisor who should approve the request
     */
    private User findSupervisorToApproveRequest() throws NotFoundException {
        User requester = currentUserService.getCurrentUser();

        if (requester == null || requester.getTitle() == null || requester.getBranch() == null) {
            throw new IllegalStateException("Requester must have a title and be assigned to a branch.");
        }

        if (requester.getTitle().getReportsTo() == null) {
            throw new NotFoundException("The requester's title has no assigned manager (reportsTo is null).");
        }

        Long managerTitleId = requester.getTitle().getReportsTo().getId();
        Long branchId = requester.getBranch().getId();

        List<User> possibleManagers = userRepository.findByTitleIdAndBranchId(managerTitleId, branchId);

        if (possibleManagers.isEmpty()) {
            log.warn("No manager found with title ID: {} in branch ID: {}", managerTitleId, branchId);
            throw new NotFoundException(
                    "No manager found with title ID: " + managerTitleId + " in branch ID: " + branchId
            );
        }

        log.debug("Found supervisor: {} for requester: {}",
                possibleManagers.get(0).getEmail(), requester.getEmail());
        return possibleManagers.get(0);
    }

    /**
     * Notifies the supervisor about a new request
     */
    private String notifySupervisor(String email, Request request, String name) {
        try {
            boolean emailStatus = emailService.sendEmailVerification(
                    email,
                    String.format("Action Required – Request Pending Processing: ( #%s )", request.getId()),
                    null,
                    name,
                    "View Request",
                    null,
                    "requests",
                    request,
                    null,
                    null,
                    null,
                    null
            );

            if (!emailStatus) {
                log.warn("Failed to send notification to supervisor: {}", email);
                return String.format("Request approval notification not sent to %s", email);
            }

            log.info("Notification sent to supervisor: {} for request ID: {}",
                    email, request.getId());
            return String.format("Request approval notification has been sent to %s", email);

        } catch (Exception ex) {
            log.error("Error sending notification: {}", ex.getMessage());
            return "Failed to send notification due to error: " + ex.getMessage();
        }
    }

    /**
     * Determines the user's role in the system
     */
    private String determineUserRole() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null || currentUser.getTitle() == null || currentUser.getTitle().getRole() == null) {
            log.warn("Cannot determine user role - missing user, title, or role information");
            return "";
        }

        Role role = currentUser.getTitle().getRole();
        System.out.println("Current User Role::: " + role.getName());
        return switch (role.getName()) {
            case "OFFICER", "Officer" -> "OFFICER";
            case "MANAGER", "Manager" -> "MANAGER";
            case "HEAD_OF_DEPARTMENT", "Head Of Department", "Head of Department" -> "HOD";
            case "ADMIN_OFFICER", "Admin Officer" -> "ADMIN_OFFICER";
            case "ADMIN_MANAGER", "Admin Manager" -> "ADMIN_MANAGER";
            case "BOM", "BRANCH_OPERATIONS_MANAGER", "Branch Operations Manager" -> "BOM";
            case "BM", "BRANCH_MANAGER", "Branch Manager" -> "BM";
            default -> "";
        };
    }

    /**
     * Returns a list of user IDs that includes the current user (manager)
     * and all users whose titles report (directly or indirectly) to the manager's title.
     */
    private List<Long> getManagerAndSubordinateUserIds() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null || currentUser.getTitle() == null) {
            log.warn("Cannot get subordinates - current user or title is null");
            return Collections.emptyList();
        }

        Long managerTitleId = currentUser.getTitle().getId();
        List<Long> subordinateTitleIds = titleRepository.findSubordinateTitleIds(managerTitleId);
        List<Long> userIds = userRepository.findUserIdsByTitleIds(subordinateTitleIds);

        if (!userIds.contains(currentUser.getId())) {
            userIds.add(currentUser.getId());
        }

        log.debug("Found {} subordinate users for manager title ID: {}", userIds.size() - 1, managerTitleId);
        return userIds;
    }

    /**
     * Determines the list of requester user IDs based on the current user's role and the provided request status.
     */
    private List<Long> requestersList(String status) {
        final boolean pendingIssuedOrDashboard = "PENDING".equalsIgnoreCase(status)
                || "ISSUED".equalsIgnoreCase(status)
                || "DASHBOARD".equalsIgnoreCase(status);

        String userRole = determineUserRole();
        log.debug("Determining requesters list for role: {} and status: {}", userRole, status);

        System.out.println("We are finally here:: " + status + userRole);
        List<Long> result = switch (userRole) {
            case "OFFICER" -> List.of(currentUserService.getCurrentUser().getId());

            case "MANAGER", "HOD" -> getManagerAndSubordinateUserIds();

            case "ADMIN_OFFICER" -> {
                if (pendingIssuedOrDashboard) {
                    /*
                    List<Long> headOfficeUserIds = userRepository.findUserIdsFromHeadOffice();
                    List<Long> bomIds = userRepository.findAllBranchOperationsManagerIds();
                    Set<Long> allowedUserIds = new HashSet<>();
                    allowedUserIds.addAll(headOfficeUserIds);
                    allowedUserIds.addAll(bomIds);
                    yield new ArrayList<>(allowedUserIds)
                     */
                    // Ensure that all users requests are visible to the Admin Officer
                    yield Collections.emptyList();

                } else if ("CREATED".equalsIgnoreCase(status)) {

//                    yield List.of(currentUserService.getCurrentUser().getId());
                    yield Collections.emptyList();
                } else if ("REJECTED".equalsIgnoreCase(status)) {
                    yield List.of(currentUserService.getCurrentUser().getId());
                } else {
                    yield List.of(currentUserService.getCurrentUser().getId());
                }
            }

            case "ADMIN_MANAGER" -> {
                if ("PENDING".equalsIgnoreCase(status) || "ISSUED".equalsIgnoreCase(status)) {
                    yield Collections.emptyList();
                } else if ("CREATED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                    yield getManagerAndSubordinateUserIds();
                } else {
                    yield getManagerAndSubordinateUserIds();
                }
            }

            case "BOM", "BM" -> {
                if (pendingIssuedOrDashboard || "CREATED".equalsIgnoreCase(status)) {
                    /*
                    List<Long> headOfficeUserIds = userRepository.findUserIdsFromHeadOffice();
                    List<Long> bomIds = userRepository.findAllBranchOperationsManagerIds();
                    Set<Long> allowedUserIds = new HashSet<>();
                    allowedUserIds.addAll(headOfficeUserIds);
                    allowedUserIds.addAll(bomIds);
                    yield new ArrayList<>(allowedUserIds)
                    yield userRepository.findUserIdsByBranchId(currentBranchId);

                    List<Long> branchUsers = userRepository.findUserIdsByBranchId(currentBranchId);
                    List<Long> subordinates = getManagerAndSubordinateUserIds();
                */
                    Long currentBranchId = currentUserService.getCurrentUser().getBranch().getId();

                    yield userRepository.findUserIdsByBranchId(currentBranchId);

                } else if ("REJECTED".equalsIgnoreCase(status)) {
                    yield List.of(currentUserService.getCurrentUser().getId());
                } else {
                    yield List.of(currentUserService.getCurrentUser().getId());
                }
            }
            default -> Collections.emptyList();
        };

        log.debug("Returning {} user IDs for requesters list", result.size());
        return result;
    }

    /**
     * Determines if the current user can see branch non-stationery requests.
     *
     * @return true if they can see them, false if they cannot, or null if not applicable.
     */
    private Boolean canSeeBranchNonStationaryRequests() {
        String userRole = determineUserRole();

        return switch (userRole) {
            case "ADMIN_OFFICER", "ADMIN_MANAGER" -> true;
            default -> null; // For all other roles, visibility doesn't apply
        };
    }

    /**
     * Determines if the current user can see branch stationery requests.
     *
     * @return - true if the user should see stationery requests from branches,
     * - false if explicitly denied,
     * - null if visibility does not apply for the role.
     */
    private Boolean canSeeBranchStationaryRequests() {
        String userRole = determineUserRole();

        if (userRole.equals("BOM")) {
            return true;
        }
        return null; // Role is undefined or empty — cannot determine visibility
    }

    private boolean determineIfUserIsAdmin() {
        String userRole = determineUserRole();
        return userRole.equals("ADMIN_OFFICER");
    }

    /**
     * Determines sort direction from string
     */
    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    /**
     * Converts a list of Request entities to DTOs
     */
    public List<RequestDTO> convertToRequestDTOList(List<Request> requests) {
        return requests.stream().map(request -> RequestDTO.builder()
                .id(request.getId())
                .name(request.getName())
                .priority(request.getPriority())
                .description(request.getDescription())
                .signaturePath(request.getSignaturePath())
                .status(request.getStatus())
                .timeOfSubmissionOfRequest(request.getTimeOfSubmissionOfRequest())
                .createDate(request.getCreateDate())
                .lastModified(request.getLastModified())
                .createdBy(request.getCreatedBy())
                .lastModifiedBy(request.getLastModifiedBy())
                .requester(request.getRequester())
                .currentApprover(request.getCurrentApprover())
                .commodities(getRequestCommodities(request))
                .requestReports(request.getRequestReportDAOS())
                .assetTypeId(request.getAssetType() != null ? request.getAssetType().getId() : null)
                .assetType(AssetTypeRefDTO.from(request.getAssetType()))
                .attributes(request.getAttributes())
                .build()).toList();
    }

    /**
     * Gets commodities for a request
     */
    private List<CommodityWithQuantityDTO> getRequestCommodities(Request request) {
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(request.getId());

        return requestCommodities.stream()
                .map(rc -> CommodityWithQuantityDTO.builder()
                        .commodity(rc.getCommodity())
                        .quantity(rc.getQuantity())
                        .build())
                .toList();
    }

    /**
     * Determines which status IDs to use based on user role
     */
    private List<Long> determineStatusIds(List<Long> statusIDs) {
        if (statusIDs == null) {
            statusIDs = new ArrayList<>();
        }

        return switch (determineUserRole()) {
            /*
             *  Display the created request only to managers and the subordinates
             */
            case "OFFICER", "MANAGER" -> {
                statusIDs.remove(3L);
                statusIDs.add(1L);
                yield statusIDs;
            }
            default -> statusIDs;
        };
    }

    /**
     * Builds a request report DAO from a request report
     */
    private RequestReportDAO buildObject(RequestReport rep) {
        return RequestReportDAO
                .builder()
                .id(rep.getId())
                .request(rep.getRequest())
                .status(rep.getStatus())
                .approver(rep.getApprover())
                .comment(rep.getComment())
                .createDate(rep.getCreateDate())
                .build();
    }

    /**
     * Builds a detailed request DTO with all related data
     */
    private RequestDTO buildDetailedRequestDTO(
            Request request,
            List<CommodityWithQuantityDTO> commodities,
            List<RequestReportDAO> reports
    ) {
        return RequestDTO.builder()
                .id(request.getId())
                .name(request.getName())
                .priority(request.getPriority())
                .commodities(commodities)
                .description(request.getDescription())
                .signaturePath(request.getSignaturePath())
                .status(request.getStatus() != null ?
                        Status.builder()
                                .id(request.getStatus().getId())
                                .name(request.getStatus().getName())
                                .description(request.getStatus().getDescription())
                                .status(request.getStatus().getStatus())
                                .code(request.getStatus().getCode())
                                .build() : null)
                .timeOfSubmissionOfRequest(request.getTimeOfSubmissionOfRequest())
                .isBranchStationery(request.getIsBranchStationery())
                .createdBy(request.getCreatedBy())
                .createDate(request.getCreateDate())
                .lastModified(request.getLastModified())
                .lastModifiedBy(request.getLastModifiedBy())
                .requester(request.getRequester() != null ?
                        userBuilder.builder(request.getRequester()) : null)
                .requestReports(reports)
                .currentApprover(request.getCurrentApprover() != null ?
                        userBuilder.builder(request.getCurrentApprover()) : null)
                .assetTypeId(request.getAssetType() != null ? request.getAssetType().getId() : null)
                .assetType(AssetTypeRefDTO.from(request.getAssetType()))
                .attributes(request.getAttributes())
                .build();
    }
}
