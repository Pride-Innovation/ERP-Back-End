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
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.helper.FileUpload;
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

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {

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

    @Transactional
    public Request createRequest(
            Request body,
            MultipartFile file,
            List<RequestCommodityDTO> requestCommoditiesRequest
    ) throws IOException, NotFoundException {

        if (body == null) {
            throw new IllegalArgumentException("Request body cannot be null.");
        }

        if (body.getName() == null || body.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Request name is required.");
        }

        if (requestCommoditiesRequest == null || requestCommoditiesRequest.isEmpty()) {
            throw new IllegalArgumentException("At least one commodity is required.");
        }

        String filePath = null;
        if (file != null && !file.isEmpty()) {
            try {
                filePath = fileUpload.uploadFileToLocalDirectory(file);
            } catch (IOException ex) {
                throw new IOException("Failed to upload file: " + ex.getMessage(), ex);
            }
        }

        Status status;
        try {
            status = statusService.findOneStatus(1L);
            if (status == null) {
                throw new NotFoundException("Status with ID 1 not found.");
            }
        } catch (Exception ex) {
            throw new NotFoundException("Error retrieving status: " + ex.getMessage());
        }

        User requester = currentUserService.getCurrentUser();
        if (requester == null) {
            throw new IllegalStateException("Current user could not be identified.");
        }

        Request request = Request.builder()
                .name(body.getName())
                .priority(body.getPriority())
                .timeOfSubmissionOfRequest(body.getTimeOfSubmissionOfRequest())
                .signaturePath(filePath)
                .status(status)
                .requester(requester)
                .description(body.getDescription())
                .build();

        Request savedRequest;
        try {
            savedRequest = requestRepository.save(request);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to save request: " + ex.getMessage(), ex);
        }

        User supervisor;
        try {
            supervisor = findSupervisorToApproveRequest();
            savedRequest.setCurrentApprover(supervisor);
            savedRequest = requestRepository.save(savedRequest);
        } catch (Exception ex) {
            throw new IllegalStateException("Error assigning supervisor: " + ex.getMessage(), ex);
        }

        try {
            addCommoditiesToRequest(savedRequest, requestCommoditiesRequest);
        } catch (Exception ex) {
            throw new RuntimeException("Error adding commodities to request: " + ex.getMessage(), ex);
        }

        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        savedRequest.setCommodities(commodities);

        try {
            String emailStatus = notifySupervisor(supervisor, savedRequest);
            savedRequest.setEmailMessage(emailStatus);
        } catch (Exception ex) {
            savedRequest.setEmailMessage("Failed to notify supervisor.");
            log.error("Failed to notify supervisor.");
        }

        return savedRequest;
    }


    @Transactional
    public Request updateRequest(
            Long requestId,
            Request body,
            MultipartFile file,
            List<RequestCommodityDTO> updatedRequestCommodities
    ) throws IOException, NotFoundException {
        Request existingRequest = findRequest(requestId);
        Status status = statusService.findOneStatus(1L); // Asset Request Created Identification

        if (file != null && !file.isEmpty()) {
            if (existingRequest.getSignaturePath() != null) {
                File oldFile = new File(existingRequest.getSignaturePath());
                if (oldFile.exists()) {
                    boolean deleted = oldFile.delete();
                    if (!deleted) {
                        System.err.println("Failed to delete old file: " + oldFile.getAbsolutePath());
                    }
                }
            }
            String newFilePath = fileUpload.uploadFileToLocalDirectory(file);
            existingRequest.setSignaturePath(newFilePath);
        }

        existingRequest.setName(body.getName());
        existingRequest.setPriority(body.getPriority());
        existingRequest.setTimeOfSubmissionOfRequest(body.getTimeOfSubmissionOfRequest());
        existingRequest.setStatus(status);
        existingRequest.setDescription(body.getDescription());

        Request savedRequest = requestRepository.save(existingRequest);
        requestCommodityRepository.deleteByRequestId(requestId);

        addCommoditiesToRequest(savedRequest, updatedRequestCommodities);

        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        savedRequest.setCommodities(commodities);
        return savedRequest;
    }

    public DeleteResponse deleteRequest(Long id) {
        if (requestRepository.existsById(id)) {
            requestRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    @Transactional
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
            String status
    ) {
        try {
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
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .build();
            Page<Request> requestPageResult = requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
            List<RequestDTO> dtoList = convertToRequestDTOList(requestPageResult.getContent());
            return new PageImpl<>(dtoList, requestPageResult.getPageable(), requestPageResult.getTotalElements());

        } catch (Exception e) {
            return null;
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    @Transactional
    public Request findRequest(Long requestId) throws NotFoundException {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with ID " + requestId + " not found"));

        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(requestId);
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        request.setCommodities(commodities);
        return request;
    }


    @Transactional
    public RequestDTO fetchSingleRequest(Long requestId) throws NotFoundException {
        Request request = findRequest(requestId);

        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(requestId);
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);
        request.setCommodities(commodities);
        Set<RequestReport> reports = requestCommodityRepository.findReportByRequestId(requestId);
        List<RequestReportDAO> requestReportDAOS = reports.stream().map(this::buildObject).toList();

        return RequestDTO.builder()
                .id(request.getId())
                .name(request.getName())
                .priority(request.getPriority())
                .commodities(commodities)
                .description(request.getDescription())
                .signaturePath(request.getSignaturePath())
                .status(request.getStatus())
                .timeOfSubmissionOfRequest(request.getTimeOfSubmissionOfRequest())
                .createdBy(request.getCreatedBy())
                .createDate(request.getCreateDate())
                .lastModified(request.getLastModified())
                .lastModifiedBy(request.getLastModifiedBy())
                .requester(request.getRequester())
                .requestReports(requestReportDAOS)
                .currentApprover(request.getCurrentApprover())
                .build();
    }

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

    @Transactional
    private void addCommoditiesToRequest(Request request, List<RequestCommodityDTO> requestCommodities)
            throws NotFoundException {
        try {
            for (RequestCommodityDTO dto : requestCommodities) {
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                RequestCommodity requestCommodity = RequestCommodity.builder()
                        .request(request)
                        .commodity(commodity)
                        .quantity(dto.getQuantity())
                        .build();
                requestCommodityRepository.save(requestCommodity);
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to add commodities to request", ex);
        }
    }

    private List<CommodityWithQuantityDTO> commodityWithQuantityDTO(List<RequestCommodity> requestCommodities) {
        List<CommodityWithQuantityDTO> commodityWithQuantities = new ArrayList<>();

        for (RequestCommodity requestCommodity : requestCommodities) {
            Commodity commodity = requestCommodity.getCommodity();
            int quantity = requestCommodity.getQuantity();

            CommodityWithQuantityDTO dto = new CommodityWithQuantityDTO(commodity, quantity);
            commodityWithQuantities.add(dto);
        }

        return commodityWithQuantities;
    }

    @Transactional
    private User findSupervisorToApproveRequest()
            throws NotFoundException {
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
            throw new NotFoundException(
                    "No manager found with title ID: " + managerTitleId + " in branch ID: " + branchId
            );
        }

        return possibleManagers.get(0);
    }

    private String notifySupervisor(User supervisor, Request request) {
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(supervisor.getEmail());

        boolean emailStatus = emailService.sendEmailVerification(
                cleanedEmail.getCleanedEmail(), // This would be supervisor's Email Address
                String.format("Stores Request ID: %s", request.getId()),
                null,
                supervisor.getLastName(),
                "View Request",
                null,
                "requests",
                request,
                null
        );

        if (!emailStatus) {
            return String.format("Request approval notification not sent to %s", supervisor.getEmail());
        }
        return String.format("Request approval notification has been sent to %s", supervisor.getEmail());
    }

    private String determineUserRole() {
        Role role = currentUserService.getCurrentUser().getTitle().getRole();
        return switch (role.getName()) {
            case "Officer" -> "OFFICER";
            case "Manager" -> "MANAGER";
            case "Admin Officer" -> "ADMIN_OFFICER";
            case "Admin Manager" -> "ADMIN_MANAGER";
            default -> "";
        };
    }

    /**
     * Returns a list of user IDs that includes the current user (manager)
     * and all users whose titles report (directly or indirectly) to the manager's title.
     */
    @Transactional
    private List<Long> getManagerAndSubordinateUserIds() {
        User currentUser = currentUserService.getCurrentUser();
        Long managerTitleId = currentUser.getTitle().getId();
        List<Long> subordinateTitleIds = titleRepository.findSubordinateTitleIds(managerTitleId);
        List<Long> userIds = userRepository.findUserIdsByTitleIds(subordinateTitleIds);

        if (!userIds.contains(currentUser.getId())) {
            userIds.add(currentUser.getId());
        }

        return userIds;
    }

    /**
     * Determines the list of requester user IDs based on the current user's role and the provided request status.
     *
     * <ul>
     *   <li><b>OFFICER</b>: Sees only their own requests.</li>
     *   <li><b>MANAGER</b>: Sees their own requests and those of their subordinates.</li>
     *   <li><b>ADMIN_OFFICER</b>: Sees all requests when status is PENDING or ISSUED; otherwise, only their own requests.</li>
     *   <li><b>ADMIN_MANAGER</b>: Sees all requests when status is PENDING or ISSUED; otherwise, their own and their subordinates' requests.</li>
     * </ul>
     * <p>
     * Returning an empty list implies no filtering (i.e., fetch all requests).
     *
     * @param status The status of the request (e.g., CREATED, PENDING, ISSUED, REJECTED).
     * @return A list of user IDs that should be considered requesters based on role and status.
     */
    private List<Long> requestersList(String status) {
        return switch (determineUserRole()) {
            case "OFFICER" -> List.of(currentUserService.getCurrentUser().getId());

            case "MANAGER" -> getManagerAndSubordinateUserIds();

            case "ADMIN_OFFICER" -> {
                if ("PENDING".equalsIgnoreCase(status) || "ISSUED".equalsIgnoreCase(status)) {
                    yield Collections.emptyList();
                } else if ("CREATED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
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

            default -> Collections.emptyList();
        };
    }


    public List<AssetRequestSummaryDTO> getCurrentYearRequestSummary() {
        Long receiptAcknowledged = 7L; // This must be the ID of  Receipt Acknowledged Status
        Long requestRejected = 2L; // This must be the ID of Request Rejected Status

        try {
            return requestRepository.fetchAssetRequestSummaries(receiptAcknowledged, requestRejected).stream()
                    .map(r -> new AssetRequestSummaryDTO(
                            (String) r[0],
                            ((Number) r[1]).longValue(),
                            ((Number) r[2]).longValue()))
                    .toList();
        } catch (Exception ex) {
            log.error("Error fetching asset request summaries: {}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
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
                    .statusIds(statusIds)
                    .requesterIds(requestersList(status))
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .build();

            Page<Request> requestPageResult = requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
            List<RequestDTO> dtoList = convertToRequestDTOList(requestPageResult.getContent());
            return new PageImpl<>(dtoList, requestPageResult.getPageable(), requestPageResult.getTotalElements());

        } catch (Exception e) {
            return Page.empty();
        }
    }

    private List<RequestDTO> convertToRequestDTOList(List<Request> requests) {
        return requests.stream().map(request -> {
            List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(request.getId());

            List<CommodityWithQuantityDTO> commodityDTOs = requestCommodities.stream()
                    .map(rc -> CommodityWithQuantityDTO.builder()
                            .commodity(rc.getCommodity())
                            .quantity(rc.getQuantity())
                            .build())
                    .toList();

            return RequestDTO.builder()
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
                    .commodities(commodityDTOs)
                    .requestReports(request.getRequestReportDAOS())
                    .build();
        }).toList();
    }

}
