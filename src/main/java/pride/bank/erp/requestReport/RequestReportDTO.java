/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestReport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.request.CommodityWithQuantityDTO;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestReportDTO {
    private Long requestId;
    private Long approverId;
    private Long statusId;
    private String comment;
    /** Explicit workflow action: "APPROVED" | "REJECTED". When present takes priority over statusId. */
    private String workflowAction;
}
