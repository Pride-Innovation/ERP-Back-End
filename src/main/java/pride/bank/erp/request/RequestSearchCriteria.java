/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class RequestSearchCriteria {
    private String name;
    private String priority;
    private String description;
    private LocalDateTime timeOfSubmissionOfRequest;
    private List<Long> statusIds;
    private List<Long> requesterIds;
    private String requestedBy;
    private String approver;
    private Boolean deleted;
}
