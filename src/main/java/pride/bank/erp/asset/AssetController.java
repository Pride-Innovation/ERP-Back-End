/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @GetMapping("/assets")
    public ResponseEntity<Page<AssetDAO>> fetchAllAssets(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String assetName,
            /*
             * Optional, like every other filter here. It was the one required parameter on the
             * endpoint, which made a perfectly valid cross-category search — "find this engraved
             * number, whatever it is" — impossible: the repair transfer, return and disposal flows
             * all search the whole register because the category is what they are trying to
             * discover. AssetSearchDao has always skipped the predicate when it is null, so the
             * annotation was simply stricter than the query behind it.
             */
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) Long assetStatusId,
            @RequestParam(required = false) Long commodityId,
            @RequestParam(required = false) String engravedNumber,
            @RequestParam(required = false) Long assignedToId,
            @RequestParam(required = false, defaultValue = "false") Boolean disposed,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String assignedTo, // Name of the assigned to user, can first name, last name or other name
            @RequestParam(required = false) String location,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,

            @RequestParam(required = false, defaultValue = "false") Boolean dueForDisposal,

            /*
             * The soft-deleted register, for restoring something removed by mistake.
             *
             * A separate view rather than a widening of this one: `true` returns deleted assets and
             * nothing else. Mixing them into the ordinary listing would put records back in front of
             * everyone that the delete existed to take away.
             */
            @RequestParam(required = false, defaultValue = "false") Boolean includeDeleted
    ) {
        Page<AssetDAO> response = assetService.fetchAllAssets(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                assetName,
                assetTypeId,
                assetStatusId,
                commodityId,
                engravedNumber,
                assignedToId,
                disposed,
                model,
                assignedTo,
                location,
                startDate,
                endDate,
                dueForDisposal,
                includeDeleted
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/assets")
    public ResponseEntity<Asset> createAsset(@RequestBody AssetDTO assetDTO)
            throws NotFoundException {
        Asset asset = assetService.createAsset(assetDTO);
        return new ResponseEntity<>(asset, HttpStatus.CREATED);
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<AssetDAO> getSingleAsset(@PathVariable Long id)
            throws NotFoundException {
        AssetDAO asset = assetService.fetchSingleAsset(id);
        return new ResponseEntity<>(asset, HttpStatus.OK);
    }

    /**
     * Soft-deletes an asset — hidden from the register, kept in the database.
     *
     * <p>{@code DELETE_ASSET} admits you to the endpoint; the branch guard in the service decides
     * whether this particular asset is yours to remove.
     *
     * <p>Distinct from disposal, which lives under {@code /movements} and is a business event — an
     * asset reaching the end of its life, written off through a movement and still counted in the
     * register. This one says the record should not have existed.
     */
    /**
     * How many deleted assets of this category the caller could restore.
     *
     * <p>The UI asks before deciding whether to offer the deleted view at all — a control that opens
     * an empty page is worse than no control. Guarded by {@code DELETE_ASSET} because that is the
     * same audience: whoever can take a record out is who needs to put it back.
     */
    @PreAuthorize("hasAuthority('" + Constants.DELETE_ASSET + "')")
    @GetMapping("/assets/deleted-count")
    public ResponseEntity<Long> countDeletedAssets(
            @RequestParam(required = false) Long assetTypeId
    ) {
        return new ResponseEntity<>(assetService.countDeletedAssets(assetTypeId), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.DELETE_ASSET + "')")
    @DeleteMapping("/assets/{assetID}")
    public ResponseEntity<AssetDAO> deleteAsset(
            @PathVariable Long assetID,
            @RequestParam(required = false) String reason
    ) throws NotFoundException {
        return new ResponseEntity<>(assetService.deleteAsset(assetID, reason), HttpStatus.OK);
    }

    /** Puts a soft-deleted asset back into the register. */
    @PreAuthorize("hasAuthority('" + Constants.DELETE_ASSET + "')")
    @PutMapping("/assets/{assetID}/restore")
    public ResponseEntity<AssetDAO> restoreAsset(@PathVariable Long assetID) throws NotFoundException {
        return new ResponseEntity<>(assetService.restoreAsset(assetID), HttpStatus.OK);
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<AssetDAO> updateAsset(
            @PathVariable Long id,
            @RequestBody AssetDTO assetDTO
    ) throws NotFoundException {
        AssetDAO response = assetService.updateAsset(assetDTO, id);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/dashboard-asset-report")
    public ResponseEntity<List<AssetTypeSummaryView>> listDashboardAssetReport() {
        return new ResponseEntity<>(assetService.listDashboardAssetReport(), HttpStatus.OK);
    }

    /**
     * Added alongside the route above rather than replacing it: adding the branch dimension
     * multiplies the row count, so an existing caller expecting one row per category would break.
     */
    @GetMapping("/dashboard-asset-report/by-branch")
    public ResponseEntity<List<AssetBranchSummaryView>> listDashboardAssetReportByBranch() {
        return new ResponseEntity<>(assetService.listDashboardAssetReportByBranch(), HttpStatus.OK);
    }

    /**
     * Per-category asset counts for the dashboard.
     *
     * @param branchId the branch the dashboard's selector is focused on. Only meaningful for a
     *                 viewer whose scope is ALL; anyone else naming a branch other than their own is
     *                 refused rather than silently rewritten, because showing one branch's figures
     *                 under another's name is worse than an error.
     */
    @GetMapping("/assets/statistics")
    public List<AssetTypeStatsProjection> getAssetStatistics(
            @RequestParam(required = false) Long branchId) {
        return assetService.getAssetTypeStatistics(branchId);
    }

    @GetMapping("/assets/my-assets")
    public List<AssetGroupDTO> getMyAssets() {
        return assetService.getAssetsGroupedByType();
    }

    @GetMapping("/assets/tag-name")
    public ResponseEntity<Asset> findAssetByTagName(@RequestParam String tagName)
            throws NotFoundException {
        return ResponseEntity.ok(assetService.findAssetByTagName(tagName));
    }

    /*
     * POST /assets/{assetID} — the flag-only dispose — has been removed.
     *
     * It set `disposed = true` and a date. No movement, no store change, no status: an asset could
     * read as disposed while still physically held in a branch store, and nothing recorded its
     * journey to the Head Office Disposal store. Meanwhile POST /movements/disposal did the real
     * work. Two endpoints, the same word, materially different outcomes — and this was the one the
     * asset page called.
     *
     * Disposal is now POST /movements/disposal only (RepairMovementService#disposeAsset), which
     * raises a DISPOSAL_TRANSFER and enforces the store and useful-life rules.
     */

    @PostMapping("/assets/reassign/{assetID}")
    public ResponseEntity<AssetDAO> reassignAsset(
            @PathVariable Long assetID,
            @RequestBody ReassignAsset reassignAsset
    )
            throws NotFoundException {
        AssetDAO response = assetService.reassignAsset(assetID, reassignAsset);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping(value = "assets/image/{assetID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetDAO> uploadAssetImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @PathVariable Long assetID
    ) throws IOException, NotFoundException {
        AssetDAO response = assetService.uploadAssetImage(file, assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("assets/remove/image/{assetID}")
    public ResponseEntity<AssetDAO> removeAssetImage(
            @PathVariable Long assetID
    ) throws NotFoundException {
        AssetDAO response = assetService.removeAssetImage(assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Hands an asset back into a store, optionally marking it as temporary-replacement pool stock.
     *
     * <p>The single action that puts an asset in a store. The former
     * {@code assets/temporary-pool/{id}} endpoint is gone: flipping the pool flag without moving the
     * asset produced pool stock the replacement picker could never see, since it looks assets up by
     * the store holding them. The flag now travels with the move that makes it meaningful.
     */
    @PutMapping("assets/store/{assetID}")
    public ResponseEntity<AssetDAO> sendAssetToStore(
            @PathVariable Long assetID,
            @RequestBody(required = false) SendToStoreDTO request
    ) throws NotFoundException {
        AssetDAO response = assetService.sendAssetToStore(
                assetID,
                request != null ? request.getStoreId() : null,
                request != null ? request.getTemporaryPool() : null
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * The dropdown sources, field configuration and row limits for an import template.
     *
     * <p>One call rather than the five the browser would otherwise make, and — more importantly —
     * the same server that validates an import decides what the template offers, so the two cannot
     * drift into a template whose dropdown values the importer rejects.
     */
    @GetMapping("/assets/import-template/{assetTypeId}")
    public ResponseEntity<AssetImportTemplateDTO> assetImportTemplate(@PathVariable Long assetTypeId)
            throws NotFoundException {
        return new ResponseEntity<>(assetService.buildImportTemplate(assetTypeId), HttpStatus.OK);
    }

    /**
     * Imports one batch of spreadsheet rows.
     *
     * <p>A batch, not a file: the browser splits a spreadsheet into {@code batchSize} chunks and
     * posts them in sequence. A whole file in one request would exceed both the 2MB multipart limit
     * and the client's 60-second timeout somewhere past two thousand rows, and would do it after
     * committing an unknown number of them.
     */
    @PostMapping("assets/bulk-insert")
    public ResponseEntity<Map<String, Object>> bulkInsertAsset(
            @RequestParam("assets") String assets,
            @RequestParam("assetTypeID") Long assetTypeID
    ) throws JsonProcessingException, NotFoundException {

        ObjectMapper objectMapper = new ObjectMapper();
        List<BulkAssetDTO> assetList = objectMapper.readValue(assets,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, BulkAssetDTO.class));

        Map<String, Object> response = assetService.bulkInsertAsset(assetList, assetTypeID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
