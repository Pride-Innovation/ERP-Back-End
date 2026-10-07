/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.assetType.AssetTypeDTO;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Stocking rules", description = "Which branches may stock which categories and commodities")
@SecurityRequirement(name = "bearerAuth")
public class StockingRuleController {

    private final StockingRuleService stockingRuleService;

    /**
     * Asked by the inventory form, so it answers to the permissions that open that form - not to
     * READ_SETTING, which would send every branch storekeeper to Settings to fill in a delivery.
     *
     * <p>{@code branchId} is for the correction page: lines added to an order are checked against the
     * order's branch, which is not the corrector's when Head Office corrects a branch's order. It
     * discloses only configuration - which categories a branch may stock - so it is not scoped.
     */
    @Operation(summary = "What a branch may stock - the caller's own unless one is named")
    @GetMapping("/stocking/stockable")
    @PreAuthorize("hasAnyAuthority('" + Constants.CREATE_INVENTORY + "','" + Constants.UPDATE_INVENTORY + "')")
    public ResponseEntity<StockableDTO> stockable(@RequestParam(required = false) Long branchId)
            throws NotFoundException {
        return ResponseEntity.ok(stockingRuleService.stockableFor(branchId));
    }

    @Operation(summary = "Replace where a category may be stocked")
    @PutMapping("/asset-types/{id}/stocking-rule")
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    public ResponseEntity<AssetTypeDTO> updateCategoryRule(@PathVariable Long id,
                                                           @RequestBody StockingRuleDTO request)
            throws NotFoundException {
        return ResponseEntity.ok(stockingRuleService.updateCategoryRule(id, request));
    }

    @Operation(summary = "Replace where a commodity may be stocked, or hand it back to its category")
    @PutMapping("/commodities/{id}/stocking-rule")
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    public ResponseEntity<Commodity> updateCommodityRule(@PathVariable Long id,
                                                         @RequestBody StockingRuleDTO request)
            throws NotFoundException {
        return ResponseEntity.ok(stockingRuleService.updateCommodityRule(id, request));
    }
}
