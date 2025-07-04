package pride.bank.erp.issuanceApprovalRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

@Service
@RequiredArgsConstructor
public class IssuanceApprovalRecordService {
    private final IssuanceApprovalRecordRepository issuanceApprovalRecordRepository;
    private final IssuanceService issuanceService;
    private final StatusService statusService;
    private final IssuanceRepository issuanceRepository;


    /**
     * Creates an approval record for an issuance and updates the issuance status to "Approved".
     *
     * @param request DTO containing the issuance ID and optional approval comment
     * @return the saved IssuanceApprovalRecord
     * @throws NotFoundException if the referenced issuance or status is not found
     */
    @Transactional
    public IssuanceApprovalRecord createIssuanceApprovalRecord(IssuanceApprovalRecordDTO request)
            throws NotFoundException {

        Issuance issuance = issuanceService.findOne(request.getIssuanceId());
        Status approvedStatus = statusService.findOneStatus(4L); // 4L = Issuance Approved

        issuance.setStatus(approvedStatus);
        issuanceRepository.save(issuance); // Persist updated issuance status

        IssuanceApprovalRecord record = IssuanceApprovalRecord.builder()
                .issuance(issuance)
                .comment(request.getComment())
                .build();

        return issuanceApprovalRecordRepository.save(record);
    }
}
