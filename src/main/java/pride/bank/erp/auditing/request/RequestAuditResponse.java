package pride.bank.erp.auditing.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RequestAuditResponse {
    private String name;
    private String priority;
    private String description;
    private String signaturePath;
    private LocalDateTime timeOfSubmissionOfRequest;
    private int revisionNumber;
    private String revisionType; // ADD, MOD, DEL
    private LocalDateTime createDate;
    private String createdBy;
}
