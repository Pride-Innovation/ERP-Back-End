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
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.exception.ValidationException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.requestReport.RequestReport;
import pride.bank.erp.requestReport.RequestReportDAO;
import pride.bank.erp.role.Role;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
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
    private static final Long HEAD_OFFICE_BRANCH_ID = 1L;
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
            List<RequestCommodityDTO> requestCommoditiesRequest
    ) throws IOException, NotFoundException, ValidationException, BusinessRuleException {
        log.info("Creating new request: {}", body != null ? body.getName() : "null");

        // Basic validation
        validateRequestData(body, requestCommoditiesRequest);

        // Get current user and determine branch
        User requester = currentUserService.getCurrentUser();
        if (requester == null) {
            throw new IllegalStateException("Current user could not be identified.");
        }

        Long requesterBranchId = requester.getBranch() != null ? requester.getBranch().getId() : null;
        boolean isHeadOfficeUser = HEAD_OFFICE_BRANCH_ID.equals(requesterBranchId);

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
        Request savedRequest = saveRequest(request);

        // Find and assign supervisor
        User supervisor = findSupervisorToApproveRequest();
        savedRequest.setCurrentApprover(supervisor);
        savedRequest = saveRequest(savedRequest);

        // Add commodities to request
        addCommoditiesToRequest(savedRequest, requestCommoditiesRequest);

        // Load commodities for response
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        savedRequest.setCommodities(commodities);

        // Notify supervisor
        String emailStatus = notifySupervisor(supervisor, savedRequest);
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
            List<RequestCommodityDTO> updatedRequestCommodities
    ) throws IOException, NotFoundException, ValidationException {
        log.info("Updating request with ID: {}", requestId);

        // Validate input data
        validateRequestData(body, updatedRequestCommodities);

        // Find the existing request
        Request existingRequest = findRequest(requestId);
        Status status = statusService.findOneStatus(1L); // Asset Request Created status

        // Get current user and determine branch
        User requester = currentUserService.getCurrentUser();
        Long requesterBranchId = requester.getBranch() != null ? requester.getBranch().getId() : null;
        boolean isHeadOfficeUser = HEAD_OFFICE_BRANCH_ID.equals(requesterBranchId);

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
            LocalDateTime endDate
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
            Status status = statusService.findOneStatus(1L);
            if (status == null) {
                throw new NotFoundException("Status with ID 1 not found.");
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
    @Transactional
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
    @Transactional(readOnly = true)
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
    private String notifySupervisor(User supervisor, Request request) {
        try {
            ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(supervisor.getEmail());

            boolean emailStatus = emailService.sendEmailVerification(
                    cleanedEmail.getCleanedEmail(),
                    String.format("Stores Request ID: %s", request.getId()),
                    null,
                    supervisor.getLastName(),
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
                log.warn("Failed to send notification to supervisor: {}", supervisor.getEmail());
                return String.format("Request approval notification not sent to %s", supervisor.getEmail());
            }

            log.info("Notification sent to supervisor: {} for request ID: {}",
                    supervisor.getEmail(), request.getId());
            return String.format("Request approval notification has been sent to %s", supervisor.getEmail());

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
        return switch (role.getName()) {
            case "Officer" -> "OFFICER";
            case "Manager" -> "MANAGER";
            case "Admin Officer" -> "ADMIN_OFFICER";
            case "Admin Manager" -> "ADMIN_MANAGER";
            case "Branch Operations Manager" -> "BOM";
            default -> "";
        };
    }

    /**
     * Returns a list of user IDs that includes the current user (manager)
     * and all users whose titles report (directly or indirectly) to the manager's title.
     */
    @Transactional(readOnly = true)
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

        List<Long> result = switch (userRole) {
            case "OFFICER" -> List.of(currentUserService.getCurrentUser().getId());

            case "MANAGER" -> getManagerAndSubordinateUserIds();

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

            case "BOM" -> {
                if (pendingIssuedOrDashboard) {
                    Long currentBranchId = currentUserService.getCurrentUser().getBranch().getId();
                    yield userRepository.findUserIdsByBranchId(currentBranchId);
                } else if ("CREATED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
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
                .build();
    }
}