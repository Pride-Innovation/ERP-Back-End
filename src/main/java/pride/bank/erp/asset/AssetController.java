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
            // The holder's department. Declared here as well as on the criteria because Spring drops
            // an undeclared parameter silently, which is how a filter comes to look functional while
            // returning the unfiltered list.
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,

            @RequestParam(required = false, defaultValue = "false") Boolean dueForDisposal,

            /*
             * A branch picked from the directory, matched by id on the asset's branch or its
             * holder's. `location` is a partial name match meant for a typed search box - fed a
             * picked branch it also caught every branch whose name contained this one's.
             */
            @RequestParam(required = false) Long branchId,

            /*
             * When the asset was written off. `startDate`/`endDate` match `createDate`, so the
             * disposal report's "this month" listed disposed assets *created* this month.
             */
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime disposedFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime disposedTo,

            /*
             * The soft-deleted register, for restoring something removed by mistake.
             *
             * A separate view rather than a widening of this one: `true` returns deleted assets and
             * nothing else. Mixing them into the ordinary listing would put records back in front of
             * everyone that the delete existed to take away.
             */
            @RequestParam(required = false, defaultValue = "false") Boolean includeDeleted,

            /*
             * Also list this branch's assets that are away at Head Office (in maintenance or pending
             * disposal, sent by this branch). Read-only and asked for by the reports page alone: the
             * assets page's row menu offers Edit and Reassign, which an away asset refuses.
             */
            @RequestParam(required = false, defaultValue = "false") Boolean includeAwayFromBranch,

            /*
             * The Disposal report: scope and Branch filter by whose write-off it was - the branch on
             * the newest disposal transfer - rather than where the asset stands, which for every
             * disposed asset is Head Office. Honoured only with disposed=true; read-only.
             */
            @RequestParam(required = false, defaultValue = "false") Boolean byDisposingBranch
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
                departmentId,
                startDate,
                endDate,
                dueForDisposal,
                includeDeleted,
                branchId,
                disposedFrom,
                disposedTo,
                includeAwayFromBranch,
                byDisposingBranch
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

    /**
     * The navigation bar's search, by the number engraved on an asset.
     *
     * <h2>One object, because an engraved number identifies one asset</h2>
     * This briefly returned a list. That was defensible while three engraved numbers were carried by
     * two assets each — the {@code Optional} finder threw on a second row, a 500 for six real
     * assets — but V25 de-duplicated the estate and put a unique index on the column, so the array
     * was carrying a case the database now prevents and making every caller branch on a length that
     * can only be 0 or 1.
     *
     * <p>The service still reads a list internally, so a lost index degrades into <em>answering</em>
     * rather than throwing; this takes the first match. The guarantee is the constraint, and the list
     * is the seatbelt behind it.
     *
     * <h2>204 when there is nothing to show, not 404 and not an error</h2>
     * A search that finds nothing has <b>succeeded</b>. It is not a fault, and dressing it as one
     * makes every caller handle an exception for the ordinary case — which on this front end means
     * the axios interceptor toasting "Request failed with status code 404" over the top of whatever
     * the search box wanted to say.
     *
     * <p>404 was the first attempt and was worse than it looked: this application's
     * {@code NotFoundException} handler carries <b>no {@code @ResponseStatus}</b> and returns a bare
     * {@code {"message": …}} map — so it answers <b>200</b>, and the browser would have read that map
     * as a match with no id. A 204 cannot be mistaken for anything.
     *
     * <p>"No such tag" and "that tag belongs to an asset outside your branch" deliberately produce
     * the <b>same</b> 204 — the service omits out-of-reach matches rather than refusing them —
     * because distinguishing them would let a search confirm that a number exists somewhere the
     * caller may not look.
     *
     * <p>The path keeps its {@code /tag-name} spelling because the front end uses it. The field being
     * searched is {@code engravedNumber}; see V25 for why the two names differ.
     */
    @GetMapping("/assets/tag-name")
    public ResponseEntity<AssetTagMatch> findAssetByTagName(@RequestParam String tagName) {
        return assetService.findAssetsByTagName(tagName).stream()
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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
                request != null ? request.getTemporaryPool() : null,
                request != null ? request.getProceedWithoutApproval() : null,
                request != null ? request.getBypassReason() : null
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
