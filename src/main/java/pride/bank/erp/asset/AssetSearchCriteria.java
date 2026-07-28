/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetSearchCriteria {
    private String assetName;
    private Long assetTypeId;
    private Long assetStatusId;
    private Long commodityId;
    private String engravedNumber;
    private Long assignedToId;
    private Boolean disposed;
    private String model;
    private String assignedTo;
    private String location;
    /**
     * Hard branch restriction applied on top of every other filter. Set by the service from the
     * caller's permissions, never from the request — a client cannot widen its own scope.
     */
    private Long scopedBranchId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
