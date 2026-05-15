/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Commodities", description = "Manage commodities – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class CommodityController {

    private final CommodityService commodityService;

    @Operation(
            summary = "List commodities",
            description = "Returns a paginated list of commodities. All filter parameters are optional and can be " +
                    "combined. When no filters are provided all commodities are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commodities retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/commodities")
    public ResponseEntity<Page<Commodity>> listCommodities(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute CommodityQueryParams params) {
        Page<Commodity> response = commodityService.fetchAllCommodities(params);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a commodity",
            description = "Creates a new commodity. Name, group name and asset type are required. " +
                    "The combination of name, group name and asset type must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Commodity created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Commodity.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate commodity",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Referenced asset type not found",
                    content = @Content)
    })
    @PostMapping("/commodities")
    public ResponseEntity<Commodity> createCommodity(
            @Valid @RequestBody CommodityDTO commodity)
            throws NotFoundException {
        Commodity response = commodityService.createCommodity(commodity);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a commodity",
            description = "Fully replaces the commodity identified by {id}. Name, group name and asset type are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commodity updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Commodity.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate commodity",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Commodity or referenced asset type not found",
                    content = @Content)
    })
    @PutMapping("/commodities/{id}")
    public ResponseEntity<Commodity> updateCommodity(
            @Valid @RequestBody CommodityDTO commodity,
            @Parameter(description = "ID of the commodity to update", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        Commodity response = commodityService.updateCommodity(commodity, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Get a commodity by ID",
            description = "Returns the full details of a single commodity including its asset type and linked suppliers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commodity found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Commodity.class))),
            @ApiResponse(responseCode = "404", description = "Commodity not found",
                    content = @Content)
    })
    @GetMapping("/commodities/{id}")
    public ResponseEntity<Commodity> getSingleCommodity(
            @Parameter(description = "ID of the commodity", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        Commodity commodity = commodityService.findCommodity(id);
        return new ResponseEntity<>(commodity, HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a commodity",
            description = "Permanently deletes the commodity identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commodity deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Commodity not found",
                    content = @Content)
    })
    @DeleteMapping("/commodities/{id}")
    public ResponseEntity<DeleteResponse> deleteCommodity(
            @Parameter(description = "ID of the commodity to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        DeleteResponse response = commodityService.deleteCommodity(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
