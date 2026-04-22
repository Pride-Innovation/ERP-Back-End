/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

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
@Tag(name = "Branches", description = "Manage bank branches – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {
    private final BranchService branchService;

    @Operation(
            summary = "List branches",
            description = "Returns a paginated list of branches. All filter parameters are optional and can be combined. " +
                    "When no filters are provided all branches are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branches retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/branches")
    public ResponseEntity<Page<BranchWithManagersDTO>> fetchAllBranches(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute BranchQueryParams params
    ) {
        Page<BranchWithManagersDTO> response = branchService.fetchAllBranches(params);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a branch",
            description = "Creates a new branch. The name, email, telephone, region and district are required. " +
                    "Manager IDs are optional and can be assigned later via the update endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Branch created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Branch.class))),
            @ApiResponse(responseCode = "400", description = "Validation error – check the request body",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Referenced user, region or district not found",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Branch name already exists",
                    content = @Content)
    })
    @PostMapping("/branches")
    public ResponseEntity<Branch> createBranch(
            @Valid @RequestBody BranchDTO branch
    ) throws NotFoundException {
        Branch response = branchService.createBranch(branch);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a branch",
            description = "Fully replaces the branch identified by {id}. " +
                    "All required fields (name, email, telephone, region, district) must be supplied."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Branch.class))),
            @ApiResponse(responseCode = "400", description = "Validation error – check the request body",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Branch, user, region or district not found",
                    content = @Content)
    })
    @PutMapping("/branches/{id}")
    public ResponseEntity<Branch> updateBranch(
            @Valid @RequestBody BranchDTO branch,
            @Parameter(description = "ID of the branch to update", required = true)
            @PathVariable Long id
    ) throws NotFoundException {
        Branch response = branchService.updateBranch(branch, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Get a branch by ID",
            description = "Returns the full details of a single branch including all assigned managers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Branch.class))),
            @ApiResponse(responseCode = "404", description = "Branch not found",
                    content = @Content)
    })
    @GetMapping("/branches/{id}")
    public ResponseEntity<Branch> getSingleBranch(
            @Parameter(description = "ID of the branch", required = true)
            @PathVariable Long id
    ) throws NotFoundException {
        return new ResponseEntity<>(branchService.findBranch(id), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a branch",
            description = "Permanently deletes the branch identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Branch not found",
                    content = @Content)
    })
    @DeleteMapping("/branches/{id}")
    public ResponseEntity<DeleteResponse> deleteBranch(
            @Parameter(description = "ID of the branch to delete", required = true)
            @PathVariable Long id
    ) throws NotFoundException {
        DeleteResponse response = branchService.deleteBranch(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
