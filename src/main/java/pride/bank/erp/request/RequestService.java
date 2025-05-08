/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.notification.Email;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final FileUpload fileUpload;
    private final RequestSearchDao requestSearchDao;
    private final StatusService statusService;
    private final UserService userService;
    private final CommodityService commodityService;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final Email emailService;

    @Transactional
    public Request createRequest(
            Request body,
            MultipartFile file,
            Long statusId,
            Long requesterId,
            List<RequestCommodityDTO> requestCommoditiesRequest
    ) throws IOException, NotFoundException {

        String filePath = fileUpload.uploadFileToLocalDirectory(file);
        Status status = statusService.findOneStatus(statusId);
        User requester = userService.findUser(requesterId);

        Request request = Request.builder()
                .name(body.getName())
                .priority(body.getPriority())
                .timeOfSubmissionOfRequest(body.getTimeOfSubmissionOfRequest())
                .signaturePath(filePath)
                .status(status)
                .requester(requester)
                .description(body.getDescription())
                .build();

        Request savedRequest = requestRepository.save(request);
        User supervisor = findSupervisorToApproveRequest(requester.getId());

        savedRequest.setCurrentApprover(supervisor);
        savedRequest = requestRepository.save(savedRequest);

        addCommoditiesToRequest(savedRequest, requestCommoditiesRequest);
        List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(savedRequest.getId());
        List<CommodityWithQuantityDTO> commodities = commodityWithQuantityDTO(requestCommodities);

        savedRequest.setCommodities(commodities);
        String emailStatus = notifySupervisor(supervisor, savedRequest);
        savedRequest.setEmailMessage(emailStatus);

        return savedRequest;
    }

    @Transactional
    public Request updateRequest(
            Long requestId,
            Request body,
            MultipartFile file,
            Long statusId,
            List<RequestCommodityDTO> updatedRequestCommodities
    ) throws IOException, NotFoundException {
        Request existingRequest = findRequest(requestId);
        Status status = statusService.findOneStatus(statusId);

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

    public Page<Request> fetchAllRequests(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name,
            String priority,
            String description,
            LocalDateTime timeOfSubmissionOfRequest
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
                    .timeOfSubmissionOfRequest(timeOfSubmissionOfRequest)
                    .build();
            return requestSearchDao.findAllWithFilters(requestPage, requestSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    public Request findRequest(Long requestId) throws NotFoundException {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with ID " + requestId + " not found"));
    }

    @Transactional
    private void addCommoditiesToRequest(Request request, List<RequestCommodityDTO> requestCommodities)
            throws NotFoundException {
        for (RequestCommodityDTO dto : requestCommodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            RequestCommodity requestCommodity = RequestCommodity.builder()
                    .request(request)
                    .commodity(commodity)
                    .quantity(dto.getQuantity())
                    .build();
            requestCommodityRepository.save(requestCommodity);
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
    private User findSupervisorToApproveRequest(Long requesterId)
            throws NotFoundException {
        User requester = userService.findUser(requesterId);
        List<User> supervisors = userRepository.findByTitleId(requester.getTitle().getReportsTo().getId());
        return supervisors.get(0);
    }

    private String notifySupervisor(User supervisor, Request request) {
        boolean emailStatus = emailService.sendEmailVerification(
                supervisor.getEmail(),
                String.format("Stores Request ID: %s", request.getId()),
                null,
                supervisor.getLastName(),
                "View Request",
                null,
                "requests",
                request
        );

        if (!emailStatus) {
            return String.format("Request approval notification not sent to %s", supervisor.getEmail());
        }
        return String.format("Request approval notification has been sent to %s", supervisor.getEmail());
    }


}
