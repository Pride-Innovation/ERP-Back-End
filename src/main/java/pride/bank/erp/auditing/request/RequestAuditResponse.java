/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

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
