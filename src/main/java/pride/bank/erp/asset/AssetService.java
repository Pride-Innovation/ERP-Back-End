/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.unitofmeasure.UnitOfMeasureService;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.movement.MovementDTO;
import pride.bank.erp.movement.MovementItemDTO;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.MovementType;
import pride.bank.erp.notification.Email;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusRepository;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockRepository;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierRepository;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final BranchService branchService;
    private final UserService userService;
    private final StatusService statusService;
    private final AssetTypeService assetTypeService;
    private final SupplierService supplierService;
    private final AssetSearchDao assetSearchDao;
    private final CommodityService commodityService;
    private final StockRepository stockRepository;
    private final StockCommodityRepository stockCommodityRepository;
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    private final CurrentUserService currentUserService;
    private final FileUpload fileUpload;
    private final Email email;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final MovementService movementService;
    private final pride.bank.erp.config.BranchScopeService branchScopeService;
    private final pride.bank.erp.auditing.trail.AuditService auditService;
    /*
     * Repositories, not the matching services, for the bulk import.
     *
     * Every one of these is needed for a by-name lookup that must return "not found" rather than
     * throw or invent a default — the services either wrap the miss in a NotFoundException carrying
     * the wrong message for a spreadsheet row, or fall back silently, which is the behaviour the
     * rewritten importer exists to remove.
     */
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final CommodityRepository commodityRepository;
    private final UnitOfMeasureService unitOfMeasureService;
    private final StatusRepository statusRepository;
    /** Decides how far a dashboard query may reach — see DashboardScopeService. */
    private final pride.bank.erp.dashboard.DashboardScopeService dashboardScopeService;

    /** Decides whether a <em>particular</em> asset is within the caller's reach. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    /** What the stocking flow leaves behind; see requireEngravedNumberBeforeLeavingStocking. */
    private static final String CODE_REQUIRE_UPDATE = "requireUpdate";
    /** A loaner on the shelf - see {@code StatusSeeder}. */
    public static final String CODE_LOAN_AVAILABLE = "loanAvailable";

    /** Upper bound on assets scanned in-memory when computing the derived "due for disposal" list. */
    private static final int DISPOSAL_SCAN_LIMIT = 10_000;

    public Page<AssetDAO> fetchAllAssets(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String assetName,
            Long assetTypeId,
            Long assetStatusId,
            Long commodityId,
            String engravedNumber,
            Long assignedToId,
            Boolean disposed,
            String model,
            String assignedTo,
            String location,
            Long departmentId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean dueForDisposal,
            Boolean includeDeleted,
            Long branchId,
            LocalDateTime disposedFrom,
            LocalDateTime disposedTo,
            Boolean includeAwayFromBranch,
            Boolean byDisposingBranch
    ) {
        // Whose write-off it was only means something on the disposed register.
        boolean disposalOwnership = Boolean.TRUE.equals(byDisposingBranch) && Boolean.TRUE.equals(disposed);
        /*
         * Resolved before the try below, whose catch-all turns every exception into `null` - a
         * refusal must reach the caller as a refusal, not as an empty success.
         */
        pride.bank.erp.dashboard.DashboardScope scope = accessScopeService.scopeFor(
                pride.bank.erp.access.ScopeSubject.ASSETS, pride.bank.erp.access.ScopeAction.VIEW);
        Long scopedBranchId = accessScopeService.resolveBranch(pride.bank.erp.access.ScopeSubject.ASSETS, null);
        Long scopedOwnerId = accessScopeService.resolveOwner(pride.bank.erp.access.ScopeSubject.ASSETS);
        if (scope != pride.bank.erp.dashboard.DashboardScope.ALL && scopedBranchId == null && scopedOwnerId == null) {
            // Null means both "every branch" and "no branch on record", and downstream null reads as
            // no restriction - so an unconfigured account was handed the whole register.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your account is not attached to a branch, so assets cannot be shown. "
                            + "Ask an administrator to set your branch.");
        }
        // Only at BRANCH scope: an officer at SELF sees what is in their hands, and an away asset has
        // no holder. Only when asked - the reports page asks, the assets page does not.
        Long awayFromBranchId = Boolean.TRUE.equals(includeAwayFromBranch)
                && scope == pride.bank.erp.dashboard.DashboardScope.BRANCH ? scopedBranchId : null;

        try {
            AssetPage assetPage = AssetPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .pageNumber(pageNumber)
                    .sortBy(sortBy)
                    .build();
            AssetSearchCriteria assetSearchCriteria = AssetSearchCriteria
                    .builder()
                    .assetName(assetName)
                    .assetTypeId(assetTypeId)
                    .assetStatusId(assetStatusId)
                    .engravedNumber(engravedNumber)
                    .assignedToId(assignedToId)
                    .commodityId(commodityId)
                    .disposed(disposed)
                    .model(model)
                    .startDate(startDate)
                    .endDate(endDate)
                    .assignedTo(assignedTo)
                    .location(location)
                .departmentId(departmentId)
                    .branchId(branchId)
                    .disposedFrom(disposedFrom)
                    .disposedTo(disposedTo)
                    // Applied from the caller's permissions, never from the request, so no filter
                    // the client sends can widen it. AccessScopeService returns null for the branch
                    // at ALL scope (the multiplier a unit confers), the caller's own branch below
                    // that, and an owner id only at SELF — which is how an officer's listing became
                    // "what is in my hands" rather than "everything at my branch".
                    .scopedBranchId(scopedBranchId)
                    .scopedOwnerId(scopedOwnerId)
                    .awayFromBranchId(awayFromBranchId)
                    .byDisposingBranch(disposalOwnership)
                    .includeDeleted(includeDeleted)
                    .build();

            // "Due for disposal" is a derived state (depends on the receipt date and the category's
            // useful life), so it can't be expressed as a DB filter. Scan the matching (non-disposed)
            // assets, compute each one's disposal status, keep the ones that have reached their useful
            // life, and paginate the result in memory.
            if (Boolean.TRUE.equals(dueForDisposal)) {
                AssetPage scanPage = AssetPage.builder()
                        .pageSize(DISPOSAL_SCAN_LIMIT)
                        .sortDirection(determineSortDirection(sortDirection))
                        .pageNumber(0)
                        .sortBy(sortBy)
                        .build();
                Page<Asset> scan = assetSearchDao.findAllWithFilters(scanPage, assetSearchCriteria);
                List<AssetDAO> ready = mapToDTOList(scan.getContent()).stream()
                        .filter(dao -> "Ready for disposal".equals(dao.getDisposalStatus()))
                        .collect(Collectors.toList());

                int from = Math.min(pageNumber * pageSize, ready.size());
                int to = Math.min(from + pageSize, ready.size());
                return new PageImpl<>(ready.subList(from, to), PageRequest.of(pageNumber, pageSize), ready.size());
            }

            Page<Asset> assetPageResult = assetSearchDao.findAllWithFilters(assetPage, assetSearchCriteria);
            List<AssetDAO> dtoList = mapToDTOList(assetPageResult.getContent());
            if (disposalOwnership) {
                labelDisposingBranch(assetPageResult.getContent(), dtoList);
            }
            return new PageImpl<>(dtoList, assetPageResult.getPageable(), assetPageResult.getTotalElements());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * "Disposed From" for a page of the Disposal report, by the rule its scope and Branch filter use:
     * the newest disposal transfer's branch, else - no transfer at all - where the asset stands.
     */
    private void labelDisposingBranch(List<Asset> assets, List<AssetDAO> dtos) {
        Map<Long, Long> owners = movementService.disposingBranches(
                assets.stream().map(Asset::getId).toList());
        Map<Long, Branch> branches = new HashMap<>();
        branchRepository.findAllById(new java.util.HashSet<>(owners.values()))
                .forEach(b -> branches.put(b.getId(), b));
        Map<Long, Asset> byId = new HashMap<>();
        assets.forEach(a -> byId.put(a.getId(), a));

        for (AssetDAO dto : dtos) {
            Asset asset = byId.get(dto.getId());
            Branch owner = owners.containsKey(dto.getId())
                    ? branches.get(owners.get(dto.getId()))
                    : (asset != null ? asset.getBranch() : null);
            if (owner != null) {
                dto.setDisposedFromBranchId(owner.getId());
                dto.setDisposedFromBranchName(owner.getName());
            }
        }
    }

    @Transactional
    public Asset createAsset(AssetDTO request) throws NotFoundException {
        Branch branch = (request.getBranch() != null) ? branchService.findBranch(request.getBranch()) : null;
        User assignedTo = (request.getAssignedTo() != null) ? userService.findUser(request.getAssignedTo()) : null;
        Status status = (request.getAssetStatus() != null) ? statusService.findOneStatus(request.getAssetStatus()) : null;
        AssetType assetType = (request.getAssetType() != null) ? assetTypeService.findOneAssetType(request.getAssetType()) : null;
        Supplier supplier = (request.getSupplier() != null) ? supplierService.findSupplier(request.getSupplier()) : null;
        Commodity commodity = commodityService.findCommodity(request.getCategory());

        // A create names its own branch rather than inheriting one from a record, so the check is
        // against what was asked for. Null is allowed through: an asset registered without a branch
        // is unassigned stock, not another branch's property.
        accessScopeService.requireCanManage(
                pride.bank.erp.access.ScopeSubject.ASSETS,
                branch != null ? branch.getId() : null,
                null);

        // Registering an asset counts it against a stock order, so creation needs one. Refused as a 400
        // rather than left to the lookup: NotFoundException answers 200 in this application.
        if (request.getLpoNumber() == null || request.getLpoNumber().isBlank()) {
            throw new BadRequestException("Choose the stock order (LPO) this asset was received on. "
                    + "Assets with no stock order are brought in through the import, where an LPO "
                    + "reference may be recorded.");
        }
        Stock stock = findStockByLPONumber(request.getLpoNumber());
        assert commodity != null;
        checkIfStockContainsCommodityNotYetRegistered(stock, commodity);

        // Optional here, unique when given. Stocking deliberately creates assets with no engraved
        // number — 33 currently sit at `requireUpdate` waiting for somebody to read the label off the
        // item — so requiring one would break receiving stock. See requireEngravedNumberAvailable.
        requireEngravedNumberAvailable(request.getEngravedNumber(), null);

        Asset asset = Asset.builder()
                .assetDepreciationRate(request.getAssetDepreciationRate())
                .assetStatus(status)
                .supplier(supplier)
                .costOfTheAsset(parseMoneyDecimal(request.getCostOfTheAsset()))
                .assetName(request.getAssetName())
                .dateReceipt(request.getDateReceipt())
                .assetType(assetType)
                .assignedTo(assignedTo)
                .hardDiskSize(request.getHardDiskSize())
                .image(request.getImage())
                .hostname(request.getHostname())
                .make(request.getMake())
                .engravedNumber(request.getEngravedNumber())
                .interfaceType(request.getInterfaceType())
                .model(request.getModel())
                .ipAddress(request.getIpAddress())
                .macAddress(request.getMacAddress())
                .ram(request.getRam())
                .netValueB(request.getNetValueB())
                .purchaseCost(parseMoneyDecimal(request.getPurchaseCost()))
                .serialNumber(request.getSerialNumber())
                .unitOfMeasure(unitFor(request.getUnitOfMeasure(), null, commodity))
                .cpuSpeed(request.getCpuSpeed())
                .detailNetBookValue(request.getDetailNetBookValue())
                .description(request.getDescription())
                .branch(branch)
                .stock(stock)
                .commodity(commodity)
                .temporaryPool(Boolean.TRUE.equals(request.getTemporaryPool()))
                .build();
        Asset saved = assetRepository.save(asset);
        auditAsset(AuditEventType.CREATED, "ASSET_CREATED", saved,
                "Registered asset " + describe(saved)
                        + (branch == null ? "" : " at " + branch.getName()),
                AuditSeverity.INFO);
        return saved;
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    /** Records an asset event, tagged to the asset so its whole history can be pulled by id. */
    private void auditAsset(AuditEventType eventType, String action, Asset asset,
                            String description, AuditSeverity severity) {
        auditService.record(eventType, AuditModule.ASSETS, action, description, severity,
                "Asset", asset.getId());
    }

    /**
     * "Dell Latitude 5540 (PBL/IT/0412)".
     *
     * <p>The engraved number is what staff actually use to identify an item, so it goes in the
     * sentence rather than leaving the reader to look the id up.
     */
    private static String describe(Asset asset) {
        String name = asset.getAssetName() == null ? "asset" : asset.getAssetName();
        String tag = asset.getEngravedNumber();
        return (tag == null || tag.isBlank()) ? name + " (#" + asset.getId() + ")" : name + " (" + tag + ")";
    }

    private static String personName(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    /**
     * An asset's unit of measure: the one chosen, if any, from Settings → Units of Measure; otherwise
     * its commodity's. Decision recorded with V34: the commodity's unit is the default and an asset
     * may differ from it. {@code current} is the unit the asset already carries, accepted on a re-save
     * even if it has since been switched off.
     */
    private String unitFor(String chosen, String current, Commodity commodity) {
        String unit = unitOfMeasureService.resolve(chosen, current);
        if (unit != null) return unit;
        return commodity != null ? UnitOfMeasureService.normalise(commodity.getGroupName()) : null;
    }

    /** A typed LPO reference: trimmed, blank as none, at most the column's 100 characters. */
    static String lpoReferenceOf(String raw) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) return null;
        if (value.length() > 100) {
            throw new BadRequestException("The LPO reference may be at most 100 characters.");
        }
        return value;
    }

    private Stock findStockByLPONumber(String lpoNumber) throws
            NotFoundException {
        return stockRepository.findByLpoNumber(lpoNumber).orElseThrow(
                () -> new NotFoundException(String.format("Stock with LPO Number: %s is not found", lpoNumber))
        );
    }

    private void checkIfStockContainsCommodityNotYetRegistered(Stock stock, Commodity commodity)
            throws NotFoundException {

        StockCommodity stockCommodity = stockCommodityRepository.findByStockIdAndCommodityId(stock.getId(), commodity.getId())
                .orElseThrow(() -> new NotFoundException(
                        String.format("Commodity '%s' is not associated with the given stock (LPO: %s)",
                                commodity.getName(), stock.getLpoNumber())
                ));


        int availableQuantity = stockCommodity.getDeliveredQuantity() - stockCommodity.getRegisteredNumber();

        if (availableQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Insufficient quantity for commodity: "
                            + commodity.getName()
                            + " in stock with LPO: "
                            + stock.getLpoNumber()
            );
        }

        stockCommodity.setRegisteredNumber(stockCommodity.getRegisteredNumber() + 1);
        stockCommodityRepository.save(stockCommodity);
    }

    public Asset findOne(Long id) throws NotFoundException {
        return assetRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Asset with id %s is not found", id))
        );
    }

    /**
     * Refuses unless this asset is within the caller's reach.
     *
     * <p>Deliberately <b>not</b> folded into {@link #findOne}. That method is also how the movement,
     * issuance and inventory services reach an asset while acting for the system rather than for the
     * person who happened to trigger them — gating it there would refuse a storekeeper receiving an
     * item on behalf of a branch they are not attached to, which is the workflow working correctly.
     * The check belongs on the acts a person performs on an asset directly, so it is written out at
     * each of them.
     *
     * <p>This closes the hole the permission model alone could not: {@code UPDATE_ASSET} admitted a
     * branch officer to the endpoint, and nothing afterwards asked whose asset it was. They could not
     * find another branch's asset through the listing, but they could edit it by id.
     */
    /**
     * Refuses unless the caller may read this asset — by id, for the things hanging off it.
     *
     * <p>An asset's assignment history and its repair history are records <em>about</em> an asset,
     * and they leak the same information the asset does: who has held it, where it has been, what
     * went wrong with it. Guarding the asset itself while leaving its trail open means a branch
     * officer refused the detail page can still read the whole chain of custody by calling the
     * history endpoint with the same id.
     *
     * <p>Public because those services live in their own packages and should not each re-implement
     * the check — there is one answer to "may I see this asset", and it is here.
     */
    /**
     * Is this asset currently out of its branch's hands?
     *
     * <p>One asset is in maintenance with <b>no transfer movement at all</b>, so nothing ever
     * recorded where it came from and no rule can reach it for its home branch. Named here rather
     * than papered over: the answer to that one is a data correction, not a scoping change.
     *
     * <p>Public and static because the store listings ({@code InventoryReportController}) ask the same
     * question before reading where an asset was sent from. One rule, or the listing shows a row the
     * detail page then refuses to open.
     */
    public static boolean isAwayFromItsBranch(Asset asset) {
        return asset.getAssetStatus() != null
                && AWAY_FROM_BRANCH_CODES.contains(asset.getAssetStatus().getStatus());
    }

    public void requireAssetReadable(Long assetId) throws NotFoundException {
        requireAssetInScope(findOne(assetId), pride.bank.erp.access.ScopeAction.VIEW);
    }

    /**
     * The two states in which an asset is physically out of its branch's hands.
     *
     * <p>Both put it in a store held at Head Office, and both are why a branch would need to look at
     * a record that no longer reads as theirs.
     */
    public static final java.util.Set<String> AWAY_FROM_BRANCH_CODES =
            java.util.Set.of("inMaintenance", "pendingDisposal");

    private void requireAssetInScope(Asset asset, pride.bank.erp.access.ScopeAction action) {
        Long branchId = asset.getBranch() != null ? asset.getBranch().getId() : null;
        Long holderId = asset.getAssignedTo() != null ? asset.getAssignedTo().getId() : null;

        if (action == pride.bank.erp.access.ScopeAction.MANAGE) {
            accessScopeService.requireCanManage(
                    pride.bank.erp.access.ScopeSubject.ASSETS, branchId, holderId);
        } else if (isAwayFromItsBranch(asset)) {
            /*
             * While an asset is away, its home branch may still read it.
             *
             * <h2>This removes a contradiction rather than widening the scope</h2>
             * `InventoryReportController.scopeAssets` already reaches an asset when **either** end is
             * the caller's — where it sits, or where it came from — which is what lets a branch see
             * its own laptop on the Head Office bench in the IT store listing. This check was
             * single-ended, so the row was listed and then would not open: "a row that sits in your
             * list and will not open is the exact failure this scoping exists to prevent."
             *
             * `asset.branch` means *where the asset is*: `relocateAsset` overwrites it with the
             * destination store's location on receipt. Measured, seven assets in maintenance read
             * Head Office and every one of them came from Gulu.
             *
             * <h2>Three deliberate narrowings</h2>
             * <ol>
             *   <li><b>VIEW only.</b> MANAGE stays single-ended above, so a branch still cannot edit,
             *       reassign or write off an asset sitting at Head Office. That boundary is
             *       untouched.</li>
             *   <li><b>Only while it is away.</b> Keyed on the status rather than on the existence of
             *       a past transfer, so the reach lasts exactly as long as the item is out of the
             *       branch's hands. Widening on a historical event would let a branch read an asset
             *       forever because it once sent it for repair.</li>
             *   <li><b>One person is added: whoever handed it over</b> on that same transfer
             *       ({@code holderLastSentAwayFrom}). The transfer closes their assignment when Head
             *       Office receives the asset, so at SELF they would otherwise be refused the laptop
             *       My Items lists as theirs while it is on the bench. It lapses with the branch, the
             *       moment the asset comes home.</li>
             * </ol>
             *
             * <p>The person is a limb of its own, and deliberately <b>not</b> put into the owner list.
             * At SELF an owner list that names somebody admits only them, while an empty one falls
             * through to the branch, which is how a Head Office officer at SELF opens an unassigned
             * asset on their own bench. Naming the sender there would quietly refuse that officer.
             * As a separate limb it can only widen, and only by one person.
             */
            Long sentBy = movementService.holderLastSentAwayFrom(asset.getId());
            User viewer = sentBy != null ? currentUserService.getCurrentUser() : null;
            if (viewer != null && sentBy.equals(viewer.getId())) {
                return;
            }
            accessScopeService.requireCanViewAnyOf(
                    pride.bank.erp.access.ScopeSubject.ASSETS,
                    java.util.Arrays.asList(branchId, movementService.branchLastSentAwayFrom(asset.getId())),
                    java.util.Collections.singletonList(holderId));
        } else {
            accessScopeService.requireCanView(
                    pride.bank.erp.access.ScopeSubject.ASSETS, branchId, holderId);
        }
    }

    @Transactional
    public AssetDAO fetchSingleAsset(Long assetId) throws NotFoundException {
        Asset asset = findOne(assetId);
        // Deleted reads as absent, not as forbidden: the record is gone as far as the register is
        // concerned, and "not found" is the honest answer to someone holding a stale link.
        if (Boolean.TRUE.equals(asset.getDeleted())) {
            throw new NotFoundException(String.format("Asset with id %s is not found", assetId));
        }
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.VIEW);
        return buildAssetDao(asset);
    }

    @Transactional
    public AssetDAO updateAsset(AssetDTO request, Long id) throws NotFoundException {
        Branch branch = (request.getBranch() != null) ? branchService.findBranch(request.getBranch()) : null;
        User assignedTo = (request.getAssignedTo() != null) ? userService.findUser(request.getAssignedTo()) : null;
        Status status = (request.getAssetStatus() != null) ? statusService.findOneStatus(request.getAssetStatus()) : null;
        AssetType assetType = (request.getAssetType() != null) ? assetTypeService.findOneAssetType(request.getAssetType()) : null;
        Supplier supplier = (request.getSupplier() != null) ? supplierService.findSupplier(request.getSupplier()) : null;

        Asset asset = findOne(id);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);

        User oldAssignedTo = asset.getAssignedTo();
        Status oldStatus = asset.getAssetStatus();

        /*
         * An asset's stock order is fixed at receipt and is never changed by an edit, so the order is
         * the asset's own - not one looked up by an LPO the form sends. That lookup used to make the
         * LPO required on every edit and refuse any asset with no order at all: every imported asset
         * could not be edited. It also never relinked anything; it only locked whichever order matched.
         *
         * Once an asset from an order has been edited, the order may no longer be deleted (deleting it
         * reverses its receipt). An asset with no order keeps its LPO as text instead.
         */
        Stock stock = asset.getStock();
        if (stock != null) {
            if (!stock.isCanNotBeDeleted()) {
                stock.setCanNotBeDeleted(true);
                stockRepository.save(stock);
            }
        } else {
            asset.setLpoReference(lpoReferenceOf(request.getLpoReference()));
        }

        /*
         * Check if the assigned user of the asset has changed.
         * This involves comparing the existing assigned user ID with the new assigned user ID.
         * The check is null-safe to handle cases where assigned users might be null.
         * If the assigned user has changed, we call the method to track the asset reassignment history,
         * passing the asset, the new assigned user, the old status (before reassignment),
         * and the status after reassignment (which defaults to the new status if provided, otherwise the old status).
         */

        if (!Objects.equals(
                oldAssignedTo != null ? oldAssignedTo.getId() : null,
                assignedTo != null ? assignedTo.getId() : null
        )) {
            Status statusAfter = (status != null) ? status : oldStatus;
            trackAssetAssignmentHistory(asset, assignedTo, oldStatus, statusAfter);
        }

        asset.setAssetDepreciationRate(request.getAssetDepreciationRate());
        asset.setAssetStatus(status);
        asset.setSupplier(supplier);
        asset.setCostOfTheAsset(parseMoneyDecimal(request.getCostOfTheAsset()));
        asset.setAssetName(request.getAssetName());
        asset.setDateReceipt(request.getDateReceipt());
        asset.setAssetType(assetType);
        asset.setAssignedTo(assignedTo);
        asset.setHardDiskSize(request.getHardDiskSize());
        asset.setImage(request.getImage());
        asset.setHostname(request.getHostname());
        asset.setMake(request.getMake());
        /*
         * The uniqueness check the form never had, and the one place "required" belongs.
         *
         * Excluding this asset's own id, or re-saving a record without touching its number would
         * collide with itself.
         *
         * The second rule is what "required at completion rather than at creation" means in practice.
         * There is no separate Complete endpoint - `crudStates.complete` exists as a constant with a
         * comment and nothing dispatches it - so **this form is the completion**: an asset leaves
         * `requireUpdate` by being edited here, and issuance refuses anything still in that status
         * ("not yet completed after stocking"). So the number is demanded exactly when the asset
         * stops being a half-finished stock line and becomes a real, issuable item, and never before.
         */
        requireEngravedNumberAvailable(request.getEngravedNumber(), asset.getId());
        requireEngravedNumberBeforeLeavingStocking(asset, status, request.getEngravedNumber());

        asset.setEngravedNumber(request.getEngravedNumber());
        asset.setInterfaceType(request.getInterfaceType());
        asset.setModel(request.getModel());
        asset.setIpAddress(request.getIpAddress());
        asset.setMacAddress(request.getMacAddress());
        asset.setRam(request.getRam());
        asset.setNetValueB(request.getNetValueB());
        asset.setPurchaseCost(parseMoneyDecimal(request.getPurchaseCost()));
        asset.setSerialNumber(request.getSerialNumber());
        // The asset's own unit if one is chosen (a unit it already carries is kept even if since
        // switched off), else its commodity's.
        asset.setUnitOfMeasure(unitFor(request.getUnitOfMeasure(), asset.getUnitOfMeasure(), asset.getCommodity()));
        asset.setCpuSpeed(request.getCpuSpeed());
        asset.setDetailNetBookValue(request.getDetailNetBookValue());
        asset.setDescription(request.getDescription());
        asset.setBranch(branch);
        if (request.getTemporaryPool() != null) {
            asset.setTemporaryPool(request.getTemporaryPool());
        }
        Asset saved = assetRepository.save(asset);

        /*
         * One row per edit, not one per changed field.
         *
         * Envers already holds the field-level before-and-after for every column on this entity; the
         * trail's job is to say that someone edited this asset, when, and from where. Duplicating the
         * column diff here would double the storage to answer a question Envers answers better.
         *
         * The two changes that are called out are the ones with consequences elsewhere: who holds the
         * asset, and what state it is in.
         */
        StringBuilder detail = new StringBuilder("Updated asset " + describe(saved));
        if (!sameUser(oldAssignedTo, assignedTo)) {
            detail.append(" — holder ")
                    .append(oldAssignedTo == null ? "unassigned" : personName(oldAssignedTo))
                    .append(" → ")
                    .append(assignedTo == null ? "unassigned" : personName(assignedTo));
        }
        if (status != null && oldStatus != null && !status.getId().equals(oldStatus.getId())) {
            detail.append(" — status ").append(oldStatus.getName()).append(" → ").append(status.getName());
        }
        auditAsset(AuditEventType.UPDATED, "ASSET_UPDATED", saved, detail.toString(), AuditSeverity.INFO);

        return buildAssetDao(saved);
    }

    private static boolean sameUser(User a, User b) {
        if (a == null || b == null) return a == b;
        return a.getId() != null && a.getId().equals(b.getId());
    }

    /**
     * Tracks the reassignment history of an asset whenever its assigned user changes.
     * <p>
     * This method performs the following steps:
     * - Finds the currently active assignment history record for the given asset (where endDate is null).
     * - If such a record exists, it sets its endDate to the current time and updates its statusAfter field.
     * - Creates a new assignment history record representing the new assignment with the current time as startDate,
     * along with the status before and after the assignment.
     *
     * @param asset        the asset being reassigned
     * @param newUser      the new user to whom the asset is assigned
     * @param statusBefore the status of the asset before reassignment
     * @param statusAfter  the status of the asset after reassignment
     */
    @Transactional
    public void trackAssetAssignmentHistory(Asset asset, User newUser, Status statusBefore, Status statusAfter) {
        AssetAssignmentHistory activeHistory = assetAssignmentHistoryRepository
                .findActiveByAssetId(asset.getId());

        LocalDateTime now = LocalDateTime.now();

        if (activeHistory != null) {
            activeHistory.setEndDate(now);
            activeHistory.setStatusAfter(statusBefore);
            assetAssignmentHistoryRepository.save(activeHistory);
        }

        AssetAssignmentHistory newHistory = AssetAssignmentHistory.builder()
                .asset(asset)
                .user(newUser)
                .startDate(now)
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .build();

        assetAssignmentHistoryRepository.save(newHistory);
    }

    @Transactional
    public List<AssetTypeSummaryView> listDashboardAssetReport() {
        return assetRepository.getAssetTypeSummary();
    }

    /** Branch × category asset counts — the grid behind the dashboard's all-branches widget. */
    public List<AssetBranchSummaryView> listDashboardAssetReportByBranch() {
        return assetRepository.getAssetSummaryByBranchAndType();
    }


    private List<AssetDAO> mapToDTOList(List<Asset> assets) {
        return assets.stream().map(this::buildAssetDao).toList();
    }

    /** Derived depreciation values returned to the client (display-only / read-only on the form). */
    private record DepreciationValues(String grossValue, String currentNetBookValue,
                                      String accumulatedDepreciation, String annualDepreciation,
                                      String monthlyDepreciation, String rate, String disposalStatus) {}

    /**
     * Derives an asset's depreciation using the <b>reducing-balance</b> method.
     * Each year the charge is {@code openingNBV * rate}; the monthly figure is that year's charge ÷ 12.
     * <ul>
     *   <li>Net Value = gross (purchase) cost basis</li>
     *   <li>Detail Net Book Value = current depreciated value (prorated by whole months since receipt)</li>
     *   <li>Accumulated Depreciation = cost − current NBV</li>
     *   <li>Annual / Monthly Depreciation = the charge at the current value</li>
     *   <li>Disposal Status = "Ready for disposal" once the asset's age reaches the category's useful life</li>
     * </ul>
     * Disposal is purely age-based (useful life in months) and is evaluated independently of the
     * depreciation figures. Currency values carry thousands separators; the rate carries a percent sign.
     * When the category has no rate or the asset has no cost basis, stored values are returned unchanged.
     */
    private DepreciationValues computeDepreciation(Asset asset) {
        Double ratePct = (asset.getAssetType() != null) ? asset.getAssetType().getDepreciationRate() : null;
        Integer usefulLifeMonths = (asset.getAssetType() != null) ? asset.getAssetType().getUsefulLifeMonths() : null;
        Double cost = toDouble(asset.getCostOfTheAsset());
        long elapsedMonths = monthsSinceReceipt(asset.getDateReceipt());

        // Disposal is age-based and independent of the depreciation math: an asset is ready once
        // it has been held for at least its category's useful life.
        String disposalStatus = (usefulLifeMonths != null && usefulLifeMonths > 0)
                ? (elapsedMonths >= usefulLifeMonths ? "Ready for disposal" : "In service")
                : null;

        if (ratePct == null || cost == null) {
            return new DepreciationValues(
                    asset.getNetValueB(),
                    asset.getDetailNetBookValue(),
                    null, null, null,
                    ratePct != null ? formatNumber(ratePct) + "%" : asset.getAssetDepreciationRate(),
                    disposalStatus);
        }

        double rate = ratePct / 100.0;

        // Reducing balance: recalc the charge each year on the opening NBV and accrue monthly.
        double nbv = cost;
        long remaining = elapsedMonths;
        while (remaining > 0 && nbv > 0) {
            double monthly = (nbv * rate) / 12.0;
            long monthsThisYear = Math.min(12, remaining);
            nbv -= monthly * monthsThisYear;
            remaining -= monthsThisYear;
        }

        double currentNbv = Math.max(0.0, nbv);
        double accumulated = cost - currentNbv;

        // Forward-looking charge at the current value.
        double annualNow = currentNbv * rate;
        double monthlyNow = annualNow / 12.0;

        return new DepreciationValues(
                formatCurrency(cost),
                formatCurrency(currentNbv),
                formatCurrency(accumulated),
                formatCurrency(annualNow),
                formatCurrency(monthlyNow),
                formatNumber(ratePct) + "%",
                disposalStatus);
    }

    private Double parseMoney(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whole months between the receipt date and today; 0 when missing/unparseable/future.
     *
     * <p>Delegates to {@link AssetLifecycle} so the disposal gate and this panel cannot drift apart.
     */
    private long monthsSinceReceipt(String dateReceipt) {
        return AssetLifecycle.monthsInService(dateReceipt);
    }

    /** Currency display with thousands separators and 2 decimals, e.g. 1234567.5 → "1,234,567.50". */
    private String formatCurrency(double value) {
        return String.format(java.util.Locale.US, "%,.2f", value);
    }

    /** Formats a stored money string for display (thousands separators); returns it unchanged when not numeric. */
    private String formatMoneyString(String value) {
        Double parsed = parseMoney(value);
        return parsed != null ? formatCurrency(parsed) : value;
    }

    /**
     * Parses money typed by a user — grouping commas and spaces removed — into the {@link BigDecimal}
     * actually stored. Returns null for blank or non-numeric input rather than throwing, so a stray
     * character in one field cannot fail the whole save.
     */
    private BigDecimal parseMoneyDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Stored money as a double, for the depreciation maths. */
    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /** Stored money formatted for display; the API surface stays a string. */
    private String formatMoney(BigDecimal value) {
        return value == null ? null : formatCurrency(value.doubleValue());
    }

    /** Strips grouping commas/spaces so money is persisted as a clean numeric string (display adds separators). */
    private String stripGroupingCommas(String value) {
        return value == null ? null : value.replaceAll("[,\\s]", "");
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public AssetDAO buildAssetDao(Asset asset) {
        DepreciationValues dep = computeDepreciation(asset);
        return AssetDAO.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .hostname(asset.getHostname())
                .detailNetBookValue(dep.currentNetBookValue())
                .engravedNumber(asset.getEngravedNumber())
                .dateReceipt(asset.getDateReceipt())
                .make(asset.getMake())
                .unitOfMeasure(asset.getUnitOfMeasure())
                .lpoReference(asset.getLpoReference())
                .purchaseCost(formatMoney(asset.getPurchaseCost()))
                .costOfTheAsset(formatMoney(asset.getCostOfTheAsset()))
                .netValueB(dep.grossValue())
                .model(asset.getModel())
                .serialNumber(asset.getSerialNumber())
                .ram(asset.getRam())
                .cpuSpeed(asset.getCpuSpeed())
                .hardDiskSize(asset.getHardDiskSize())
                .macAddress(asset.getMacAddress())
                .ipAddress(asset.getIpAddress())
                .interfaceType(asset.getInterfaceType())
                .assetDepreciationRate(dep.rate())
                .accumulatedDepreciation(dep.accumulatedDepreciation())
                .annualDepreciation(dep.annualDepreciation())
                .monthlyDepreciation(dep.monthlyDepreciation())
                .disposalStatus(dep.disposalStatus())
                .description(asset.getDescription())
                .image(asset.getImage())
                .lastRepairedBy(asset.getLastRepairedBy())
                .temporaryPool(asset.isTemporaryPool())

                // Map nested references as simple DTOs or just their IDs and names to avoid recursion
                .supplier(asset.getSupplier() != null ?
                        Supplier.builder()
                                .id(asset.getSupplier().getId())
                                .name(asset.getSupplier().getName())
                                .address(asset.getSupplier().getAddress())
                                .telephone(asset.getSupplier().getTelephone())
                                .email(asset.getSupplier().getEmail())
                                .build() : null)
                .assignedTo(asset.getAssignedTo() != null ?
                        User.builder()
                                /*
                                 * The id was missing, so the edit form - which binds the holder by id -
                                 * opened with Assigned To empty, and saving it sent no holder. The
                                 * history row that change writes has a NOT NULL user, so every such
                                 * save failed outright. The supplier beside it always carried its id.
                                 */
                                .id(asset.getAssignedTo().getId())
                                .availability(asset.getAssignedTo().getAvailability())
                                .branch(asset.getAssignedTo().getBranch())
                                .email(asset.getAssignedTo().getEmail())
                                .firstName(asset.getAssignedTo().getFirstName())
                                .lastName(asset.getAssignedTo().getLastName())
                                .otherName(asset.getAssignedTo().getOtherName())
                                .gender(asset.getAssignedTo().getGender())
                                .staffNumber(asset.getAssignedTo().getStaffNumber())
                                .title(asset.getAssignedTo().getTitle())
                                .department(asset.getAssignedTo().getDepartment())
                                .isEnabled(asset.getAssignedTo().isEnabled())
                                .isAccountNonLocked(asset.getAssignedTo().isAccountNonLocked())
                                .blocked(asset.getAssignedTo().isBlocked())
                                .build() : null)
                .branch(asset.getBranch() != null ?
                        Branch.builder()
                                .id(asset.getBranch().getId())
                                .district(asset.getBranch().getDistrict())
                                .branchManager(asset.getBranch().getBranchManager())
                                .branchOperationsManager(asset.getBranch().getBranchOperationsManager())
                                .creditAdministrator(asset.getBranch().getCreditAdministrator())
                                .relationshipManager(asset.getBranch().getRelationshipManager())
                                .name(asset.getBranch().getName())
                                .region(asset.getBranch().getRegion())
                                .telephone(asset.getBranch().getTelephone())
                                .email(asset.getBranch().getEmail())
                                /*
                                 * Dropped until now, and it read as `false` rather than as missing.
                                 *
                                 * This DTO is assembled field by field to avoid recursion, and any
                                 * field left out of a primitive-boolean builder silently becomes
                                 * false - so `asset.branch.isHeadOffice` was false for every asset in
                                 * the estate, including the ones at Head Office. Measured: an asset
                                 * whose branch is named "Head Office" serialised with
                                 * `isHeadOffice: false`, while the same flag on its *holder's* branch
                                 * came through correctly, because that one passes the real entity.
                                 *
                                 * Nothing read it, so nothing was visibly wrong - which is exactly
                                 * how it survived. Anything keying on it would have matched nothing
                                 * and looked implemented.
                                 */
                                .isHeadOffice(asset.getBranch().isHeadOffice())
                                .build() : null)
                .assetStatus(asset.getAssetStatus() != null ?
                        Status.builder()
                                .id(asset.getAssetStatus().getId())
                                .code(asset.getAssetStatus().getCode())
                                .name(asset.getAssetStatus().getName())
                                .description(asset.getAssetStatus().getDescription())
                                .status(asset.getAssetStatus().getStatus())
                                .build() : null)
                .assetType(asset.getAssetType() != null ?
                        AssetType.builder()
                                .id(asset.getAssetType().getId())
                                .name(asset.getAssetType().getName())
                                .description(asset.getAssetType().getDescription())
                                // Repair routing config rides along so the repair-transfer UI can
                                // default the destination and enforce the EXTERNAL consultant rule.
                                .repairable(asset.getAssetType().isRepairable())
                                .repairDestination(asset.getAssetType().getRepairDestination())
                                .build() : null)
                .commodity(asset.getCommodity() != null ?
                        Commodity.builder()
                                .id(asset.getCommodity().getId())
                                .name(asset.getCommodity().getName())
                                .groupName(asset.getCommodity().getGroupName())
                                .build() : null)
                .stock(asset.getStock() != null ?
                        Stock.builder()
                                .id(asset.getStock().getId())
                                .referenceNumber(asset.getStock().getReferenceNumber())
                                .name(asset.getStock().getName())
                                .totalCost(asset.getStock().getTotalCost())
                                .balanceCost(asset.getStock().getBalanceCost())
                                .lpoNumber(asset.getStock().getLpoNumber())
                                .grnNumber(asset.getStock().getGrnNumber())
                                .build() : null)
                .createDate(asset.getCreateDate())
                .lastModified(asset.getLastModified())
                .createdBy(asset.getCreatedBy())
                .lastModifiedBy(asset.getLastModifiedBy())
                .build();
    }

    /**
     * Per-category asset counts for the dashboard, narrowed to the viewer's scope.
     *
     * <p>This used to read {@code currentUser.getBranch().getId()} unconditionally, which had two
     * consequences. A Head Office user holding VIEW_ALL_BRANCHES got Head Office's own assets rather
     * than a national roll-up — the headline figures on the page were not what their label implied.
     * And a user with no duty station on record hit a NullPointerException before the query ran.
     *
     * <p>Both are gone: {@link DashboardScopeService} decides the branch (null meaning the roll-up)
     * and the owner (non-null only at SELF scope, so a branch officer's figures count what he holds
     * rather than what his branch holds).
     *
     * @param requestedBranchId the branch the page's selector is focused on; ignored below ALL scope
     *                          except that naming someone else's branch is refused outright
     */
    public List<AssetTypeStatsProjection> getAssetTypeStatistics(Long requestedBranchId) {
        Long branchId = dashboardScopeService.resolveBranch(requestedBranchId);
        Long ownerId = dashboardScopeService.resolveOwner();
        return assetRepository.getAssetStatsScoped(branchId, ownerId);
    }

    /**
     * The signed-in user's own assets, grouped by category.
     *
     * <p>Disposed assets are excluded. They were not, so an item written off months ago still sat
     * under "assets assigned to you" — and the widget's whole claim is that you are accountable for
     * what it lists. Nobody is accountable for a disposed asset.
     *
     * <p>Each group now carries its category id as well as its name, because the asset detail route
     * is per-category ({@code /assets/general/{assetTypeId}/view/{id}}); without it the dashboard
     * could not build a link and the rows were dead.
     */
    public List<AssetGroupDTO> getAssetsGroupedByType() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null) return List.of();

        List<Asset> assets = assetRepository.findAllByAssignedToIdWithTypeAndStatus(currentUser.getId())
                .stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDisposed()))
                .toList();

        // Keyed by the AssetType itself rather than its name, so the id travels with the group.
        Map<AssetType, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(Asset::getAssetType));

        return grouped.entrySet().stream()
                .map(entry -> new AssetGroupDTO(
                        entry.getKey().getName(),
                        entry.getKey().getId(),
                        (long) entry.getValue().size(),
                        entry.getValue().stream()
                                .map(a -> new AssetGroupDTO.AssetDTO(
                                        a.getId(),
                                        a.getAssetName(),
                                        a.getEngravedNumber(),
                                        a.getAssetStatus() != null ? a.getAssetStatus().getName() : null,
                                        a.getSerialNumber(),
                                        a.getBranch() != null ? a.getBranch().getName() : null
                                ))
                                .collect(Collectors.toList())
                ))
                .sorted(Comparator.comparing(AssetGroupDTO::getType))
                .collect(Collectors.toList());
    }

    /**
     * The navigation bar's search: find an asset by the number engraved on it.
     *
     * <h2>Scoped, and multi-ended on purpose</h2>
     * This had no scope of any kind — it went straight to the repository, so anybody holding
     * {@code READ_ASSET} could find any asset in the bank by tag and be sent to a detail page that
     * then refused them. A control that lands on a refusal reads as a broken screen rather than a
     * boundary.
     *
     * <p>The reach is the <b>same multi-ended rule the repair guard uses</b>: in range when either
     * the asset's current branch or the branch recorded on its repair transfer is the caller's. A
     * plain branch equality is the obvious rule and would be wrong in both directions, because
     * {@code asset.branch} means <em>where the asset is</em> rather than whose it is: a Gulu laptop
     * on the Head Office bench reads Head Office once received and Gulu before that. Scan the label
     * on the bench with a strict rule and it is unfindable by whichever side is holding it.
     *
     * <p>Out-of-reach matches are <b>omitted rather than refused</b>, so the answer is the same
     * "nothing here" a non-existent tag gets. Refusing would confirm that the number exists
     * somewhere, which is the one thing a search must not leak to somebody who may not see it.
     *
     * @return every match the caller may see — normally one, empty when there is none, and more than
     *         one only if the unique index added in V25 is ever lost
     */
    @Transactional(readOnly = true)
    public List<AssetTagMatch> findAssetsByTagName(String tagName) {
        String tag = tagName == null ? null : tagName.trim();
        if (tag == null || tag.isEmpty()) {
            return List.of();
        }

        return assetRepository.findLiveByEngravedNumber(tag).stream()
                .filter(this::withinTagSearchReach)
                .map(asset -> AssetTagMatch.builder()
                        .id(asset.getId())
                        // The detail route is /assets/general/:typeId/view/:id — the id alone cannot
                        // open the page.
                        .assetTypeId(asset.getAssetType() != null ? asset.getAssetType().getId() : null)
                        .engravedNumber(asset.getEngravedNumber())
                        .assetName(asset.getAssetName())
                        .serialNumber(asset.getSerialNumber())
                        .branchName(asset.getBranch() != null ? asset.getBranch().getName() : null)
                        .status(asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null)
                        .build())
                .toList();
    }

    /**
     * An asset may not leave {@code requireUpdate} without the number engraved on it.
     *
     * <p>{@code requireUpdate} is what the stocking flow leaves behind — the issuance service calls
     * it "not yet completed after stocking" and refuses to issue anything in it. So the status is
     * the application's own marker for "this record is half-finished", and the engraved number is
     * the half that is missing. Letting an asset out of that state without one produces exactly the
     * rows this rule exists to stop: issuable, assignable items that the tag search can never find.
     *
     * <p>Deliberately <b>not</b> a check on every update. An asset already in {@code requireUpdate}
     * can be edited freely while it stays there — a receiving officer correcting a supplier or a cost
     * before the label has been read should not be blocked — and one that already has a number is
     * unaffected either way.
     */
    private void requireEngravedNumberBeforeLeavingStocking(
            Asset asset, Status newStatus, String engravedNumber) {

        boolean wasStocking = asset.getAssetStatus() != null
                && CODE_REQUIRE_UPDATE.equals(asset.getAssetStatus().getStatus());
        boolean staysStocking = newStatus == null
                || CODE_REQUIRE_UPDATE.equals(newStatus.getStatus());

        if (!wasStocking || staysStocking) {
            return;
        }

        if (engravedNumber == null || engravedNumber.isBlank()) {
            throw new pride.bank.erp.exception.BadRequestException(
                    "This asset is still awaiting completion after stocking. Record the number "
                            + "engraved on it before moving it out of that state \u2014 without one it "
                            + "cannot be found by the tag search.");
        }
    }

    /**
     * May the caller open this asset's record? The tag search answers with the detail page's own
     * guard, not a copy of it.
     *
     * <p>It used to restate the rule — where the asset sits, or the branch it was sent away from —
     * and the copy fell behind when the guard gained its third limb, the person who handed the asset
     * over for repair. So an officer at SELF scanning the label of their own laptop on the repair
     * bench was told no such tag existed, while My Items listed it and its page opened. Calling the
     * guard makes "the search finds it" and "the record opens" one fact: a result you can see and
     * cannot follow, or one you could follow and cannot see, is no longer possible.
     *
     * <p>A refusal is translated into "not in reach", never propagated: out-of-reach matches are
     * omitted, so the answer stays the same as for a number that does not exist.
     */
    private boolean withinTagSearchReach(Asset asset) {
        try {
            requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.VIEW);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException refused) {
            return false;
        }
    }

    /**
     * Refuses an engraved number already recorded against another asset.
     *
     * <h2>One rule, three write paths</h2>
     * The bulk import has always checked this. {@code createAsset} and {@code updateAsset} never did
     * — they set the field straight from the request — so the import refused a duplicate politely
     * while the form beside it created one silently. That is how the three collisions V25 had to
     * repair came to exist.
     *
     * <p>Kept alongside the database constraint rather than replaced by it: the index is what makes
     * uniqueness true when two people save at once, and this is what produces a message naming the
     * asset already holding the number. A constraint on its own surfaces as a stack trace.
     *
     * @param excludingAssetId the asset being edited, so re-saving it does not collide with itself;
     *                         null when creating
     */
    void requireEngravedNumberAvailable(String engravedNumber, Long excludingAssetId) {
        if (engravedNumber == null || engravedNumber.isBlank()) {
            // Absent is allowed: stocking creates assets without one, to be filled in on completion.
            return;
        }

        List<Asset> clashes =
                assetRepository.findOthersByEngravedNumber(engravedNumber.trim(), excludingAssetId);
        if (clashes.isEmpty()) {
            return;
        }

        Asset held = clashes.get(0);
        throw new pride.bank.erp.exception.BadRequestException(String.format(
                "Engraved number '%s' is already recorded against %s (asset #%d). "
                        + "An engraved number identifies one item, so it cannot be reused.",
                engravedNumber.trim(),
                held.getAssetName() == null ? "another asset" : held.getAssetName(),
                held.getId()));
    }

    /**
     * Stamps the write-off on the asset itself. Package-private and called only once the asset has
     * physically reached the Disposal store, so the flag can no longer be set on an asset still
     * sitting in a branch.
     *
     * <p>This was public and reachable from {@code POST /assets/{id}}, which let a caller mark an
     * asset disposed without moving it anywhere. The endpoint is gone; disposal starts at
     * {@code POST /movements/disposal}.
     */
    @Transactional
    AssetDAO markDisposed(Long assetID) throws NotFoundException {
        Asset asset = findOne(assetID);
        asset.setDisposed(true);
        asset.setDisposalDate(LocalDateTime.now());
        Asset data = assetRepository.save(asset);
        // Filed under Disposal rather than Assets: it is the end of the item's life, and the people
        // who audit write-offs will be filtering for exactly this.
        auditService.record(AuditEventType.DELETED, AuditModule.DISPOSAL, "ASSET_DISPOSED",
                "Disposed of asset " + describe(data), AuditSeverity.WARNING,
                "Asset", data.getId());
        return buildAssetDao(data);
    }

    @Transactional
    public AssetDAO reassignAsset(Long assetID, ReassignAsset reassignAsset)
            throws NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);

        /*
         * A loaner is lent through Temporary Replacement, which records it as a loan and brings it
         * back. Reassigning it would hand it over as an ordinary assignment while it is still flagged
         * as pool stock - a loan nothing would ever ask for back. Making a loan permanent is its own
         * action on the outstanding-loaners list.
         */
        if (asset.isTemporaryPool()) {
            throw new IllegalArgumentException(describe(asset) + " is a temporary-replacement loaner. "
                    + "Lend it through Temporary Replacement, or receive it into store with "
                    + "\"Available for temporary issuance\" switched off before assigning it.");
        }

        Long previousUserId = null;
        if (asset.getAssignedTo() != null) {
            previousUserId = asset.getAssignedTo().getId();
        }

        User user = userService.findUser(reassignAsset.assignedTo());
        asset.setAssignedTo(user);
        asset.setBranch(user.getBranch());

        /*
         * Out of the store and onto the person.
         *
         * Assigning an asset that sat in a store used to leave both standing: it read "in the Admin
         * store" and "with Jane" at once, and kept the status it had on the shelf - "Sent to Store"
         * or "Available for Issuance" - so the register said nobody had it. Statuses that describe
         * something else happening to the asset (in maintenance, in transit, pending disposal) are
         * left as they are; this only settles the ones that mean "on a shelf".
         */
        asset.setCurrentStore(null);
        String shelfCode = asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null;
        if (shelfCode == null || "sentToStore".equals(shelfCode) || "issuanceAvailable".equals(shelfCode)) {
            asset.setAssetStatus(statusService.findByStatusCode("assetAssigned"));
        }

        AssetDAO data = buildAssetDao(assetRepository.save(asset));
        updateAssetAssignmentHistory(asset, user);
        if (previousUserId != null) {
            addEndDateForPreviousAssignedTo(assetID, previousUserId);
        }
        notifyUserAfterAssignment(user, asset);
        auditAsset(AuditEventType.UPDATED, "ASSET_ASSIGNED", asset,
                "Assigned asset " + describe(asset) + " to " + personName(user)
                        + (asset.getBranch() == null ? "" : " at " + asset.getBranch().getName()),
                AuditSeverity.INFO);
        return data;
    }

    public AssetDAO uploadAssetImage(MultipartFile file, Long assetID)
            throws IOException, NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);
        String filePath = null;

        if (file != null && !file.isEmpty()) {
            try {
                filePath = fileUpload.uploadFileToLocalDirectory(file);
            } catch (IOException ex) {
                throw new IOException("Failed to upload file: " + ex.getMessage(), ex);
            }
        }
        asset.setImage(filePath);
        return buildAssetDao(assetRepository.save(asset));
    }

    public AssetDAO removeAssetImage(Long assetID)
            throws NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);
        asset.setImage(null);
        return buildAssetDao(assetRepository.save(asset));
    }

    /**
     * How many soft-deleted assets of one category the caller could restore.
     *
     * <p>Exists so the UI can hide the "Deleted records" view when there is nothing in it. Scoped
     * exactly like the listing, so the count never promises records the view would not show.
     */
    @Transactional(readOnly = true)
    public long countDeletedAssets(Long assetTypeId) {
        return assetRepository.countDeletedInScope(
                assetTypeId,
                accessScopeService.resolveBranch(pride.bank.erp.access.ScopeSubject.ASSETS, null),
                accessScopeService.resolveOwner(pride.bank.erp.access.ScopeSubject.ASSETS));
    }

    /**
     * Soft-deletes an asset: hidden from every listing, kept in the database.
     *
     * <p>Not the same act as disposal. Disposal is a business event — an asset reaching the end of
     * its life, written off through a movement, still counted in the register as disposed. This is
     * administrative: the record should not have existed. A duplicate, a mis-keyed import row.
     *
     * <p>The row survives so its assignment history and repair history still point at something
     * real, and so the audit trail reads back. Recorded at WARNING because a record leaving the
     * register is worth noticing even when it is reversible.
     */
    @Transactional
    public AssetDAO deleteAsset(Long assetID, String reason) throws NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);

        if (Boolean.TRUE.equals(asset.getDeleted())) {
            return buildAssetDao(asset);   // idempotent; deleting twice is not an error
        }

        asset.setDeleted(true);
        Asset saved = assetRepository.save(asset);

        auditAsset(AuditEventType.DELETED, "ASSET_DELETED", saved,
                "Deleted asset " + describe(saved)
                        + (reason == null || reason.isBlank() ? "" : " — " + reason)
                        + ". The record is hidden from the register but kept, and can be restored.",
                AuditSeverity.WARNING);
        return buildAssetDao(saved);
    }

    /**
     * Puts a soft-deleted asset back into the register.
     *
     * <p>The reason the flag is a flag. A delete that could not be undone would be a worse hard
     * delete — it would keep the row without keeping the option.
     */
    @Transactional
    public AssetDAO restoreAsset(Long assetID) throws NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);

        if (!Boolean.TRUE.equals(asset.getDeleted())) {
            return buildAssetDao(asset);   // already in the register
        }

        asset.setDeleted(false);
        Asset saved = assetRepository.save(asset);

        auditAsset(AuditEventType.UPDATED, "ASSET_RESTORED", saved,
                "Restored asset " + describe(saved) + " to the register", AuditSeverity.INFO);
        return buildAssetDao(saved);
    }

    /**
     * Hands an asset back into a store, optionally marking it as temporary-replacement pool stock.
     *
     * <p>This is the single action that actually puts an asset <i>in</i> a store. It replaces two
     * that each did half the job: the old "Send to Store" set a status and cleared the holder but
     * never set {@code currentStore}, left the assignment history row open, and credited no store —
     * so the asset ended up in no store at all; and "Temporary Pool" only flipped a boolean without
     * moving anything. Because the pool picker looks up assets by
     * {@code currentStore.storeType}, an asset "sent to store" could never appear in it, which is
     * why temporary replacement was unusable however many assets were flagged.
     *
     * <p>Recorded as a {@link MovementType#RETURN_TO_STORE} movement so the hand-back is audited
     * like every other stock move, rather than being an invisible field update.
     *
     * @param storeId       destination store; defaults to the asset's branch ADMIN store
     * @param temporaryPool when non-null, also sets whether this asset is loanable pool stock
     */
    @Transactional
    public AssetDAO sendAssetToStore(Long assetID, Long storeId, Boolean temporaryPool)
            throws NotFoundException {
        return sendAssetToStore(assetID, storeId, temporaryPool, null, null);
    }

    /** As above, carrying the recorded no-approver override for a cross-location hand-back. */
    @Transactional
    public AssetDAO sendAssetToStore(Long assetID, Long storeId, Boolean temporaryPool,
                                     Boolean proceedWithoutApproval, String bypassReason)
            throws NotFoundException {
        Asset asset = findOne(assetID);
        requireAssetInScope(asset, pride.bank.erp.access.ScopeAction.MANAGE);

        // Whether the asset ends up as a loaner: what was asked, else what it already is.
        boolean loaner = temporaryPool != null ? temporaryPool : asset.isTemporaryPool();

        /*
         * Where it goes. Named outright wins; otherwise a loaner goes to its category's loaner store
         * and anything else to the Admin store where it stands.
         *
         * The loaner default used to be the same Admin store as everything else, so an IT loaner
         * received with no store chosen landed in the Head Office Admin store - lendable, but not
         * where the IT store page or anybody looking for it would find it.
         */
        Store destination = (storeId != null)
                ? storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store " + storeId + " not found"))
                : (loaner ? loanerStoreFor(asset) : resolveDefaultStoreFor(asset));

        // The loan pool is IT and Admin stores only - the temporary-replacement picker reads nothing
        // else. A loaner booked into a Disposal or courier store would be flagged lendable and never
        // offered, which is how one ended up in the Disposal store.
        if (loaner && destination.getStoreType() != pride.bank.erp.store.StoreType.IT
                && destination.getStoreType() != pride.bank.erp.store.StoreType.ADMIN) {
            throw new IllegalArgumentException("A loaner must be kept in an IT or Admin store; "
                    + destination.getName() + " is a " + destination.getStoreType() + " store.");
        }

        User holder = asset.getAssignedTo();
        if (holder != null) {
            // The holder is handing it back, so the movement records them as the source. It has no
            // store to debit — the asset left the store when it was issued.
            movementService.createMovement(MovementDTO.builder()
                    .movementType(MovementType.RETURN_TO_STORE)
                    .sourceUserId(holder.getId())
                    .destStoreId(destination.getId())
                    .remarks("Asset returned to " + destination.getName())
                    .proceedWithoutApproval(proceedWithoutApproval)
                    .bypassReason(bypassReason)
                    .items(List.of(MovementItemDTO.builder().assetId(assetID).quantity(1).build()))
                    .build());
        }

        // Applied here rather than waiting for movement completion: the storekeeper is physically
        // taking the item now, and an unassigned asset with no store is the exact broken state this
        // method exists to prevent.
        inventoryService.returnAssetToStore(asset, destination);

        asset.setTemporaryPool(loaner);
        /*
         * A loaner reads Available for Loan; anything else is simply in store.
         *
         * Loaners used to read "Available for Issuance" - the status of stock waiting to be issued
         * against a request. On the register the two were indistinguishable, and issuance, which
         * checks only that status, would hand a loaner out permanently.
         */
        asset.setAssetStatus(statusService.findByStatusCode(loaner ? CODE_LOAN_AVAILABLE : "sentToStore"));

        Asset saved = assetRepository.save(asset);
        // The RETURN_TO_STORE movement above is audited in its own right, but only when someone was
        // holding the asset — and it says nothing about the pool flag, which decides whether the item
        // can be loaned out again.
        auditAsset(AuditEventType.UPDATED, "ASSET_SENT_TO_STORE", saved,
                "Sent asset " + describe(saved) + " to " + destination.getName()
                        + (holder == null ? "" : ", handed back by " + personName(holder))
                        + (saved.isTemporaryPool() ? " — held as temporary pool stock" : ""),
                AuditSeverity.INFO);
        return buildAssetDao(saved);
    }

    /**
     * The store a loaner of this category belongs in: the bank's IT store for an IT-routed category,
     * the Head Office Admin store for anything else - the same split the repair routing uses, so a
     * loaner sits with the people who lend and fix that kind of asset.
     */
    private Store loanerStoreFor(Asset asset) throws NotFoundException {
        pride.bank.erp.assetType.RepairDestination routing =
                asset.getAssetType() != null && asset.getAssetType().getRepairDestination() != null
                        ? asset.getAssetType().getRepairDestination()
                        : pride.bank.erp.assetType.RepairDestination.IT;
        if (routing == pride.bank.erp.assetType.RepairDestination.IT) {
            return storeRepository.findByStoreType(pride.bank.erp.store.StoreType.IT).stream().findFirst()
                    .orElseThrow(() -> new NotFoundException("The organisation has no IT store"));
        }
        Branch headOffice = branchRepository.findFirstByIsHeadOfficeTrue()
                .orElseThrow(() -> new NotFoundException("No Head Office branch is configured"));
        return storeService.resolveAdminStore(headOffice);
    }

    /** Where an asset goes when no store is named: the ADMIN store of the branch holding it. */
    private Store resolveDefaultStoreFor(Asset asset) throws NotFoundException {
        if (asset.getCurrentStore() != null) return asset.getCurrentStore();
        Branch branch = asset.getAssignedTo() != null && asset.getAssignedTo().getBranch() != null
                ? asset.getAssignedTo().getBranch()
                : asset.getBranch();
        if (branch == null) {
            throw new NotFoundException(
                    "Asset " + asset.getId() + " has no branch, so a destination store cannot be resolved");
        }
        return storeService.resolveAdminStore(branch);
    }

    /**
     * The largest number of rows one request may carry.
     *
     * <p>Two hard ceilings sit behind this: {@code spring.servlet.multipart.max-request-size} is 2MB,
     * which a ~700-byte JSON row exhausts at roughly 2,900 rows, and the browser's HTTP client gives
     * up after 60 seconds, which at 15-40ms of lookups and a save per row falls somewhere around
     * 2,400. A single large request therefore fails somewhere past two thousand rows — and it fails
     * opaquely, after committing an unknown number of them.
     *
     * <p>So the browser chunks a file into batches of this size and posts them in sequence. Each
     * batch is well inside both limits, progress is visible, and because rows commit independently a
     * failed batch costs nothing that came before it.
     */
    public static final int BULK_IMPORT_BATCH_LIMIT = 200;

    /** Rows one spreadsheet may carry. Not a technical limit — see {@link #buildImportTemplate}. */
    public static final int BULK_IMPORT_MAX_ROWS = 2_000;

    /**
     * The dropdown sources, field configuration and limits behind an asset import template.
     *
     * <p>{@link #BULK_IMPORT_MAX_ROWS} is a judgement rather than a ceiling: with the file chunked
     * into {@link #BULK_IMPORT_BATCH_LIMIT}-row requests there is no technical reason a larger one
     * could not be processed. It exists because somebody has to read the failures — two thousand
     * error rows is already more than anyone will work through in one sitting — and because it keeps
     * the blast radius of a badly prepared file small enough to be worth redoing.
     */
    @Transactional(readOnly = true)
    public AssetImportTemplateDTO buildImportTemplate(Long assetTypeId) throws NotFoundException {
        AssetType assetType = assetTypeService.findOneAssetType(assetTypeId);

        // Resolved through the allow-list rather than by listing every status, so the dropdown can
        // only ever offer what insertSingleAssetRow will accept.
        List<String> statusNames = AssetImportStatuses.IMPORTABLE.stream()
                .map(code -> statusRepository.findByStatus(code).orElse(null))
                .filter(Objects::nonNull)
                .map(Status::getName)
                .toList();

        List<String> staff = userRepository.findAll().stream()
                .filter(u -> u.getStaffNumber() != null && !u.getStaffNumber().isBlank())
                .filter(User::isEnabled)
                .map(u -> u.getStaffNumber() + " — "
                        + ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .sorted()
                .toList();

        return AssetImportTemplateDTO.builder()
                .assetTypeId(assetType.getId())
                .assetTypeName(assetType.getName())
                .fieldConfig(assetType.getFieldConfig())
                .branches(branchRepository.findAll().stream()
                        .map(Branch::getName).filter(Objects::nonNull).sorted().toList())
                .suppliers(supplierRepository.findAll().stream()
                        .map(Supplier::getName).filter(Objects::nonNull).sorted().toList())
                .commodities(commodityRepository.findAllByAssetType_Id(assetTypeId).stream()
                        .map(Commodity::getName).filter(Objects::nonNull).distinct().sorted().toList())
                .statuses(statusNames)
                .staff(staff)
                .unitsOfMeasure(unitOfMeasureService.activeNameList())
                .batchSize(BULK_IMPORT_BATCH_LIMIT)
                .maxRows(BULK_IMPORT_MAX_ROWS)
                .build();
    }

    /**
     * Creates assets from a spreadsheet, one row at a time.
     *
     * <p>Rewritten from a version that wrapped the whole loop in a single try/catch. That shape had
     * three faults which together made a failed import impossible to recover from: the first bad row
     * abandoned every row after it while leaving the ones before it committed; the response carried
     * no counts and no row numbers, so nobody could tell what had happened or how far it got; and
     * nothing checked for duplicates, so the natural response — fix the file and upload it again —
     * duplicated every asset that had already succeeded.
     *
     * <p>Now each row is attempted and reported on its own, exactly as {@code bulkInsertUsers} does,
     * and the result names the failures by spreadsheet row so the file can be corrected and safely
     * re-uploaded.
     *
     * <p>Deliberately not {@code @Transactional}: each {@code save} runs in its own implicit
     * transaction, so a row that throws rolls back only itself.
     *
     * @param assetTypeID the category being imported into. Comes from the page rather than a column
     *                    — the assets screen is per-category, and it keeps the template narrower.
     */
    public Map<String, Object> bulkInsertAsset(List<BulkAssetDTO> assetList, Long assetTypeID)
            throws NotFoundException {
        AssetType assetType = assetTypeService.findOneAssetType(assetTypeID);
        int total = assetList == null ? 0 : assetList.size();

        if (total > BULK_IMPORT_BATCH_LIMIT) {
            throw new BadRequestException("An import batch may carry at most "
                    + BULK_IMPORT_BATCH_LIMIT + " rows; this one has " + total + ".");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        int successCount = 0;

        /*
         * Engraved numbers and serials seen earlier in this batch.
         *
         * A spreadsheet repeating a tag on rows 12 and 84 is at least as likely as one clashing with
         * the database — a copy-paste while preparing the file does it. Checking only the database
         * would let both rows through, since neither is committed when the other is validated.
         */
        Set<String> engravedInBatch = new HashSet<>();
        Set<String> serialsInBatch = new HashSet<>();

        for (int i = 0; i < total; i++) {
            BulkAssetDTO dto = assetList.get(i);
            long rowNumber = dto.getNo() != null ? dto.getNo() : (i + 1);

            try {
                insertSingleAssetRow(dto, assetType, engravedInBatch, serialsInBatch);
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", rowNumber);
                err.put("assetName", dto.getAssetName());
                err.put("engravedNumber", dto.getEngravedNumber());
                err.put("serialNumber", dto.getSerialNumber());
                err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errors.add(err);
            }
        }

        if (successCount > 0) {
            auditService.record(AuditEventType.CREATED, AuditModule.ASSETS, "ASSETS_BULK_IMPORTED",
                    "Imported " + successCount + " of " + total + " " + assetType.getName()
                            + " asset(s) from a spreadsheet"
                            + (errors.isEmpty() ? "" : " — " + errors.size() + " row(s) failed"),
                    errors.isEmpty() ? AuditSeverity.WARNING : AuditSeverity.CRITICAL);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // As with users: "success" means some row landed, not that every row was perfect. A partial
        // import is a normal outcome and the caller distinguishes it by comparing inserted to total.
        result.put("success", successCount > 0);
        result.put("total", total);
        result.put("inserted", successCount);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    /**
     * Validates and saves one spreadsheet row.
     *
     * <p>Every lookup is strict. The previous importer fell back to Head Office whenever a branch
     * name did not match, so a typo filed the asset in the wrong building and said nothing — the
     * kind of error that is only discovered during a physical count a year later. A name that does
     * not resolve is a row error naming the value that failed, and the row is skipped.
     */
    private void insertSingleAssetRow(BulkAssetDTO dto, AssetType assetType,
                                      Set<String> engravedInBatch, Set<String> serialsInBatch)
            throws NotFoundException {

        String assetName = AssetImportParsing.trimToNull(dto.getAssetName());
        String engravedNumber = AssetImportParsing.trimToNull(dto.getEngravedNumber());
        String serialNumber = AssetImportParsing.trimToNull(dto.getSerialNumber());

        if (assetName == null) throw new BadRequestException("Asset Name is required.");
        if (engravedNumber == null) throw new BadRequestException("Engraved Number is required.");

        // ── Duplicates, against this batch and against what is already recorded ──
        if (!engravedInBatch.add(engravedNumber.toLowerCase())) {
            throw new BadRequestException("Engraved Number '" + engravedNumber
                    + "' appears more than once in this file.");
        }
        // The same rule the form now uses, rather than a second spelling of it. The message it
        // raises names the asset already holding the number, which a bulk import is the place that
        // most needs - a row number and "already recorded" leaves the operator to go and find it.
        requireEngravedNumberAvailable(engravedNumber, null);
        if (serialNumber != null) {
            if (!serialsInBatch.add(serialNumber.toLowerCase())) {
                throw new BadRequestException("Serial Number '" + serialNumber
                        + "' appears more than once in this file.");
            }
            if (assetRepository.existsBySerialNumberIgnoreCase(serialNumber)) {
                throw new BadRequestException("Serial Number '" + serialNumber
                        + "' is already recorded against another asset.");
            }
        }

        // ── Placement ────────────────────────────────────────────────────────
        String locationName = AssetImportParsing.trimToNull(dto.getLocation());
        if (locationName == null) throw new BadRequestException("Location / Branch is required.");
        Branch branch = branchRepository.findByNameIgnoreCase(locationName)
                .orElseThrow(() -> new BadRequestException(
                        "Location / Branch '" + locationName + "' was not found."));

        User holder = resolveImportHolder(dto.getAssignedTo());
        // The holder's branch wins: an asset is where the person holding it is, and a register
        // exported from an older system routinely disagrees with itself on this.
        if (holder != null && holder.getBranch() != null) {
            branch = holder.getBranch();
        }

        /*
         * The same branch check createAsset applies, on the path that creates far more.
         *
         * Checked here rather than once for the batch because the branch is a property of each row —
         * a spreadsheet can name a different location on every line — and because it lands as a row
         * error like any other. A single out-of-reach row is reported beside the duplicates and the
         * unknown suppliers, and the rest of the import still lands; failing the whole batch for one
         * bad line would be a worse answer to the same question.
         */
        if (!accessScopeService.canManage(pride.bank.erp.access.ScopeSubject.ASSETS,
                branch != null ? branch.getId() : null, null)) {
            // Named, like every other import error, rather than left as a generic refusal. A
            // spreadsheet of two hundred rows is exactly where "you can only change records at your
            // own branch" is least useful — the importer has to say which row and which branch, or
            // the person is left comparing the file against their own permissions line by line.
            throw new BadRequestException(
                    "You cannot import assets for '"
                            + (branch != null ? branch.getName() : "no branch")
                            + "'. You may only import into your own branch unless your unit grants "
                            + "cross-branch asset management.");
        }

        Status status = resolveImportStatus(dto.getStatus());

        // ── Classification ───────────────────────────────────────────────────
        Supplier supplier = null;
        String supplierName = AssetImportParsing.trimToNull(dto.getSupplier());
        if (supplierName != null) {
            supplier = supplierRepository.findByNameIgnoreCase(supplierName)
                    .orElseThrow(() -> new BadRequestException(
                            "Supplier '" + supplierName + "' was not found."));
        }

        Commodity commodity = null;
        String commodityName = AssetImportParsing.trimToNull(dto.getCommodity());
        if (commodityName != null) {
            commodity = commodityRepository
                    .findByNameIgnoreCaseAndAssetType_Id(commodityName, assetType.getId())
                    .orElseThrow(() -> new BadRequestException("Commodity '" + commodityName
                            + "' was not found under " + assetType.getName() + "."));
        }

        Asset asset = Asset.builder()
                .assetName(assetName)
                .engravedNumber(engravedNumber)
                .serialNumber(serialNumber)
                .tagName(AssetImportParsing.trimToNull(dto.getTagName()))
                .assetType(assetType)
                .commodity(commodity)
                .supplier(supplier)
                .branch(branch)
                .assignedTo(holder)
                .assetStatus(status)
                .dateReceipt(AssetImportParsing.parseDate(dto.getDateReceived(), "Date Received"))
                .purchaseCost(AssetImportParsing.parseMoney(dto.getPurchaseCost(), "Purchase Cost"))
                .costOfTheAsset(AssetImportParsing.parseMoney(dto.getCostOfTheAsset(), "Cost of the Asset"))
                .netValueB(AssetImportParsing.trimToNull(dto.getNetBookValue()))
                .assetDepreciationRate(AssetImportParsing.trimToNull(dto.getDepreciationRate()))
                .unitOfMeasure(unitFor(dto.getUnitOfMeasure(), null, commodity))
                .lpoReference(lpoReferenceOf(dto.getLpoReference()))
                .make(AssetImportParsing.trimToNull(dto.getMake()))
                .model(AssetImportParsing.trimToNull(dto.getModel()))
                .description(AssetImportParsing.trimToNull(dto.getDescription()))
                .hostname(AssetImportParsing.trimToNull(dto.getHostname()))
                .ipAddress(AssetImportParsing.parseIpAddress(dto.getIpAddress()))
                .macAddress(AssetImportParsing.parseMacAddress(dto.getMacAddress()))
                .ram(AssetImportParsing.trimToNull(dto.getRam()))
                .cpuSpeed(AssetImportParsing.trimToNull(dto.getCpuSpeed()))
                .hardDiskSize(AssetImportParsing.trimToNull(dto.getHardDiskSize()))
                .interfaceType(AssetImportParsing.trimToNull(dto.getInterfaceType()))
                .disposed(false)
                .build();

        Asset saved = assetRepository.save(asset);

        /*
         * The holder gets an assignment-history row but no email.
         *
         * The history matters — it is what the asset trail and the return-after-repair flow read to
         * know who an item belongs to, and an imported asset with a holder and no history would look
         * like it had never been given to anyone. The email does not: a migration would send every
         * member of staff one "you have been assigned an asset" message per item they already have.
         */
        if (holder != null) {
            updateAssetAssignmentHistory(saved, holder);
        }
    }

    /**
     * The staff member named on an import row, by staff number or email.
     *
     * <p>Not by name: two people share one often enough that matching on it would hand assets to the
     * wrong person silently, which is worse than refusing the row.
     */
    private User resolveImportHolder(String raw) {
        String value = AssetImportParsing.trimToNull(raw);
        if (value == null) {
            return null;
        }
        // The template's dropdown emits "PBL0001 — Jane Doe"; take the identifier off the front so a
        // hand-typed staff number and a picked option both work.
        String identifier = value.contains("—") ? value.substring(0, value.indexOf('—')).trim() : value;

        return userRepository.findByStaffNumberIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new BadRequestException("Assigned To '" + value
                        + "' matched no staff number or email address."));
    }

    /**
     * The status an imported row should start in.
     *
     * <p>Blank means "Require Update", which flags the asset as needing its details completed — the
     * honest state for most of a legacy migration. A named status is honoured, but only if it is one
     * an asset can actually be in: the status table is shared with requests, stock and the approval
     * ladders, and filing a laptop as "BOM Approved" would leave it somewhere no screen can move it
     * out of.
     */
    private Status resolveImportStatus(String raw) throws NotFoundException {
        String value = AssetImportParsing.trimToNull(raw);
        if (value == null) {
            return statusService.findByStatusCode(AssetImportStatuses.DEFAULT);
        }

        // The template shows display names ("Available for Issuance"); a hand-edited file or an older
        // export may carry the code ("issuanceAvailable"). Both resolve.
        Status match = statusRepository.findByStatus(value).orElseGet(() ->
                statusRepository.findAll().stream()
                        .filter(s -> value.equalsIgnoreCase(s.getName()) || value.equalsIgnoreCase(s.getStatus()))
                        .findFirst()
                        .orElse(null));

        if (match == null) {
            throw new BadRequestException("Status '" + value + "' was not found.");
        }
        if (!AssetImportStatuses.isImportable(match.getStatus())) {
            throw new BadRequestException("Status '" + value
                    + "' is not a status an asset can be imported in.");
        }
        return match;
    }

    private void updateAssetAssignmentHistory(
            Asset asset,
            User user
    ) {
        AssetAssignmentHistory assetAssignmentHistory = AssetAssignmentHistory
                .builder()
                .asset(asset)
                .endDate(null)
                .startDate(LocalDateTime.now())
                .statusBefore(asset.getAssetStatus())
                .statusAfter(null)
                .user(user)
                .build();

        assetAssignmentHistoryRepository.save(assetAssignmentHistory);
    }

    private void addEndDateForPreviousAssignedTo(Long assetId, Long userId) {
        AssetAssignmentHistory record = assetAssignmentHistoryRepository
                .findByUserIdAndAssetId(userId, assetId).orElse(null);

        assert record != null;

        record.setEndDate(LocalDateTime.now());
        assetAssignmentHistoryRepository.save(record);
    }

    private void notifyUserAfterAssignment(User user, Asset asset) {
        String assetName = asset.getAssetName() + " " + "(" + asset.getEngravedNumber() + ")";
        email.sendEmailVerification(
                user.getEmail(),
                "Account Verification",
                user.getFirstName(),
                "Notification",
                "Asset Notification",
                assetName,
                "notification",
                null,
                null,
                null,
                null,
                null
        );
    }

}
