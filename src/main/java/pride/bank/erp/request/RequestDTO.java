/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.assetType.AssetTypeRefDTO;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestReport.RequestReportDAO;
import pride.bank.erp.status.Status;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequestDTO {
    private Long id;
    private String name;
    private String priority;
    private List<CommodityWithQuantityDTO> commodities;
    private List<RequestReportDAO> requestReports;
    private String description;
    private String signaturePath;
    private Status status;
    private LocalDateTime timeOfSubmissionOfRequest;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;
    private User requester;
    private Set<RequestCommodity> requestCommodities;
    private User currentApprover;
    /**
     * The unit a group step is routed to, when it is routed to a unit rather than a person.
     *
     * <p>Absent from this DTO until now, which is why the front end could not offer an Approve button
     * for a unit-routed step: {@code WorkflowEngineService} admits a member of this unit, and the
     * listing's inbox already matches on it, so such a request appeared in someone's "awaiting me"
     * list with no way to action it. The page cannot mirror a rule it cannot see.
     */
    private pride.bank.erp.unit.Unit currentUnit;

    /**
     * The kind of action the workflow is waiting for — {@code REQUEST_APPROVAL},
     * {@code ACKNOWLEDGE_REQUEST}, {@code ISSUANCE}, {@code ACKNOWLEDGE_RECEIPT}, or null.
     *
     * <p>Carried so a row menu can offer the action that is actually due. Built from status instead,
     * it offers Approve on a request that has moved to issuance — and the engine applies whatever
     * action it is handed to whatever step is current, so that approval would land on the issuance
     * step. Status is a consequence of the last action; the step is what is due next.
     *
     * <p>Null for a finished or rejected request, and for one predating the workflow engine. No
     * action is offered in either case, which is the safe reading: without a workflow there is no way
     * to know the stage.
     */
    private String currentStepType;
    private Boolean isBranchStationery;

    /** Primary asset category id (null on legacy requests). */
    private Long assetTypeId;

    /** Compact view of the chosen category for UI rendering. */
    private AssetTypeRefDTO assetType;

    /** Submitted values for the chosen category's custom attributes. */
    private Map<String, Object> attributes;
}
