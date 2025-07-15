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
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.util.Optional;

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

    @Value("${application.admin.email}")
    private String adminEmailAddress;

    @Transactional
    public RequestReport approveRequest(RequestReportDTO body)
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
        return response;
    }

    /**
     * Updates the request status and handles notifications.
     * If the request is rejected, only the requester is notified.
     * If the request is approved, both the requester and the admin are notified.
     */
    private String handleStatusUpdate(
            Request request,
            Status status,
            String comment
    ) {
        request.setStatus(status);
        requestRepository.save(request);

        String statusName = status.getStatus();

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
            Optional<User> admin = userRepository.findByEmail(adminEmailAddress);
            if (admin.isEmpty()) {
                throw new IllegalStateException("Admin user not found for email: " + adminEmailAddress);
            }

            setRequestPosition(request, admin.get());

            notifyIndividual(
                    request.getRequester().getEmail(),
                    "Request has been Approved by your Manager",
                    request,
                    request.getRequester().getFirstName()
            );

            return notifyIndividual(
                    adminEmailAddress,
                    comment,
                    request,
                    "Admin"
            );
        }

        return null;
    }


    private String notifyIndividual(
            String email,
            String comment,
            Request request,
            String requesterName
    ) {
        boolean emailStatus = emailService.sendEmailVerification(
                email,
                String.format("Stores Request ID: %s", request.getId()),
                null,
                requesterName,
                "View Request",
                comment,
                "requests",
                request,
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

}