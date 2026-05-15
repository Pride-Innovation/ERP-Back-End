/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

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
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Manage suppliers – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(
            summary = "List suppliers",
            description = "Returns a paginated list of suppliers. All filter parameters are optional and can be combined. " +
                    "When no filters are provided all suppliers are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suppliers retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/suppliers")
    public ResponseEntity<Page<Supplier>> fetchAllSuppliers(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute SupplierQueryParams params) {
        Page<Supplier> suppliers = supplierService.fetchAllSuppliers(params);
        return new ResponseEntity<>(suppliers, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a supplier",
            description = "Creates a new supplier. Name, email and telephone are required. " +
                    "Commodity IDs are optional and can be assigned at creation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Supplier created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Supplier.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate supplier name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Referenced commodity not found",
                    content = @Content)
    })
    @PostMapping("/suppliers")
    public ResponseEntity<Supplier> createSupplier(
            @Valid @RequestBody SupplierDTO supplier)
            throws NotFoundException {
        Supplier response = supplierService.createSupplier(supplier);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a supplier",
            description = "Fully replaces the supplier identified by {id}. Name, email and telephone are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Supplier.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate supplier name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Supplier or referenced commodity not found",
                    content = @Content)
    })
    @PutMapping("/suppliers/{id}")
    public ResponseEntity<Supplier> updateSupplier(
            @Parameter(description = "ID of the supplier to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SupplierDTO supplier)
            throws NotFoundException {
        Supplier response = supplierService.updateSupplier(supplier, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Get a supplier by ID",
            description = "Returns the full details of a single supplier including its linked commodities."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Supplier.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content)
    })
    @GetMapping("/suppliers/{id}")
    public ResponseEntity<Supplier> getOneSupplier(
            @Parameter(description = "ID of the supplier", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        Supplier response = supplierService.findSupplier(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a supplier",
            description = "Permanently deletes the supplier identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content)
    })
    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<DeleteResponse> deleteSupplier(
            @Parameter(description = "ID of the supplier to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        DeleteResponse response = supplierService.deleteSupplier(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
