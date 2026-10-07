/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeDTO;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which branches may stock what.
 *
 * <p>The rule lives on the <b>category</b>; a <b>commodity</b> may override it. A commodity with no
 * category is Head Office only - the conservative answer for something nobody has classified.
 *
 * <p>Enforced where an order line comes into being - {@code StockService.createStock} and lines
 * <em>added</em> by {@code updateStock}. Receiving against an order already raised is deliberately
 * not checked: the goods were ordered under yesterday's rule and are arriving at the door either
 * way, and refusing the receipt would leave them physically present and recorded nowhere.
 */
@Service
@RequiredArgsConstructor
public class StockingRuleService {

    private final BranchRepository branchRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetTypeService assetTypeService;
    private final CommodityRepository commodityRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    /** Head Office only, for a commodity with no category. */
    private static final StockingRule HEAD_OFFICE_ONLY = new StockingRule() {
        public boolean isStockAtHeadOffice() { return true; }
        public boolean isStockAtAllBranches() { return false; }
        public Set<Branch> getStockingBranches() { return Set.of(); }
    };

    // ── Evaluating ───────────────────────────────────────────────────────────────────────────────

    /** The rule a commodity actually follows: its own when it overrides, else its category's. */
    public StockingRule effectiveRule(Commodity commodity) {
        if (commodity.isStockingOverride()) return commodity;
        return commodity.getAssetType() != null ? commodity.getAssetType() : HEAD_OFFICE_ONLY;
    }

    /**
     * Whether {@code rule} admits {@code branch}. Head Office answers to its own switch only - it is
     * never one of "all branches", and a branch list naming it means nothing.
     */
    public static boolean allows(StockingRule rule, Branch branch) {
        if (rule == null || branch == null) return false;
        if (branch.isHeadOffice()) return rule.isStockAtHeadOffice();
        if (rule.isStockAtAllBranches()) return true;
        Set<Branch> named = rule.getStockingBranches();
        return named != null && named.stream().anyMatch(b -> Objects.equals(b.getId(), branch.getId()));
    }

    public boolean allows(Commodity commodity, Branch branch) {
        return allows(effectiveRule(commodity), branch);
    }

    /**
     * Refuses when any of these commodities may not be stocked at {@code branch}, naming every one at
     * once so the stocker fixes the order in one pass rather than discovering the lines one at a time.
     */
    public void requireStockable(Collection<Commodity> commodities, Branch branch) {
        List<String> refused = new ArrayList<>();
        for (Commodity commodity : commodities) {
            if (commodity != null && !allows(commodity, branch)) {
                refused.add(commodity.getName());
            }
        }
        if (refused.isEmpty()) return;

        String where = branch == null ? "an unknown branch" : branch.getName();
        throw new IllegalArgumentException(String.format(
                "%s cannot be stocked at %s. Where each category may be stocked is set in "
                        + "Settings → Asset Categories.",
                String.join(", ", refused), where));
    }

    /** The branch the caller stocks into: their duty station, or Head Office when they have none. */
    public Branch stockingBranchForCurrentUser() throws NotFoundException {
        try {
            User current = currentUserService.getCurrentUser();
            if (current != null && current.getBranch() != null && current.getBranch().getId() != null) {
                return current.getBranch();
            }
        } catch (Exception ignored) {
            // No authenticated user - fall through to Head Office.
        }
        return headOffice();
    }

    /** Found by its flag, never by id or name - ids depend on seed order, names on Settings. */
    public Branch headOffice() throws NotFoundException {
        return branchRepository.findFirstByIsHeadOfficeTrue()
                .orElseThrow(() -> new NotFoundException("Head office branch not found."));
    }

    /**
     * What the caller's branch may stock. A category is listed when its own rule admits the branch
     * or when any of its commodities does through an override.
     */
    @Transactional(readOnly = true)
    public StockableDTO stockableForCurrentUser() throws NotFoundException {
        return stockableFor(null);
    }

    /** As {@link #stockableForCurrentUser}, for a named branch - the one an order being corrected belongs to. */
    @Transactional(readOnly = true)
    public StockableDTO stockableFor(Long branchId) throws NotFoundException {
        Branch branch = branchId == null ? stockingBranchForCurrentUser()
                : branchRepository.findById(branchId)
                        .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found."));

        Set<Long> categoryIds = new TreeSet<>();
        for (AssetType type : assetTypeRepository.findAll()) {
            if (allows(type, branch)) categoryIds.add(type.getId());
        }
        Set<Long> commodityIds = new TreeSet<>();
        for (Commodity commodity : commodityRepository.findAll()) {
            if (!allows(commodity, branch)) continue;
            commodityIds.add(commodity.getId());
            if (commodity.getAssetType() != null) categoryIds.add(commodity.getAssetType().getId());
        }

        return StockableDTO.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .headOffice(branch.isHeadOffice())
                .assetTypeIds(new ArrayList<>(categoryIds))
                .commodityIds(new ArrayList<>(commodityIds))
                .build();
    }

    // ── Changing ─────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public AssetTypeDTO updateCategoryRule(Long assetTypeId, StockingRuleDTO request) throws NotFoundException {
        AssetType type = assetTypeRepository.findById(assetTypeId)
                .orElseThrow(() -> new NotFoundException("Asset category " + assetTypeId + " not found."));

        Set<Branch> branches = resolveBranches(request);
        String before = describe(type);
        type.setStockAtHeadOffice(request.isStockAtHeadOffice());
        type.setStockAtAllBranches(request.isStockAtAllBranches());
        type.getStockingBranches().clear();
        if (!request.isStockAtAllBranches()) type.getStockingBranches().addAll(branches);
        AssetType saved = assetTypeRepository.save(type);

        auditService.record(AuditEventType.UPDATED, AuditModule.INVENTORY, "STOCKING_RULE_CHANGED",
                "Category " + saved.getName() + ": stocking changed from [" + before + "] to ["
                        + describe(saved) + "]",
                AuditSeverity.WARNING, "AssetType", saved.getId());
        return assetTypeService.toDTO(saved);
    }

    @Transactional
    public Commodity updateCommodityRule(Long commodityId, StockingRuleDTO request) throws NotFoundException {
        Commodity commodity = commodityRepository.findById(commodityId)
                .orElseThrow(() -> new NotFoundException("Commodity " + commodityId + " not found."));

        String before = commodity.isStockingOverride() ? describe(commodity) : "same as category";
        boolean override = Boolean.TRUE.equals(request.getOverride());
        commodity.setStockingOverride(override);
        commodity.getStockingBranches().clear();
        if (override) {
            Set<Branch> branches = resolveBranches(request);
            commodity.setStockAtHeadOffice(request.isStockAtHeadOffice());
            commodity.setStockAtAllBranches(request.isStockAtAllBranches());
            if (!request.isStockAtAllBranches()) commodity.getStockingBranches().addAll(branches);
        } else {
            // Back to the column defaults, so a later override starts from a clean slate rather than
            // from a rule somebody switched off months ago.
            commodity.setStockAtHeadOffice(true);
            commodity.setStockAtAllBranches(false);
        }
        Commodity saved = commodityRepository.save(commodity);

        String after = override ? describe(saved) : "same as category";
        auditService.record(AuditEventType.UPDATED, AuditModule.INVENTORY, "STOCKING_RULE_CHANGED",
                "Commodity " + saved.getName() + ": stocking changed from [" + before + "] to [" + after + "]",
                AuditSeverity.WARNING, "Commodity", saved.getId());
        return saved;
    }

    /**
     * The named branches, refusing any that do not exist and Head Office - which has its own switch,
     * and naming it in a list would read as granting it while doing nothing.
     */
    private Set<Branch> resolveBranches(StockingRuleDTO request) {
        Set<Long> ids = new LinkedHashSet<>(request.getBranchIds() == null ? List.of() : request.getBranchIds());
        ids.remove(null);
        if (ids.isEmpty() || request.isStockAtAllBranches()) return new HashSet<>();

        List<Branch> found = branchRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            Set<Long> missing = new TreeSet<>(ids);
            found.forEach(b -> missing.remove(b.getId()));
            throw new IllegalArgumentException("No such branch: " + missing);
        }
        if (found.stream().anyMatch(Branch::isHeadOffice)) {
            throw new IllegalArgumentException(
                    "Head Office is set by its own switch, not in the list of branches.");
        }
        return new HashSet<>(found);
    }

    /** "Head Office, all branches" / "Gulu Branch, Mbarara Branch" / "nowhere" - for the audit line. */
    static String describe(StockingRule rule) {
        List<String> parts = new ArrayList<>();
        if (rule.isStockAtHeadOffice()) parts.add("Head Office");
        if (rule.isStockAtAllBranches()) {
            parts.add("all branches");
        } else if (rule.getStockingBranches() != null) {
            rule.getStockingBranches().stream().map(Branch::getName).filter(Objects::nonNull)
                    .sorted().forEach(parts::add);
        }
        return parts.isEmpty() ? "nowhere" : String.join(", ", parts);
    }
}
