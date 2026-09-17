/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.stocktake.StockTakeDTOs.*;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The stock-take workflow: open a sheet, count it, submit it, approve the variances, post them.
 *
 * <p>Two properties are worth stating plainly, because they are what make the exercise trustworthy:
 *
 * <ol>
 *   <li><b>Nothing touches a balance until posting.</b> Counting and approving change no stock, so
 *       an abandoned or rejected count leaves the store exactly as it found it.</li>
 *   <li><b>Every posted variance leaves a reason-coded {@link StockAdjustment}.</b> A balance is
 *       never quietly rewritten — which is the whole point, since an unexplained adjustment is
 *       indistinguishable from the bugs this system has been carrying.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockTakeService {

    private final StockCountRepository countRepository;
    private final StockCountLineRepository lineRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StoreRepository storeRepository;
    private final StoreBalanceRepository storeBalanceRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final CurrentUserService currentUserService;
    private final StockTakePostingService postingService;

    /** The one resolver for how far a person may reach — see AccessScopeService. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    // ── Opening ─────────────────────────────────────────────────────────────

    /**
     * Opens a count sheet, snapshotting what the system currently believes for every commodity the
     * store holds. The snapshot is what variances are later measured against.
     */
    @Transactional
    /**
     * Refuses unless this store is one the caller could physically count.
     *
     * <h2>Why the store's location, and not the items' branches</h2>
     * A stock take is a physical act at a physical place — someone standing at the shelves writing
     * down what is on them. So it is scoped by where the store <em>is</em>: a Gulu officer counts
     * Gulu's Admin store, and only Head Office counts the bank-wide IT and Disposal stores, because
     * nobody in Gulu can walk into those rooms.
     *
     * <p>That is a different key from the one balance <em>visibility</em> uses, which is the item's
     * own branch — a Gulu asset awaiting repair in the Head Office IT store is Gulu's to see. Two
     * different questions ("whose item is this?" and "where am I standing?"), two different answers,
     * and the model is being honest rather than inconsistent.
     *
     * <p>Consequence, accepted deliberately: a branch cannot formally count its own items sitting
     * inside the shared IT or Disposal store. Seeing them is a report and is already answered by the
     * branch-scoped balance listing; counting them is not something a branch can actually do.
     */
    private void requireStoreCountable(Store store, pride.bank.erp.access.ScopeAction action) {
        Long locationId = store != null && store.getLocation() != null
                ? store.getLocation().getId() : null;

        if (action == pride.bank.erp.access.ScopeAction.MANAGE) {
            accessScopeService.requireCanManage(
                    pride.bank.erp.access.ScopeSubject.INVENTORY, locationId, null);
        } else {
            accessScopeService.requireCanView(
                    pride.bank.erp.access.ScopeSubject.INVENTORY, locationId, null);
        }
    }

    /** As above, for an action reached through the count rather than the store. */
    private void requireCountInScope(StockCount count, pride.bank.erp.access.ScopeAction action) {
        requireStoreCountable(count.getStore(), action);
    }

    public CountView openCount(OpenCountRequest request) throws NotFoundException {
        if (request.getStoreId() == null) {
            throw new IllegalArgumentException("A store is required to open a count");
        }

        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new NotFoundException("Store " + request.getStoreId() + " not found"));

        // Opening a count claims a set of shelves. You can only claim shelves you can reach.
        requireStoreCountable(store, pride.bank.erp.access.ScopeAction.MANAGE);

        // Two simultaneous counts of the same shelves produce two conflicting truths.
        List<StockCount> open = countRepository.findOpenForStore(store.getId());
        if (!open.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%s already has an unfinished count (%s, %s). Finish or cancel it first.",
                    store.getName(), open.get(0).getReference(), open.get(0).getStatus()));
        }

        AssetType assetType = null;
        if (request.getAssetTypeId() != null) {
            assetType = assetTypeRepository.findById(request.getAssetTypeId())
                    .orElseThrow(() -> new NotFoundException(
                            "Asset category " + request.getAssetTypeId() + " not found"));
        }

        StockCount count = countRepository.save(StockCount.builder()
                .store(store)
                .assetType(assetType)
                .status(StockCountStatus.DRAFT)
                .blind(request.getBlind() == null || request.getBlind())
                .countedBy(currentUserService.getCurrentUser())
                .notes(trimToNull(request.getNotes()))
                .build());
        count.setReference(String.format("SC/%d/%06d", LocalDateTime.now().getYear(), count.getId()));
        count = countRepository.save(count);

        List<StoreBalance> balances = storeBalanceRepository.findByStore_Id(store.getId());
        final AssetType scope = assetType;
        int created = 0;
        for (StoreBalance balance : balances) {
            if (scope != null) {
                AssetType lineType = balance.getCommodity() != null ? balance.getCommodity().getAssetType() : null;
                if (lineType == null || !lineType.getId().equals(scope.getId())) continue;
            }
            lineRepository.save(StockCountLine.builder()
                    .stockCount(count)
                    .commodity(balance.getCommodity())
                    .systemQuantityAtStart(balance.getQuantity())
                    .build());
            created++;
        }

        log.info("Opened stock count {} for store '{}' with {} line(s), blind={}",
                count.getReference(), store.getName(), created, count.isBlind());
        return toView(count, true);
    }

    // ── Counting ────────────────────────────────────────────────────────────

    /** Records counted quantities. Only the lines supplied are touched, so counting can be partial. */
    @Transactional
    public CountView saveCounts(Long countId, SaveCountsRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "record counts against", StockCountStatus.DRAFT, StockCountStatus.IN_PROGRESS);

        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("No counted lines were supplied");
        }

        for (CountedLine submitted : request.getLines()) {
            if (submitted.getCommodityId() == null) continue;

            StockCountLine line = lineRepository
                    .findByStockCount_IdAndCommodity_Id(countId, submitted.getCommodityId())
                    .orElseThrow(() -> new NotFoundException(
                            "Commodity " + submitted.getCommodityId() + " is not on this count sheet"));

            Integer counted = submitted.getCountedQuantity();
            if (counted != null && counted < 0) {
                throw new IllegalArgumentException(
                        "A counted quantity cannot be negative (" + line.getCommodity().getName() + ")");
            }

            line.setCountedQuantity(counted);
            line.setReason(submitted.getReason());
            line.setNote(trimToNull(submitted.getNote()));
            line.setCountedAt(counted == null ? null : LocalDateTime.now());
            // Counting it again is exactly what clears a recount flag.
            if (counted != null) line.setRecountRequired(false);
            lineRepository.save(line);
        }

        if (count.getStatus() == StockCountStatus.DRAFT) {
            count.setStatus(StockCountStatus.IN_PROGRESS);
            countRepository.save(count);
        }
        return toView(count, true);
    }

    // ── Submission ──────────────────────────────────────────────────────────

    /**
     * Hands the sheet over for review. Every line must have been counted — a blank is not a zero,
     * and treating it as one would write off stock nobody actually looked for.
     */
    @Transactional
    public CountView submit(Long countId) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "submit", StockCountStatus.DRAFT, StockCountStatus.IN_PROGRESS);

        List<StockCountLine> lines = lines(countId);
        List<String> uncounted = lines.stream()
                .filter(l -> l.getCountedQuantity() == null)
                .map(l -> l.getCommodity().getName())
                .toList();
        if (!uncounted.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%d line(s) have not been counted yet — a blank is not a zero. Outstanding: %s",
                    uncounted.size(), String.join(", ", uncounted.subList(0, Math.min(5, uncounted.size())))));
        }

        // A variance without a reason is just an unexplained number.
        List<String> missingReason = lines.stream()
                .filter(l -> l.getVariance() != null && l.getVariance() != 0 && l.getReason() == null)
                .map(l -> l.getCommodity().getName())
                .toList();
        if (!missingReason.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "A reason is required for every variance. Missing on: %s",
                    String.join(", ", missingReason.subList(0, Math.min(5, missingReason.size())))));
        }

        List<String> otherWithoutNote = lines.stream()
                .filter(l -> l.getReason() == AdjustmentReason.OTHER && isBlank(l.getNote()))
                .map(l -> l.getCommodity().getName())
                .toList();
        if (!otherWithoutNote.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Reason 'OTHER' needs a note explaining it. Missing on: %s",
                    String.join(", ", otherWithoutNote.subList(0, Math.min(5, otherWithoutNote.size())))));
        }

        count.setStatus(StockCountStatus.SUBMITTED);
        count.setSubmittedAt(LocalDateTime.now());
        count.setReviewRemarks(null);
        log.info("Stock count {} submitted for review", count.getReference());
        return toView(countRepository.save(count), true);
    }

    // ── Review ──────────────────────────────────────────────────────────────

    /** Accepts the variances. Still changes no stock — posting is a separate, deliberate step. */
    @Transactional
    public CountView approve(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "approve", StockCountStatus.SUBMITTED);

        User approver = currentUserService.getCurrentUser();
        if (approver != null && count.getCountedBy() != null
                && approver.getId().equals(count.getCountedBy().getId())) {
            throw new IllegalArgumentException(
                    "A count cannot be approved by the person who counted it.");
        }

        count.setStatus(StockCountStatus.APPROVED);
        count.setApprovedBy(approver);
        count.setApprovedAt(LocalDateTime.now());
        count.setReviewRemarks(trimToNull(request != null ? request.getRemarks() : null));
        log.info("Stock count {} approved", count.getReference());
        return toView(countRepository.save(count), true);
    }

    /** Sends the sheet back to be counted again. */
    @Transactional
    public CountView sendBack(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "send back", StockCountStatus.SUBMITTED);

        if (request == null || isBlank(request.getRemarks())) {
            throw new IllegalArgumentException("Say why the sheet is going back, so it can be corrected");
        }

        count.setStatus(StockCountStatus.IN_PROGRESS);
        count.setSubmittedAt(null);
        count.setReviewRemarks(request.getRemarks().trim());
        return toView(countRepository.save(count), true);
    }

    @Transactional
    public CountView cancel(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        if (count.getStatus() == StockCountStatus.POSTED) {
            throw new IllegalArgumentException("A posted count cannot be cancelled — count again instead");
        }
        count.setStatus(StockCountStatus.CANCELLED);
        count.setReviewRemarks(trimToNull(request != null ? request.getRemarks() : null));
        return toView(countRepository.save(count), true);
    }

    // ── Posting ─────────────────────────────────────────────────────────────

    /**
     * Writes the approved variances to the balances, one {@link StockAdjustment} per changed line.
     *
     * <p>Before anything is written, each line's current balance is compared with the snapshot taken
     * when the sheet was opened. If stock moved in between, the count is no longer a statement about
     * the present: {@code counted - snapshot} ignores the movement and {@code counted - current}
     * double-counts it. Rather than pick a wrong answer, those lines are flagged, the sheet goes
     * back to IN_PROGRESS, and nothing is posted.
     */
    public CountView post(Long countId) throws NotFoundException {
        StockCount toPost = find(countId);
        // The sharpest action here: this writes the counted quantities into live balances.
        requireCountInScope(toPost, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(toPost, "post", StockCountStatus.APPROVED);

        // Deliberately NOT @Transactional here. The drift check must commit the recount flags it
        // sets and only then refuse; running it inside the posting transaction would roll those
        // flags back along with the refusal, leaving the counter told "no" with no indication of
        // which lines to redo.
        List<String> drifted = postingService.flagDriftedLines(countId);
        if (!drifted.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Stock moved while this count was open (%s). Those line(s) have been flagged for "
                            + "recount and the sheet sent back. Nothing was adjusted.",
                    String.join(", ", drifted.subList(0, Math.min(5, drifted.size())))));
        }

        postingService.postApproved(countId);
        return fetch(countId);
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CountView fetch(Long countId) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.VIEW);
        return toView(count, true);
    }

    @Transactional(readOnly = true)
    public List<CountView> list(Long storeId) {
        List<StockCount> counts = (storeId != null)
                ? countRepository.findByStore_IdOrderByIdDesc(storeId)
                : countRepository.findAllByOrderByIdDesc();

        /*
         * Filtered in memory rather than in the query.
         *
         * The scope here is "stores I could physically reach", which is a property of the store's
         * location — and the repository's two finders are a store id or everything. Pushing the rule
         * into JPQL would mean a third finder and a join purely to express what one predicate says
         * plainly. Counts are opened one store at a time and closed within days, so this list is
         * tens of rows, not thousands; if it ever grows, this is the line to move into the query.
         *
         * Silent narrowing, not a refusal: asking for "the counts" with no store named should return
         * the ones you can act on, not an error.
         */
        return counts.stream()
                .filter(this::countIsInScope)
                .map(c -> toView(c, false))
                .toList();
    }

    /** Whether this count's store is one the caller could reach, without throwing. */
    private boolean countIsInScope(StockCount count) {
        Long locationId = count.getStore() != null && count.getStore().getLocation() != null
                ? count.getStore().getLocation().getId() : null;
        return accessScopeService.canView(
                pride.bank.erp.access.ScopeSubject.INVENTORY, locationId, null);
    }

    /** Adjustment history — what was corrected, by how much, and why. */
    @Transactional(readOnly = true)
    public VarianceReport varianceReport(Long storeId, LocalDateTime from, LocalDateTime to) {
        List<StockAdjustment> adjustments = adjustmentRepository.findForReport(storeId, from, to);

        List<ReasonSummary> byReason = adjustmentRepository.summariseByReason(storeId, from, to).stream()
                .map(row -> ReasonSummary.builder()
                        .reason((AdjustmentReason) row[0])
                        .lines(((Number) row[1]).longValue())
                        .netUnits(row[2] == null ? 0L : ((Number) row[2]).longValue())
                        .build())
                .toList();

        long up = adjustments.stream().filter(a -> a.getQuantityDelta() > 0)
                .mapToLong(StockAdjustment::getQuantityDelta).sum();
        long down = adjustments.stream().filter(a -> a.getQuantityDelta() < 0)
                .mapToLong(a -> -a.getQuantityDelta()).sum();

        return VarianceReport.builder()
                .byReason(byReason)
                .adjustments(adjustments.stream().map(this::toAdjustmentView).toList())
                .totalLines(adjustments.size())
                .unitsWrittenUp(up)
                .unitsWrittenDown(down)
                .build();
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private StockCount find(Long id) throws NotFoundException {
        return countRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Stock count " + id + " not found"));
    }

    private List<StockCountLine> lines(Long countId) {
        return lineRepository.findByStockCount_IdOrderByCommodity_NameAsc(countId);
    }

    private int currentQuantity(Store store, StockCountLine line) {
        return storeBalanceRepository
                .findByStore_IdAndCommodity_Id(store.getId(), line.getCommodity().getId())
                .map(StoreBalance::getQuantity)
                .orElse(0);
    }

    private void requireStatus(StockCount count, String action, StockCountStatus... allowed) {
        for (StockCountStatus status : allowed) {
            if (count.getStatus() == status) return;
        }
        throw new IllegalArgumentException(String.format(
                "Cannot %s a count that is %s.", action, count.getStatus()));
    }

    private CountView toView(StockCount count, boolean includeLines) {
        List<StockCountLine> lines = includeLines ? lines(count.getId()) : List.of();

        // A blind count withholds the system figure until the counting is finished — seeing it while
        // counting invites confirming the number instead of counting the shelf.
        boolean revealSystemQuantities = !count.isBlind()
                || count.getStatus() == StockCountStatus.SUBMITTED
                || count.getStatus() == StockCountStatus.APPROVED
                || count.getStatus() == StockCountStatus.POSTED
                || count.getStatus() == StockCountStatus.CANCELLED;

        List<CountLineView> lineViews = lines.stream()
                .map(l -> CountLineView.builder()
                        .lineId(l.getId())
                        .commodityId(l.getCommodity().getId())
                        .commodityName(l.getCommodity().getName())
                        .assetTypeName(l.getCommodity().getAssetType() != null
                                ? l.getCommodity().getAssetType().getName() : null)
                        .unit(l.getCommodity().getGroupName())
                        .systemQuantity(revealSystemQuantities ? l.getSystemQuantityAtStart() : null)
                        .countedQuantity(l.getCountedQuantity())
                        .variance(revealSystemQuantities ? l.getVariance() : null)
                        .reason(l.getReason())
                        .note(l.getNote())
                        .recountRequired(l.isRecountRequired())
                        .countedAt(l.getCountedAt())
                        .build())
                .toList();

        int counted = (int) lines.stream().filter(l -> l.getCountedQuantity() != null).count();
        int withVariance = (int) lines.stream()
                .filter(l -> l.getVariance() != null && l.getVariance() != 0).count();
        int net = lines.stream()
                .filter(l -> l.getVariance() != null)
                .mapToInt(StockCountLine::getVariance).sum();

        return CountView.builder()
                .id(count.getId())
                .reference(count.getReference())
                .storeId(count.getStore().getId())
                .storeName(count.getStore().getName())
                .assetTypeName(count.getAssetType() != null ? count.getAssetType().getName() : null)
                .status(count.getStatus())
                .blind(count.isBlind())
                .countedByName(fullName(count.getCountedBy()))
                .approvedByName(fullName(count.getApprovedBy()))
                .submittedAt(count.getSubmittedAt())
                .approvedAt(count.getApprovedAt())
                .postedAt(count.getPostedAt())
                .notes(count.getNotes())
                .reviewRemarks(count.getReviewRemarks())
                .totalLines(lines.size())
                .countedLines(counted)
                .linesWithVariance(withVariance)
                .netUnitVariance(net)
                .readyToSubmit(!lines.isEmpty() && counted == lines.size())
                .lines(lineViews)
                .build();
    }

    private AdjustmentView toAdjustmentView(StockAdjustment a) {
        return AdjustmentView.builder()
                .id(a.getId())
                .storeName(a.getStore() != null ? a.getStore().getName() : null)
                .commodityName(a.getCommodity() != null ? a.getCommodity().getName() : null)
                .quantityDelta(a.getQuantityDelta())
                .quantityBefore(a.getQuantityBefore())
                .quantityAfter(a.getQuantityAfter())
                .reason(a.getReason())
                .note(a.getNote())
                .countReference(a.getStockCount() != null ? a.getStockCount().getReference() : null)
                .postedAt(a.getPostedAt())
                .build();
    }

    private static String fullName(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? ("User " + user.getId()) : name;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
