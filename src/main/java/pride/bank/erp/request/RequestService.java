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
    private final pride.bank.erp.auditing.trail.AuditService auditService;
    /** Decides how far a dashboard query may reach — see DashboardScopeService. */
    private final pride.bank.erp.dashboard.DashboardScopeService dashboardScopeService;
    private final FileUpload fileUpload;
    private final RequestSearchDao requestSearchDao;
    private final StatusService statusService;
    private final CommodityService commodityService;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final Email emailService;
    private final CurrentUserService currentUserService;

    /** The one resolver for how far a person may see — see AccessScopeService. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;
    private final TitleRepository titleRepository;
    private final UserBuilder userBuilder;
    private final WorkflowEngineService workflowEngineService;
    private final AssetTypeService assetTypeService;
    private final RequestReportRepository requestReportRepository;
    private final pride.bank.erp.workflowinstance.RequestWorkflowInstanceRepository instanceRepository;

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

        /*
         * A store restock is asked for deliberately, not guessed from who is asking. It needs
         * RESTOCK_STORE, and only a branch can ask: Head Office is where restocks come from, and it
         * fills its own store through stock orders.
         */
        if (body.isForStoreRestock()) {
            boolean mayRestock = requester.getAuthorities().stream()
                    .anyMatch(a -> Constants.RESTOCK_STORE.equals(a.getAuthority()));
            if (!mayRestock) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Restocking the branch store needs the Restock Store permission.");
            }
            if (isHeadOfficeUser) {
                throw new BusinessRuleException("Head Office restocks its store through stock orders, "
                        + "not requests. A store-restock request is raised by a branch.");
            }
        }

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

        auditService.record(pride.bank.erp.auditing.trail.AuditEventType.CREATED,
                pride.bank.erp.auditing.trail.AuditModule.REQUESTS, "REQUEST_CREATED",
                "Raised request #" + savedRequest.getId() + " for " + commodities.size() + " item line(s)",
                pride.bank.erp.auditing.trail.AuditSeverity.INFO,
                "Request", savedRequest.getId());

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
        requireRequestChangeable(existingRequest);
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
            requireRequestChangeable(request);
            request.setDeleted(true);
            request.setDeletionDate(LocalDateTime.now());
            requestRepository.save(request);
            log.info("Successfully marked request ID {} as deleted", id);
            auditService.record(pride.bank.erp.auditing.trail.AuditEventType.DELETED,
                    pride.bank.erp.auditing.trail.AuditModule.REQUESTS, "REQUEST_DELETED",
                    "Deleted request #" + id,
                    pride.bank.erp.auditing.trail.AuditSeverity.WARNING,
                    "Request", id);
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
            String requestedBy,
            String approver,
            Long requesterId,
            Long approverId,
            Long requesterDepartmentId,
            Long requesterBranchId,
            Boolean deleted,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long currentApproverId
    ) {
        try {
            requireBranchFilterInReach(requesterBranchId);
            log.debug("Fetching requests with filters - page: {}, statusIds: {}", pageNumber, statusIds);

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
                    .requesterIds(requestersList())
                    .requestedBy(requestedBy)
                    .approver(approver)
                    // Picked from the directory. Narrows within the caller's scope; `approverId` is
                    // deliberately not `currentApproverId`, which would switch that scope off.
                    .raisedByUserId(requesterId)
                    .approverId(approverId)
                    .requesterDepartmentId(requesterDepartmentId)
                    // The same LEFT-joined predicate the dashboard's branch filter uses.
                    .requesterBranchId(requesterBranchId)
                    .deleted(deleted)
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .startDate(startDate)
                    .endDate(endDate)
                    .currentUserId(currentUserService.getCurrentUser().getId())
                    .currentApproverId(resolveApproverInbox(currentApproverId))
                    .currentUnitId(currentUserService.getCurrentUser().getUnit() != null
                            ? currentUserService.getCurrentUser().getUnit().getId() : null)
                    .build();

            Page<Request> requestPageResult = requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
            List<RequestDTO> dtoList = convertToRequestDTOList(requestPageResult.getContent());

            log.debug("Retrieved {} requests out of {} total",
                    dtoList.size(), requestPageResult.getTotalElements());

            return new PageImpl<>(dtoList, requestPageResult.getPageable(), requestPageResult.getTotalElements());

        } catch (org.springframework.security.access.AccessDeniedException e) {
            /*
             * A refusal is not an empty register.
             *
             * Everything used to fall into the blanket catch below and come back as `Page.empty()`,
             * so a fault and "there are no requests" were the same screen. That is bad enough on its
             * own; with the scope checks now in place it would have been worse, because a boundary
             * would have rendered as an ordinary empty list and hidden the fact that the caller was
             * refused. Rethrown so the client gets a 403.
             */
            throw e;
        } catch (Exception e) {
            log.error("Error fetching requests", e);
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
        requireRequestReadable(request);
        RequestCommodityReports data = generateRequestReportsAndCommodities(request);
        return buildDetailedRequestDTO(request, data.getCommodities(), data.getReports());
    }

    /**
     * Gets a summary of asset requests for the current year and branch
     *
     * @return List of summary DTOs
     */
    @Transactional(readOnly = true)
    public List<AssetRequestSummaryDTO> getCurrentYearRequestSummary(Long requestedBranchId) {
        Long receiptAcknowledged = 7L; // ID for Receipt Acknowledged Status
        Long requestRejected = 2L;     // ID for Request Rejected Status
        /*
         * Scope, not the caller's own branch.
         *
         * This read currentUser.getBranch().getId() unconditionally, so a Head Office administrator
         * saw Head Office's fulfilment under a widget titled for the organisation, and a user with
         * no duty station on record hit a NullPointerException before the query ran.
         */
        Long branchId = dashboardScopeService.resolveBranch(requestedBranchId);
        Long ownerId = dashboardScopeService.resolveOwner();

        log.debug("Fetching asset request summaries for branch {} / owner {}", branchId, ownerId);

        try {
            List<AssetRequestSummaryDTO> summaries = requestRepository
                    .fetchAssetRequestSummariesScoped(receiptAcknowledged, requestRejected, branchId, ownerId)
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
    /**
     * Statuses at which a request is finished and stops being "open".
     *
     * <p>Resolved by code, never by hardcoded id. The dashboard queue previously defaulted to the
     * literal id {@code 3} and relied on the status table having been seeded into an empty database
     * in a particular order — any environment seeded differently queued the wrong status silently.
     *
     * <p>Only two states end a request: it was rejected, or the requester acknowledged receiving the
     * item. Everything between — every approval tier, the issuance, and the movement carrying the
     * goods — is still open, which is what makes a requester's own request stay on their dashboard
     * from the moment they raise it until it physically reaches them.
     */
    private static final List<String> TERMINAL_REQUEST_CODES =
            List.of("requestRejected", "receiptAcknowledged");

    private List<Long> terminalStatusIds() {
        return TERMINAL_REQUEST_CODES.stream()
                .map(code -> {
                    try {
                        Status status = statusService.findByStatusCode(code);
                        return status != null ? status.getId() : null;
                    } catch (Exception e) {
                        log.warn("Status code '{}' is not seeded — open-request filtering will not "
                                + "exclude it", code);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Requests waiting on this person's decision.
     *
     * <p>Exactly what the workflow engine has routed to them: assigned to them individually, or to a
     * unit they belong to for a group step. It therefore disappears from their queue the moment they
     * approve, because approving moves {@code currentApprover} to the next tier — which is the
     * behaviour the queue is supposed to have and previously did not.
     *
     * <p>The old dashboard queue answered a different question entirely. It filtered by status id
     * and by a hardcoded switch on the user's role name, so it showed Officers newly-created
     * requests and everyone else approved ones, with no notion of who was actually being waited on.
     * Two people looking at one widget labelled "Work Queue" were looking at different stages of the
     * pipeline, and neither was looking at their own queue.
     */
    @Transactional(readOnly = true)
    public Page<RequestDTO> findAwaitingMyDecision(int pageSize, int pageNumber) {
        User me = currentUserService.getCurrentUser();
        if (me == null) return Page.empty();

        RequestPage requestPage = RequestPage.builder()
                .pageSize(pageSize)
                .pageNumber(pageNumber)
                .sortBy("createDate")
                .sortDirection(determineSortDirection(DESCENDING_SORT_DIRECTION))
                .build();

        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .currentApproverId(me.getId())
                .currentUnitId(me.getUnit() != null ? me.getUnit().getId() : null)
                .excludedStatusIds(terminalStatusIds())
                .deleted(false)
                .build();

        Page<Request> page = requestSearchDao.findAllWithFilters(requestPage, criteria);
        return new PageImpl<>(convertToRequestDTOList(page.getContent()),
                page.getPageable(), page.getTotalElements());
    }

    /**
     * Open requests, scoped to what the viewer is entitled to see.
     *
     * <p>"Open" means raised and not yet finished — see {@link #TERMINAL_REQUEST_CODES}. A request
     * stays here through every approval tier, through issuance, and through the movement carrying
     * the item, and leaves only when the requester acknowledges receipt.
     *
     * <p>Breadth follows the viewer's dashboard scope rather than their role name: SELF shows only
     * what they raised, BRANCH everything raised at their duty station, ALL the whole bank (or one
     * branch when the page's selector is focused).
     */
    @Transactional(readOnly = true)
    public Page<RequestDTO> findOpenRequests(int pageSize, int pageNumber, Long requestedBranchId) {
        RequestPage requestPage = RequestPage.builder()
                .pageSize(pageSize)
                .pageNumber(pageNumber)
                .sortBy("createDate")
                .sortDirection(determineSortDirection(DESCENDING_SORT_DIRECTION))
                .build();

        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .excludedStatusIds(terminalStatusIds())
                .raisedByUserId(dashboardScopeService.resolveOwner())
                .requesterBranchId(dashboardScopeService.resolveBranch(requestedBranchId))
                .deleted(false)
                .build();

        Page<Request> page = requestSearchDao.findAllWithFilters(requestPage, criteria);
        return new PageImpl<>(convertToRequestDTOList(page.getContent()),
                page.getPageable(), page.getTotalElements());
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
                .forStoreRestock(body.isForStoreRestock())
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
     * Returns a list of user IDs that includes the current user (manager)
     * and all users whose titles report (directly or indirectly) to the manager's title.
     */
    /**
     * Me and my subordinates — <b>at my own branch</b>.
     *
     * <h2>Two ways this used to fail open</h2>
     * <b>Subordinates were resolved by title alone.</b> A title is organisation-wide — "Teller" is the
     * same title at every branch — so {@code findUserIdsByTitleIds} answered with everyone in the bank
     * holding a subordinate title. Measured on live data, the Gulu Head Cashier (a SUPERVISOR, and so
     * SELF-scoped) had subordinates spanning Gulu <em>and Mbarara</em>, and therefore saw Mbarara's
     * requests on the All Requests tab. The reporting line is a property of the title; the branch has
     * to be applied separately, and was not.
     *
     * <p><b>An empty list is the widest possible answer, not the narrowest.</b> {@code RequestSearchDao}
     * only applies the requester predicate when the list is non-empty, so returning nothing means "no
     * restriction". This returned {@code Collections.emptyList()} for a user with no title — handing
     * them every request in the bank, which is the opposite of what a missing title should mean.
     *
     * <p>So every path below ends in at least the caller's own id. A configuration gap narrows
     * somebody to themselves; it never widens them to everybody.
     */
    private List<Long> getManagerAndSubordinateUserIds() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            // Nothing to narrow to, and an empty list would mean "everything".
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your account could not be identified, so requests cannot be shown.");
        }

        List<Long> userIds = new ArrayList<>();
        userIds.add(currentUser.getId());

        Long branchId = currentUser.getBranch() != null ? currentUser.getBranch().getId() : null;
        if (currentUser.getTitle() == null || branchId == null) {
            // No reporting line, or no duty station to confine it to: just their own records. Both are
            // configuration gaps, and neither is a reason to show somebody the bank.
            log.warn("User {} has no title or no branch — narrowing requests to their own records",
                    currentUser.getId());
            return userIds;
        }

        List<Long> subordinateTitleIds = titleRepository.findSubordinateTitleIds(
                currentUser.getTitle().getId());
        if (!subordinateTitleIds.isEmpty()) {
            userRepository.findUserIdsByTitleIdsAndBranchId(subordinateTitleIds, branchId).stream()
                    .filter(id -> !userIds.contains(id))
                    .forEach(userIds::add);
        }

        log.debug("Found {} subordinate user(s) at branch {} for title {}",
                userIds.size() - 1, branchId, currentUser.getTitle().getId());
        return userIds;
    }

    /**
     * Which people's requests this user may see.
     *
     * <p>An empty list means <b>no restriction</b>, which is worth stating because it reads like the
     * opposite.
     *
     * <p>Reads {@link pride.bank.erp.access.AccessScopeService} — the same resolver the assets
     * listing and every write check consult — so a person's reach is one answer given in one place
     * rather than a rule per module. Before this there were three ways of saying it: a switch on
     * {@code title.role.name}, the {@code VIEW_ALL_BRANCHES} permission, and the dashboard scopes.
     * They could and did disagree.
     *
     * <p>{@code SELF} yields the person and the people who report to them rather than the person
     * alone. A manager's own request list would otherwise be one row, and approving is a different
     * act from raising — the reporting line is what makes the list useful to them.
     */
    /**
     * Refuses unless the caller may read this request — for satellites that hang off one.
     *
     * <p>The approval trail, the request report and the issuance record are all <em>about</em> a
     * request, so they answer to the same rule rather than inventing their own.
     */
    @Transactional(readOnly = true)
    public void requireReadableById(Long requestId) throws NotFoundException {
        requireRequestReadable(findRequest(requestId));
    }

    /**
     * The branch a request belongs to — its requester's duty station.
     *
     * <p>A request has no branch of its own; it belongs to wherever the person who raised it works.
     * {@code RequestSearchDao} says the same thing when it filters through {@code requester.branch}.
     */
    private static Long branchOf(Request request) {
        return (request.getRequester() != null && request.getRequester().getBranch() != null)
                ? request.getRequester().getBranch().getId() : null;
    }

    private static Long requesterOf(Request request) {
        return request.getRequester() != null ? request.getRequester().getId() : null;
    }

    /**
     * Whether the caller was ever part of this request's approval.
     *
     * <h2>Why the current routing is not enough</h2>
     * {@code currentApprover} names whoever must act <b>now</b>, and approving moves it to the next
     * tier — so an approver lost the right to read the record at the very instant they approved it.
     * A supervisor is SELF-scoped by default ({@code UnitRoleSeeder.SELF_SCOPED_ROLES}), and at SELF
     * the scope check keys on the <em>requester</em>, who is somebody else — so every other limb
     * failed too and the page answered "You do not have access to this record" immediately after a
     * successful approval.
     *
     * <p>Worse than a stray message: the request can remain in their listing, because
     * {@code getManagerAndSubordinateUserIds} includes a subordinate requester. A row that is in your
     * list and will not open is the failure this scoping exists to avoid.
     *
     * <p>Having been routed a request is a <b>durable</b> relationship with it. Someone who approved
     * something should be able to look at what they approved — that is the ordinary expectation and
     * the basis of any later question about the decision.
     */
    private boolean hasActedOn(Request request) {
        User current = currentUserService.getCurrentUser();
        return current != null
                && workflowEngineService.hasActedOnRequest(request.getId(), current.getId());
    }

    /** True when the workflow has routed this request to the caller, personally or via their unit. */
    private boolean isRoutedToCaller(Request request) {
        User current = currentUserService.getCurrentUser();
        if (current == null) return false;

        boolean toMe = request.getCurrentApprover() != null
                && Objects.equals(request.getCurrentApprover().getId(), current.getId());
        boolean toMyUnit = request.getCurrentUnit() != null && current.getUnit() != null
                && Objects.equals(request.getCurrentUnit().getId(), current.getUnit().getId());
        return toMe || toMyUnit;
    }

    /**
     * Refuses a request the caller has no business reading.
     *
     * <h2>Why reading is wider than changing</h2>
     * Scope alone is the wrong rule here. An approval step routinely routes a branch request to
     * somebody at Head Office or in a unit, and the whole point of the inbox is that they can open
     * it — so a plain branch check would refuse the very people the workflow just asked to act. The
     * listing already makes this exception; the single read has to make the same one or the inbox
     * would list rows that will not open.
     *
     * <p>So: in scope, <b>or</b> routed to you. Both are legitimate relationships to a request.
     */
    private void requireRequestReadable(Request request) {
        if (isRoutedToCaller(request)) return;
        if (hasActedOn(request)) return;
        if (mayIssue()) return;
        accessScopeService.requireCanView(
                pride.bank.erp.access.ScopeSubject.REQUESTS, branchOf(request), requesterOf(request));
    }

    /**
     * Whether the caller's job is to fulfil requests.
     *
     * <h2>Why this is a third way in</h2>
     * Issuance is cross-branch by design — the ordinary case is Head Office fulfilling a branch's
     * request, the same reason {@code createMovement} carries no scope check. An issuer must be able
     * to open the request they are issuing against, and neither of the other two limbs gets them
     * there: the requester's branch is not theirs, and the "routed to me" exception has already
     * lapsed, because {@code WorkflowEngineService} nulls {@code currentApprover} the moment the last
     * step completes — which is exactly when issuing begins.
     *
     * <p><b>Measured before adding it.</b> Of the ten accounts holding {@code ISSUE_ITEMS}, five have
     * no cross-branch request reach: three Head Office storekeepers with no unit, and two at Gulu.
     * Without this limb those five could no longer do the job the permission names.
     *
     * <p>It is a read, and only a read: {@code requireRequestChangeable} does not consult it, so an
     * issuer still cannot edit or delete what they are issuing against. And it is far narrower than
     * what stood here before, which was that any holder of {@code READ_REQUEST} could read every
     * request in the bank by id.
     */
    private boolean mayIssue() {
        User current = currentUserService.getCurrentUser();
        return current != null && current.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(Constants.ISSUE_ITEMS::equals);
    }

    /**
     * Refuses a change to a request the caller has no business changing.
     *
     * <h2>Deliberately narrower than the read</h2>
     * Being asked to approve something is not a licence to edit or delete it. Updating a request is
     * the requester's act — it is how a rejected one is corrected and resubmitted — and deleting is
     * administrative, so both answer to the ordinary branch/owner rule with no routing exception.
     *
     * <p>Approving is a different act again and is guarded where it belongs, by
     * {@code WorkflowEngineService.requireActorIsRouted}.
     */
    private void requireRequestChangeable(Request request) {
        accessScopeService.requireCanManage(
                pride.bank.erp.access.ScopeSubject.REQUESTS, branchOf(request), requesterOf(request));
    }

    /**
     * Whose approval inbox the listing may show — the caller's own, unless they may see any branch's.
     *
     * <h2>The hole this closes</h2>
     * {@code currentApproverId} arrived straight from the request, and {@code RequestSearchDao}
     * <b>skips the branch/self scope entirely whenever it is set</b> — deliberately, because an
     * approver often sits above the requester and the subordinate restriction would hide the very
     * work the inbox exists to show. Correct on its own; fatal in combination with a client-supplied
     * value.
     *
     * <p>So {@code GET /requests?currentApproverId=<a colleague>} dropped the scope and returned
     * everything routed to that person, from every branch, to any holder of {@code READ_REQUEST}.
     * The unit half of that predicate was already server-derived; only the approver half was not.
     *
     * <p>This is the same fault the movements approval inbox had — {@code
     * GET /movements/pending-approval/{approverId}} took the approver from the path — and it is
     * closed the same way: the caller's own id, with {@code MANAGE_ALL_BRANCH_REQUESTS} as the
     * explicit override, since that grant is precisely "may act on any branch's requests".
     *
     * <p>Nothing changes for the front end, which only ever sends the signed-in user's own id.
     *
     * @param requested what the client asked for; may be null, meaning "not the inbox view"
     * @return the approver id to filter on, or null to leave the inbox filter off
     */
    private Long resolveApproverInbox(Long requested) {
        if (requested == null) return null;

        User current = currentUserService.getCurrentUser();
        Long own = current != null ? current.getId() : null;
        if (requested.equals(own)) return own;

        boolean mayReadAnyInbox = accessScopeService.scopeFor(
                pride.bank.erp.access.ScopeSubject.REQUESTS,
                pride.bank.erp.access.ScopeAction.MANAGE) == pride.bank.erp.dashboard.DashboardScope.ALL;
        if (mayReadAnyInbox) {
            log.info("User {} is reading approver {}'s inbox under MANAGE_ALL_BRANCH_REQUESTS",
                    own, requested);
            return requested;
        }

        // Narrowed to their own rather than refused: the parameter is a view selector, and the page
        // that sends it is asking for "my inbox" in every legitimate case.
        log.warn("User {} asked for approver {}'s inbox without cross-branch request management; "
                + "narrowed to their own", own, requested);
        return own;
    }

    /**
     * What each of these requests is currently waiting for, keyed by request id.
     *
     * <p>Absent for a finished or rejected request, and for one predating the workflow engine — the
     * caller reads that as "no action is due", which is the safe answer: without a workflow there is
     * no way to know the stage, and offering a wrong action on a real request is worse than offering
     * none.
     */
    private Map<Long, String> currentStepTypes(List<Request> requests) {
        List<Long> ids = requests.stream()
                .map(Request::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, String> byRequest = new HashMap<>();
        for (Object[] row : instanceRepository.findCurrentStepTypes(ids)) {
            if (row[0] != null && row[1] != null) {
                byRequest.put((Long) row[0], stageOf(row[1], row.length > 2 ? row[2] : null));
            }
        }
        return byRequest;
    }

    /**
     * The stage a step represents, which is not always its type.
     *
     * <h2>ISSUANCE_APPROVAL is a stage the enum does not have a name for</h2>
     * Signing off an issuance is stored as a {@code REQUEST_APPROVAL} step carrying
     * {@code approverSubject = ISSUER}: it is routed to the <em>issuer's</em> manager, and
     * {@code resolveStatusCode} maps it to {@code issuanceApproved} rather than to an approval
     * status. Everything except the enum already treats it as its own stage.
     *
     * <p>Reporting it as a plain approval is not cosmetic. The two acts post to different endpoints —
     * {@code POST /approvals} merely advances the step, while {@code POST /approve-issuance} creates
     * the fulfilment movement that actually sends the items. A screen told "this is an approval"
     * offers the first, and the request advances recorded as approved with nothing on its way.
     *
     * <p>Named here rather than on {@code StepType} because it is a reading of two fields, not a new
     * kind of step: the workflow editor still configures an approval step with an issuer subject.
     */
    private static String stageOf(Object stepType, Object approverSubject) {
        boolean issuerApproval =
                pride.bank.erp.workflow.StepType.REQUEST_APPROVAL.equals(stepType)
                        && pride.bank.erp.workflow.ApproverSubject.ISSUER.equals(approverSubject);
        return issuerApproval ? "ISSUANCE_APPROVAL" : stepType.toString();
    }

    /**
     * A branch named in the Branch filter must be one the caller may see.
     *
     * <p>The filter narrows within {@link #requestersList()}, so naming another branch could not
     * widen anything - it would return an empty page. Refused instead, because an empty page reads
     * as "that branch raised nothing", which is a statement about data the caller may not look at.
     */
    private void requireBranchFilterInReach(Long requesterBranchId) {
        if (requesterBranchId == null) return;
        if (accessScopeService.scopeFor(pride.bank.erp.access.ScopeSubject.REQUESTS,
                pride.bank.erp.access.ScopeAction.VIEW) == pride.bank.erp.dashboard.DashboardScope.ALL) {
            return;
        }
        if (!requesterBranchId.equals(currentUserBranchId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view requests for your own branch.");
        }
    }

    private List<Long> requestersList() {
        return switch (accessScopeService.scopeFor(
                pride.bank.erp.access.ScopeSubject.REQUESTS,
                pride.bank.erp.access.ScopeAction.VIEW)) {
            case ALL -> Collections.emptyList();   // no restriction
            case BRANCH -> {
                Long branchId = currentUserBranchId();
                // Scoped to a branch but with none on record: fail closed to the reporting line
                // rather than open, since an empty list is the widest possible answer.
                yield branchId == null
                        ? getManagerAndSubordinateUserIds()
                        : userRepository.findUserIdsByBranchId(branchId);
            }
            case SELF -> getManagerAndSubordinateUserIds();
        };
    }

    private Long currentUserBranchId() {
        User currentUser = currentUserService.getCurrentUser();
        return (currentUser != null && currentUser.getBranch() != null)
                ? currentUser.getBranch().getId() : null;
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
        /*
         * One query for the page, not one per row.
         *
         * The row menu needs to know what action is due, and that is a property of the workflow step
         * rather than of the status. Resolving it through each request's instance and workflow
         * individually is the shape that turns a listing into N+1.
         */
        Map<Long, String> stepTypes = currentStepTypes(requests);

        return requests.stream().map(request -> RequestDTO.builder()
                .currentStepType(stepTypes.get(request.getId()))
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
                .currentUnit(request.getCurrentUnit())
                .forStoreRestock(request.isForStoreRestock())
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
                // Needed by the detail page to know a group step is its viewer's to take.
                .currentUnit(request.getCurrentUnit())
                .forStoreRestock(request.isForStoreRestock())
                // And what kind of action is due, so the page offers that one and no other.
                .currentStepType(currentStepTypes(List.of(request)).get(request.getId()))
                .assetTypeId(request.getAssetType() != null ? request.getAssetType().getId() : null)
                .assetType(AssetTypeRefDTO.from(request.getAssetType()))
                .attributes(request.getAttributes())
                .build();
    }
}
