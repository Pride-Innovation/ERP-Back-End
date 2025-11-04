/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestReport;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.acknowledgeIssuanceReceipt.AcknowledgeIssuanceReceiptService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RequestReportService {
    private final RequestReportRepository requestReportRepository;
    private final RequestService requestService;
    private final UserService userService;
    private final StatusService statusService;
    private final RequestRepository requestRepository;
    private final Email emailService;
    private final UserRepository userRepository;
    private final UserBuilder userBuilder;
    private final RequestCommodityRepository requestCommodityRepository;

    @Value("${application.admin.email}")
    private String adminEmailAddress;

    @Transactional
    public RequestDTO approveRequest(RequestReportDTO body)
            throws NotFoundException {
        Request request = requestService.findRequest(body.getRequestId());
        User approver = userService.findUser(body.getApproverId());
        Status status = statusService.findOneStatus(body.getStatusId());

        String message = handleStatusUpdate(request, status, body.getComment());

        RequestReport requestReport = RequestReport.builder()
                .approver(approver)
                .request(request)
                .status(status)
                .comment(body.getComment())
                .build();

        RequestReport response = requestReportRepository.save(requestReport);
        response.setMessage(message);
        return RequestDTO
                .builder()
                .id(request.getId())
                .name(request.getName())
                .priority(request.getPriority())
                .commodities(request.getCommodities())
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
                .createDate(request.getCreateDate())
                .lastModified(request.getLastModified())
                .createdBy(request.getCreatedBy())
                .lastModifiedBy(request.getLastModifiedBy())
                .requester(request.getRequester() != null ?
                        userBuilder.builder(request.getRequester()) : null)
                .currentApprover(request.getCurrentApprover() != null ?
                        userBuilder.builder(request.getCurrentApprover()) : null)
                .build();
    }

    /**
     * Updates the request status and handles notifications.
     * If the request is rejected, only the requester is notified.
     * If the request is approved, both the requester and the admin are notified.
     */
    @Transactional
    private String handleStatusUpdate(
            Request request,
            Status status,
            String comment
    ) throws NotFoundException {
        request.setStatus(status);
        requestRepository.save(request);

        String statusName = status.getStatus();
        User requester = request.getRequester();

        // Track Branch Stationery and Non stationery request
        boolean isBranchStationeryRequest = false;
        boolean isBranchNonStationeryRequest = false;

        if ("requestRejected".equalsIgnoreCase(statusName)) {
            setRequestPosition(request, request.getRequester());
            return notifyIndividual(
                    request.getRequester().getEmail(),
                    comment,
                    request,
                    request.getRequester().getFirstName()
            );
        }

        if ("requestApproved".equalsIgnoreCase(statusName)) {
            notifyIndividual(
                    requester.getEmail(),
                    "Request has been Approved by your Manager",
                    request,
                    requester.getFirstName()
            );

            List<RequestCommodity> requestCommodities = requestCommodityRepository.findByRequestId(request.getId());
            List<RequestCommodityDTO> commodities = requestCommodities.stream().map(this::requestCommodityDtoBuilder).toList();

            isBranchStationeryRequest = requestService.containsStationeryItems(commodities);
            System.out.printf("Branch Request::: %s", isBranchStationeryRequest);
            if (!requestService.containsStationeryItems(commodities)) {
                System.out.println("Should not be here");
                isBranchNonStationeryRequest = true;
            }

            if (isHeadOffice(requester)) {
                System.out.println("Head Office Request");
                User admin = userRepository.findByEmail(adminEmailAddress)
                        .orElseThrow(() -> new IllegalStateException("Admin user not found for email: " + adminEmailAddress));

                setRequestPosition(request, admin);
                return notifyIndividual(
                        admin.getEmail(),
                        comment,
                        request,
                        "Admin"
                );
            } else if (!isHeadOffice(requester) && isBranchNonStationeryRequest) {
                System.out.println("Branch Asset Request");
                User admin = userRepository.findByEmail(adminEmailAddress)
                        .orElseThrow(() -> new IllegalStateException("Admin user not found for email: " + adminEmailAddress));

                setRequestPosition(request, admin);
                return notifyIndividual(
                        admin.getEmail(),
                        comment,
                        request,
                        "Admin"
                );
            } else if (!isHeadOffice(requester) && isBranchStationeryRequest) {
                // Notify Branch Operations Manager
                User branchOpsManager = requester.getBranch().getBranchOperationsManager();

                if (branchOpsManager == null) {
                    throw new NotFoundException("Branch Operations Manager not assigned for branch: "
                            + requester.getBranch().getName());
                }

                setRequestPosition(request, branchOpsManager);
                return notifyIndividual(
                        branchOpsManager.getEmail(),
                        comment,
                        request,
                        branchOpsManager.getFirstName()
                );
            }
        }

        return null;
    }


    private RequestCommodityDTO requestCommodityDtoBuilder(RequestCommodity requestCommodity) {

        return RequestCommodityDTO
                .builder()
                .quantity(requestCommodity.getQuantity())
                .commodityId(requestCommodity.getCommodity().getId())
                .build();
    }

    private String notifyIndividual(
            String email,
            String comment,
            Request request,
            String requesterName
    ) {
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(email);

        boolean emailStatus = emailService.sendEmailVerification(
                cleanedEmail.getCleanedEmail(),
                String.format("Action Required – Approved Request Pending Processing (Request ID# %s )", request.getId()),
                null,
                requesterName,
                "View Request",
                comment,
                "requests",
                request,
                null,
                null,
                null,
                null
        );

        if (!emailStatus) {
            return String.format("Request approval notification not sent to %s", email);
        }
        return String.format("Request approval notification has been sent to %s", email);
    }

    private void setRequestPosition(Request request, User newPosition) {
        request.setCurrentApprover(newPosition);
        requestRepository.save(request);
    }

    private boolean isHeadOffice(User user) {
        return user.getBranch() != null && user.getBranch().getId() == 1;
    }

    @Transactional
    public RequestReportDAO findRequestReportByRequestId(Long requestId) {
        List<RequestReport> details = requestReportRepository.findRequestReportsByRequestId(requestId);

        if (details == null || details.isEmpty()) {
            return null;
        }

        // If the request is approved after several rejections, the approved version will the last one
        RequestReport data = details.size() == 1
                ? details.get(0)
                : details.get(details.size() - 1);

        return RequestReportDAO
                .builder()
                .id(data.getId())
                .status(data.getStatus() != null ?
                        Status.builder()
                                .id(data.getStatus().getId())
                                .status(data.getStatus().getStatus())
                                .description(data.getStatus().getDescription())
                                .name(data.getStatus().getName())
                                .code(data.getStatus().getCode())
                                .build() : null)
                .request(data.getRequest() != null ?
                        Request.builder()
                                .id(data.getRequest().getId())
                                .name(data.getRequest().getName())
                                .priority(data.getRequest().getPriority())
                                .description(data.getRequest().getDescription())
                                .build() : null)
                .approver(data.getApprover() != null ?
                        userBuilder.builder(data.getApprover()) : null)
                .createDate(data.getCreateDate())
                .createdBy(data.getCreatedBy())
                .comment(data.getComment())
                .lastModified(data.getLastModified())
                .lastModifiedBy(data.getLastModifiedBy())
                .build();
    }
}