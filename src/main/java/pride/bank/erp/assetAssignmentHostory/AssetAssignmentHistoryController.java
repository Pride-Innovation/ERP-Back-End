/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetAssignmentHistoryController {
    private final AssetAssignmentHistoryService assetAssignmentHistoryService;

    /**
     * Who has held this asset, and when.
     *
     * <p>Answers to {@code READ_ASSIGNMENT_HISTORY}, carved out of {@code READ_ASSET}: seeing what
     * an asset is and seeing everyone who has ever held it are separate disclosures, and the second
     * says where a named member of staff was working and what they were issued.
     *
     * <p>The branch scope applies on top, inside the service — this permission grants the trail of
     * assets already within reach, never more. It was previously open to any signed-in user at all:
     * the route fell through every matcher to {@code .anyRequest().authenticated()}, and
     * {@code INVENTORY_ROUTE} does not cover it despite the name suggesting otherwise.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_ASSIGNMENT_HISTORY + "')")
    @GetMapping("/assignment-history")
    public ResponseEntity<Page<AssetAssignmentHistoryDAO>> findAllAssetAssignmentHistory(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) Long assetId
    ) {

        Page<AssetAssignmentHistoryDAO> response = assetAssignmentHistoryService
                .findAllAssetAssignmentHistory(
                        pageSize,
                        sortDirection,
                        pageNumber,
                        sortBy,
                        assetId
                );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
