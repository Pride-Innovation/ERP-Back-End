/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.region;

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
@Tag(name = "Regions", description = "Manage bank regions – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class RegionController {

    private final RegionService regionService;

    @Operation(
            summary = "List regions",
            description = "Returns a paginated list of regions. The optional name filter uses a case-insensitive " +
                    "partial match. When no filters are provided all regions are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Regions retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/regions")
    public ResponseEntity<Page<Region>> fetchAllRegions(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute RegionQueryParams params) {
        Page<Region> response = regionService.fetchAllRegions(params);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a region",
            description = "Creates a new region. Name is required and must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Region created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Region.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate region name",
                    content = @Content)
    })
    @PostMapping("/regions")
    public ResponseEntity<Region> createRegion(
            @Valid @RequestBody RegionRequestDTO request) {
        Region response = regionService.createRegion(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a region",
            description = "Fully replaces the region identified by {id}. Name is required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Region updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Region.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate region name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Region not found",
                    content = @Content)
    })
    @PutMapping("/regions/{id}")
    public ResponseEntity<Region> updateRegion(
            @Valid @RequestBody RegionRequestDTO request,
            @Parameter(description = "ID of the region to update", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        Region response = regionService.updateRegion(request, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Get a region by ID",
            description = "Returns the details of a single region."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Region found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Region.class))),
            @ApiResponse(responseCode = "404", description = "Region not found",
                    content = @Content)
    })
    @GetMapping("/regions/{id}")
    public ResponseEntity<Region> getSingleRegion(
            @Parameter(description = "ID of the region", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(regionService.findRegion(id), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a region",
            description = "Permanently deletes the region identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Region deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Region not found",
                    content = @Content)
    })
    @DeleteMapping("/regions/{id}")
    public ResponseEntity<DeleteResponse> deleteRegion(
            @Parameter(description = "ID of the region to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        DeleteResponse response = regionService.deleteRegion(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
