/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

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
import pride.bank.erp.user.TitleOccupancyService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Titles", description = "Manage bank Titles – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class TitleController {
    private final TitleService titleService;

    @Operation(
            summary = "List titles",
            description = "Returns a paginated list of titles. All filter parameters are optional and can be combined. " +
                    "When no filters are provided all titles are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Titles retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/titles")
    public ResponseEntity<Page<Title>> fetchAllTitles(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute TitleQueryParams params
    ) {
        Page<Title> response = titleService.fetchAllTitles(params);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a title",
            description = "Creates a new title. Name, short code and role are required. " +
                    "Reports-to is optional for top-level titles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Title created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Title.class))),
            @ApiResponse(responseCode = "400", description = "Validation error – check the request body",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Referenced reports to Title, role not found",
                    content = @Content)
    })
    @PostMapping("/titles")
    public ResponseEntity<Title> createTitle(
            @Valid @RequestBody TitleRequestDTO titleRequestDTO
    )
            throws NotFoundException {
        Title response = titleService.createTitle(titleRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a title",
            description = "Fully replaces the title identified by {id}. Name, short code and role are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Title.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid reporting hierarchy",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Title, reports to title or role not found",
                    content = @Content)
    })
    @PutMapping("/titles/{id}")
    public ResponseEntity<Title> updateTitle(
            @Valid @RequestBody TitleRequestDTO titleRequestDTO,
            @Parameter(description = "ID of the title to update", required = true)
            @PathVariable Long id
    )
            throws NotFoundException {
        Title response = titleService.updateTitle(titleRequestDTO, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Single-holder titles held twice",
            description = "Every location where a title marked single-holder has more than one active "
                    + "holder (not blocked; enabled or on leave). Empty when the rule holds everywhere."
    )
    @GetMapping("/titles/occupancy-conflicts")
    @PreAuthorize("hasAuthority('READ_SETTING')")
    public ResponseEntity<List<TitleOccupancyService.Conflict>> occupancyConflicts() {
        return ResponseEntity.ok(titleService.occupancyConflicts());
    }

    @Operation(
            summary = "Get a title by ID",
            description = "Returns the full details of a single title."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Title.class))),
            @ApiResponse(responseCode = "404", description = "Title not found",
                    content = @Content)
    })
    @GetMapping("/titles/{id}")
    public ResponseEntity<Title> getSingleTitle(
            @Parameter(description = "ID of the title", required = true)
            @PathVariable Long id
    ) throws NotFoundException {
        return new ResponseEntity<>(titleService.findTitle(id), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a title",
            description = "Permanently deletes the title identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Title not found",
                    content = @Content)
    })
    @DeleteMapping("/titles/{id}")
    public ResponseEntity<DeleteResponse> deleteTitle(
            @Parameter(description = "ID of the title to delete", required = true)
            @PathVariable Long id
    ) throws NotFoundException {
        DeleteResponse response = titleService.deleteTitle(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
