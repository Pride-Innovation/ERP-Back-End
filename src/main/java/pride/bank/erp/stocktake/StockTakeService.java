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

import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.store.StoreType;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final AuditService auditService;
    private final StockCountApprovalNotifier approvalNotifier;

    /** The one resolver for how far a person may reach — see AccessScopeService. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    // ── Opening ─────────────────────────────────────────────────────────────

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
            if (!canActAt(store)) {
                throw new AccessDeniedException(
                        "A stock take is done by the staff at the shelves: you can open, count, approve "
                                + "and post counts only for stores at your own branch.");
            }
        } else {
            accessScopeService.requireCanView(
                    pride.bank.erp.access.ScopeSubject.INVENTORY, locationId, null);
        }
    }

    /**
     * Whether the caller may act on a count of this store: it stands at their own branch.
     *
     * <h2>Not the inventory MANAGE scope, deliberately</h2>
     * Counting is a physical act at a physical place, so acting on a count belongs to the people at
     * that branch. The general inventory reach would have let anyone holding
     * {@code MANAGE_ALL_BRANCH_INVENTORY} - the Admin and Infra units, for their stock-order work -
     * open, approve and post counts at every branch once granted the stock-take permissions. Viewing
     * still follows that reach: Head Office reads every branch's counts, and acts only at its own.
     *
     * <p>The super administrator bypasses it, as it bypasses every check.
     */
    private boolean canActAt(Store store) {
        User caller = currentUserService.getCurrentUser();
        if (isSuperAdmin(caller)) return true;
        Long own = caller != null && caller.getBranch() != null ? caller.getBranch().getId() : null;
        Long locationId = store != null && store.getLocation() != null ? store.getLocation().getId() : null;
        return own != null && own.equals(locationId);
    }

    private static boolean isSuperAdmin(User user) {
        if (user == null) return false;
        java.util.stream.Stream<pride.bank.erp.role.Role> roles = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(user.getTitle() != null ? user.getTitle().getRole() : null),
                java.util.stream.Stream.concat(
                        user.getAdditionalRoles() != null ? user.getAdditionalRoles().stream() : java.util.stream.Stream.empty(),
                        user.getUnit() != null && user.getUnit().getRoles() != null
                                ? user.getUnit().getRoles().stream() : java.util.stream.Stream.empty()));
        return roles.anyMatch(r -> r != null && Constants.SUPER_ADMIN.equals(r.getName()));
    }

    /**
     * The stores the caller may open a count for: at their own branch, not a courier, and holding at
     * least one stock line - the IT and Disposal stores hold only serialized assets, which a stock take
     * does not count, so a sheet for them would be empty.
     */
    @Transactional(readOnly = true)
    public List<CountableStore> countableStores() {
        User caller = currentUserService.getCurrentUser();
        List<Store> candidates;
        if (isSuperAdmin(caller)) {
            candidates = storeRepository.findAll();
        } else if (caller != null && caller.getBranch() != null) {
            candidates = storeRepository.findByLocation_Id(caller.getBranch().getId());
        } else {
            candidates = List.of();
        }
        return candidates.stream()
                .filter(s -> s.getStoreType() != StoreType.COURIER)
                .map(s -> CountableStore.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .storeType(s.getStoreType() != null ? s.getStoreType().name() : null)
                        .branchName(s.getLocation() != null ? s.getLocation().getName() : null)
                        .lineCount(storeBalanceRepository.findByStore_Id(s.getId()).size())
                        .build())
                .filter(s -> s.getLineCount() > 0)
                .sorted(java.util.Comparator.comparing(CountableStore::getName, java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** As above, for an action reached through the count rather than the store. */
    private void requireCountInScope(StockCount count, pride.bank.erp.access.ScopeAction action) {
        requireStoreCountable(count.getStore(), action);
    }

    /**
     * Opens a count sheet, snapshotting what the system currently believes for every commodity the
     * store holds. The snapshot is what variances are later measured against.
     *
     * <p>Transactional so the count and its lines land together. The annotation used to sit above
     * the private scope helper instead, where Spring ignores it, so a failure part-way through the
     * snapshot left a count holding only some of its lines.
     */
    @Transactional
    public CountView openCount(OpenCountRequest request) throws NotFoundException {
        if (request.getStoreId() == null) {
            throw new IllegalArgumentException("A store is required to open a count");
        }

        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new NotFoundException("Store " + request.getStoreId() + " not found"));

        // Opening a count claims a set of shelves. You can only claim shelves you can reach.
        requireStoreCountable(store, pride.bank.erp.access.ScopeAction.MANAGE);

        // A courier "store" is stock on the road: there is no shelf to stand at, and the movement
        // that carries it settles the balance on receipt.
        if (store.getStoreType() == StoreType.COURIER) {
            throw new IllegalArgumentException(
                    store.getName() + " is a courier holding stock in transit and cannot be counted.");
        }

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

        // The lines this sheet will hold, decided before anything is saved.
        final AssetType scope = assetType;
        List<StoreBalance> toCount = storeBalanceRepository.findByStore_Id(store.getId()).stream()
                .filter(balance -> {
                    if (scope == null) return true;
                    AssetType lineType = balance.getCommodity() != null ? balance.getCommodity().getAssetType() : null;
                    return lineType != null && lineType.getId().equals(scope.getId());
                })
                .toList();

        // An empty sheet counts nothing and would sit in the store's one open slot. Refused before the
        // count is created, so nothing is left behind - not even inside a caller's transaction.
        if (toCount.isEmpty()) {
            throw new IllegalArgumentException(store.getName() + " holds no stock lines"
                    + (assetType != null ? " in " + assetType.getName() : "")
                    + " to count. A stock take counts consumable stock; serialized assets such as "
                    + "laptops are not counted here.");
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

        int created = 0;
        for (StoreBalance balance : toCount) {
            lineRepository.save(StockCountLine.builder()
                    .stockCount(count)
                    .commodity(balance.getCommodity())
                    .systemQuantityAtStart(balance.getQuantity())
                    .build());
            created++;
        }

        log.info("Opened stock count {} for store '{}' with {} line(s), blind={}",
                count.getReference(), store.getName(), created, count.isBlind());
        audit(AuditEventType.CREATED, "STOCK_COUNT_OPENED", AuditSeverity.INFO, count,
                String.format("Opened stock count %s for %s (%d line(s))",
                        count.getReference(), store.getName(), created));
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
        // The screen blocked an empty sheet; the server did not, so one could be submitted, approved
        // and "posted" having counted nothing.
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("This count sheet has no lines, so there is nothing to submit.");
        }
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
        audit(AuditEventType.UPDATED, "STOCK_COUNT_SUBMITTED", AuditSeverity.INFO, count,
                String.format("Submitted stock count %s for review", count.getReference()));
        CountView view = toView(countRepository.save(count), true);
        // Sent once the submission commits; see the notifier for who is told and why.
        approvalNotifier.onSubmitted(count,
                new StockCountApprovalNotifier.CountViewSummary(view.getTotalLines(), view.getLinesWithVariance()),
                currentUserService.getCurrentUser());
        return view;
    }

    // ── Review ──────────────────────────────────────────────────────────────

    /** Accepts the variances. Still changes no stock — posting is a separate, deliberate step. */
    @Transactional
    public CountView approve(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "approve", StockCountStatus.SUBMITTED);

        User approver = currentUserService.getCurrentUser();
        requireNotTheCreator(count, approver);

        count.setStatus(StockCountStatus.APPROVED);
        count.setApprovedBy(approver);
        count.setApprovedAt(LocalDateTime.now());
        count.setReviewRemarks(trimToNull(request != null ? request.getRemarks() : null));
        log.info("Stock count {} approved", count.getReference());
        audit(AuditEventType.APPROVED, "STOCK_COUNT_APPROVED", AuditSeverity.INFO, count,
                String.format("Approved the variances on stock count %s", count.getReference()));
        return toView(countRepository.save(count), true);
    }

    /**
     * Maker-checker: whoever opened the count may not decide it, whatever they hold.
     *
     * <p>{@code countedBy} is the person who opened the sheet, set once and never moved. The decision
     * belongs to <b>any other</b> holder of APPROVE_STOCK_TAKE at the branch - deliberately not also
     * excluding whoever entered figures or submitted. A branch is staffed with two of them (BM and BOM);
     * if one opens and the other counts, a wider rule would leave nobody able to approve.
     *
     * <p>Applies to approving and to sending back alike, since both are the reviewer's decision. There
     * is no exemption, the super administrator included.
     */
    private static void requireNotTheCreator(StockCount count, User reviewer) {
        if (reviewer != null && count.getCountedBy() != null
                && reviewer.getId().equals(count.getCountedBy().getId())) {
            throw new IllegalArgumentException(
                    "A count cannot be approved or sent back by the person who opened it. "
                            + "Any other approver at this branch can review it.");
        }
    }

    /** Sends the sheet back to be counted again. */
    @Transactional
    public CountView sendBack(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "send back", StockCountStatus.SUBMITTED);
        requireNotTheCreator(count, currentUserService.getCurrentUser());

        if (request == null || isBlank(request.getRemarks())) {
            throw new IllegalArgumentException("Say why the sheet is going back, so it can be corrected");
        }

        count.setStatus(StockCountStatus.IN_PROGRESS);
        count.setSubmittedAt(null);
        count.setReviewRemarks(request.getRemarks().trim());
        audit(AuditEventType.REJECTED, "STOCK_COUNT_SENT_BACK", AuditSeverity.INFO, count,
                String.format("Sent stock count %s back for recount: %s",
                        count.getReference(), count.getReviewRemarks()));
        return toView(countRepository.save(count), true);
    }

    /**
     * Abandons a count that has not been posted. Nothing was ever applied, so nothing is undone.
     *
     * <h2>Two ways in</h2>
     * An approver may cancel anything short of POSTED. The counter may cancel <b>their own</b> count
     * while it is still being counted (DRAFT or IN_PROGRESS) — without that, a count opened by mistake
     * or left half-done held its store's one open slot until an approver happened to notice, and no
     * screen offered a way out at all. Once submitted, the sheet is the approver's to decide.
     *
     * <p>A reason is required: a cancelled count is a gap in the store's history, and the trail
     * should say why.
     */
    @Transactional
    public CountView cancel(Long countId, ReviewRequest request) throws NotFoundException {
        StockCount count = find(countId);
        requireCountInScope(count, pride.bank.erp.access.ScopeAction.MANAGE);
        requireStatus(count, "cancel", StockCountStatus.DRAFT, StockCountStatus.IN_PROGRESS,
                StockCountStatus.SUBMITTED, StockCountStatus.APPROVED);

        User caller = currentUserService.getCurrentUser();
        if (!holds(caller, Constants.APPROVE_STOCK_TAKE)) {
            boolean ownCount = caller != null && count.getCountedBy() != null
                    && caller.getId().equals(count.getCountedBy().getId());
            boolean stillCounting = count.getStatus() == StockCountStatus.DRAFT
                    || count.getStatus() == StockCountStatus.IN_PROGRESS;
            if (!ownCount || !stillCounting) {
                throw new AccessDeniedException(
                        "Only an approver can cancel this count. A counter may cancel their own count "
                                + "only while it is still being counted.");
            }
        }

        if (request == null || isBlank(request.getRemarks())) {
            throw new IllegalArgumentException("Say why the count is being cancelled");
        }

        count.setStatus(StockCountStatus.CANCELLED);
        count.setReviewRemarks(request.getRemarks().trim());
        audit(AuditEventType.DELETED, "STOCK_COUNT_CANCELLED", AuditSeverity.WARNING, count,
                String.format("Cancelled stock count %s: %s", count.getReference(), count.getReviewRemarks()));
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
        return storeIsInScope(count.getStore());
    }

    private boolean storeIsInScope(Store store) {
        Long locationId = store != null && store.getLocation() != null ? store.getLocation().getId() : null;
        return accessScopeService.canView(
                pride.bank.erp.access.ScopeSubject.INVENTORY, locationId, null);
    }

    /**
     * Adjustment history — what was corrected, by how much, and why.
     *
     * <h2>Scoped by the same rule as the count listing</h2>
     * It was not scoped at all: any holder of READ_STOCK_TAKE read every store's corrections in the
     * bank, and the headline totals beside them were bank-wide too, on a page whose count list was
     * already narrowed to the caller's stores. Narrowed silently when no store is named; refused when
     * one is named that the caller cannot reach, as the listing of a named store would be.
     */
    @Transactional(readOnly = true)
    public VarianceReport varianceReport(Long storeId, LocalDateTime from, LocalDateTime to) {
        if (storeId != null) {
            storeRepository.findById(storeId)
                    .ifPresent(store -> requireStoreCountable(store, pride.bank.erp.access.ScopeAction.VIEW));
        }

        List<StockAdjustment> adjustments = adjustmentRepository.findForReport(storeId, from, to).stream()
                .filter(a -> storeIsInScope(a.getStore()))
                .toList();

        // From the scoped list, not a separate aggregate — which would total stores the list withholds.
        Map<AdjustmentReason, long[]> tally = new LinkedHashMap<>();
        for (StockAdjustment a : adjustments) {
            long[] t = tally.computeIfAbsent(a.getReason(), r -> new long[2]);
            t[0]++;
            t[1] += a.getQuantityDelta();
        }
        List<ReasonSummary> byReason = tally.entrySet().stream()
                .map(e -> ReasonSummary.builder()
                        .reason(e.getKey()).lines(e.getValue()[0]).netUnits(e.getValue()[1]).build())
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
                .countedById(count.getCountedBy() != null ? count.getCountedBy().getId() : null)
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
                .storeBranchName(count.getStore().getLocation() != null ? count.getStore().getLocation().getName() : null)
                .actionable(canActAt(count.getStore()))
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

    private static boolean holds(User user, String authority) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** Stock takes in the audit trail; the ledger records the stock, this records who did what. */
    private void audit(AuditEventType type, String action, AuditSeverity severity, StockCount count, String text) {
        auditService.record(type, AuditModule.INVENTORY, action, text, severity, "STOCK_COUNT", count.getId());
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
