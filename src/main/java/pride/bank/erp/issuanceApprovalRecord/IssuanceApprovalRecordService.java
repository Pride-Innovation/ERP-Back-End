package pride.bank.erp.issuanceApprovalRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

@Service
@RequiredArgsConstructor
public class IssuanceApprovalRecordService {
    private final IssuanceApprovalRecordRepository issuanceApprovalRecordRepository;
    private final IssuanceService issuanceService;
    private final StatusService statusService;
    private final IssuanceRepository issuanceRepository;
    private final RequestService requestService;
    private final RequestRepository requestRepository;

    /**
     * Creates an approval record for an issuance and updates the issuance status to "Approved".
     *
     * @param issuanceApprovalRecordDTO DTO containing the issuance ID and optional approval comment
     * @return the saved IssuanceApprovalRecord
     * @throws NotFoundException if the referenced issuance or status is not found
     */
    @Transactional
    public Request createIssuanceApprovalRecord(IssuanceApprovalRecordDTO issuanceApprovalRecordDTO)
            throws NotFoundException {

        Issuance issuance = issuanceService.findOne(issuanceApprovalRecordDTO.getIssuanceId());
        Status approvedIssuanceStatus = statusService.findOneStatus(issuanceApprovalRecordDTO.getStatusId()); // 4L = Issuance Approved
        Request request = requestService.findRequest(issuanceApprovalRecordDTO.getRequestId());

        issuance.setStatus(approvedIssuanceStatus);
        request.setStatus(approvedIssuanceStatus);

        issuanceRepository.save(issuance); // Persist updated issuance status

        IssuanceApprovalRecord record = IssuanceApprovalRecord.builder()
                .issuance(issuance)
                .request(request)
                .comment(issuanceApprovalRecordDTO.getComment())
                .build();

        issuanceApprovalRecordRepository.save(record);
        return requestRepository.save(request); // Persist updated request status

    }
}
