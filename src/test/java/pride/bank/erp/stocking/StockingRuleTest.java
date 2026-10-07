package pride.bank.erp.stocking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeController;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityController;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockService;
import pride.bank.erp.stockCommodity.StockCommodityDTO;
import pride.bank.erp.supplier.SupplierRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which branches may stock what - configured per category in Settings, overridable per commodity.
 *
 * <h2>What was wrong</h2>
 * Nothing restricted it: every category could be stocked by every branch, and the stock form offered
 * all twelve to everyone. The rule now lives on the category, a commodity may override it, and it is
 * enforced where an order line comes into being - never on a receipt against an order already raised.
 *
 * <p>Every test runs in a rolled-back transaction and sets the rules it relies on itself, so it does not
 * depend on how an administrator has configured the live categories.
 */
@SpringBootTest
@Transactional
class StockingRuleTest {

    @Autowired StockingRuleService stockingRuleService;
    @Autowired StockService stockService;
    @Autowired AssetTypeRepository assetTypeRepository;
    @Autowired CommodityRepository commodityRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void clearPrincipal() {
        RequestContextHolder.resetRequestAttributes();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ── The rule itself ──────────────────────────────────────────────────────

    /** Head Office answers to its own switch; "all branches" never includes it. */
    @Test
    void headOfficeIsNeverOneOfAllBranches() throws Exception {
        Branch ho = stockingRuleService.headOffice();
        Branch branch = aBranch();

        AssetType type = category();
        type.setStockAtHeadOffice(false);
        type.setStockAtAllBranches(true);

        assertThat(StockingRuleService.allows(type, ho)).isFalse();
        assertThat(StockingRuleService.allows(type, branch)).isTrue();
    }

    /** "All branches" covers a branch nobody named - including one created after the rule was set. */
    @Test
    void allBranchesCoversABranchNotNamed() {
        Branch tomorrow = new Branch();
        tomorrow.setId(Long.MAX_VALUE);
        tomorrow.setName("Opened Tomorrow");
        AssetType type = category();
        type.setStockAtAllBranches(true);
        type.getStockingBranches().clear();

        assertThat(StockingRuleService.allows(type, tomorrow)).isTrue();
    }

    /** With "all branches" off, only the named ones. */
    @Test
    void namedBranchesOnly() {
        List<Branch> two = nonHeadOfficeBranches(2);
        AssetType type = category();
        type.setStockAtAllBranches(false);
        type.getStockingBranches().clear();
        type.getStockingBranches().add(two.get(0));

        assertThat(StockingRuleService.allows(type, two.get(0))).isTrue();
        assertThat(StockingRuleService.allows(type, two.get(1))).isFalse();
    }

    /** A commodity's own rule wins only while it overrides; switched off, it follows its category. */
    @Test
    void anOverrideWinsAndCanBeHandedBack() throws Exception {
        Branch branch = aBranch();
        Commodity commodity = categorisedCommodity();
        stockingRuleService.updateCategoryRule(commodity.getAssetType().getId(), headOfficeOnly());

        assertThat(stockingRuleService.allows(commodity, branch)).isFalse();

        stockingRuleService.updateCommodityRule(commodity.getId(),
                StockingRuleDTO.builder().override(true).stockAtHeadOffice(true)
                        .branchIds(List.of(branch.getId())).build());
        assertThat(stockingRuleService.allows(commodity, branch)).isTrue();

        stockingRuleService.updateCommodityRule(commodity.getId(),
                StockingRuleDTO.builder().override(false).build());
        assertThat(stockingRuleService.allows(commodity, branch)).isFalse();
        assertThat(commodity.getStockingBranches()).isEmpty();
    }

    /** Nobody classified it, so it stays where everything used to be stocked. */
    @Test
    void anUncategorisedCommodityIsHeadOfficeOnly() throws Exception {
        Commodity loose = Commodity.builder().name("Unclassified").groupName("pieces").build();
        assertThat(stockingRuleService.allows(loose, stockingRuleService.headOffice())).isTrue();
        assertThat(stockingRuleService.allows(loose, aBranch())).isFalse();
    }

    /** Head Office has its own switch; naming it in the list would read as a grant and do nothing. */
    @Test
    void headOfficeCannotBeNamedAsABranch() throws Exception {
        Long typeId = category().getId();
        Long hoId = stockingRuleService.headOffice().getId();
        assertThatThrownBy(() -> stockingRuleService.updateCategoryRule(typeId,
                StockingRuleDTO.builder().branchIds(List.of(hoId)).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own switch");
        assertThatThrownBy(() -> stockingRuleService.updateCategoryRule(typeId,
                StockingRuleDTO.builder().branchIds(List.of(-1L)).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such branch");
    }

    // ── Enforcement ──────────────────────────────────────────────────────────

    /** A branch raising an order for a category kept at Head Office is refused, and nothing is saved. */
    @Test
    void aBranchCannotRaiseAnOrderOutsideItsRule() throws Exception {
        User stocker = branchUser();
        Commodity commodity = categorisedCommodity();
        stockingRuleService.updateCategoryRule(commodity.getAssetType().getId(), headOfficeOnly());
        signInAs(stocker);

        long before = countStocks();
        assertThatThrownBy(() -> stockService.createStock(order(), supplierId(),
                List.of(line(commodity)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(commodity.getName())
                .hasMessageContaining("cannot be stocked at " + stocker.getBranch().getName());
        assertThat(countStocks()).isEqualTo(before);
    }

    /** The same order goes through once the branch is admitted. */
    @Test
    void anAdmittedBranchRaisesTheOrder() throws Exception {
        User stocker = branchUser();
        Commodity commodity = categorisedCommodity();
        stockingRuleService.updateCategoryRule(commodity.getAssetType().getId(),
                StockingRuleDTO.builder().stockAtHeadOffice(true)
                        .branchIds(List.of(stocker.getBranch().getId())).build());
        signInAs(stocker);

        Stock stock = stockService.createStock(order(), supplierId(), List.of(line(commodity)), null);
        assertThat(stock.getBranch().getId()).isEqualTo(stocker.getBranch().getId());
    }

    /**
     * Tightening a rule must not freeze orders raised under the old one: their existing lines can still
     * be corrected. Only a line the correction <em>adds</em> answers to today's rule.
     */
    @Test
    void aCorrectionChecksOnlyTheLinesItAdds() throws Exception {
        User stocker = branchUser();
        Commodity kept = categorisedCommodity();
        Commodity added = anotherCategoryCommodity(kept.getAssetType());
        stockingRuleService.updateCategoryRule(kept.getAssetType().getId(),
                StockingRuleDTO.builder().stockAtHeadOffice(true)
                        .branchIds(List.of(stocker.getBranch().getId())).build());
        stockingRuleService.updateCategoryRule(added.getAssetType().getId(), headOfficeOnly());
        signInAs(stocker);
        Stock stock = stockService.createStock(order(), supplierId(), List.of(line(kept)), null);

        // The rule is tightened after the order was raised...
        stockingRuleService.updateCategoryRule(kept.getAssetType().getId(), headOfficeOnly());

        // ...and correcting its existing line still works,
        StockCommodityDTO bigger = line(kept);
        bigger.setOrderedQuantity(5);
        stockService.updateStock(stock.getId(), order(), null, List.of(bigger));

        // while adding a line the branch may not stock is refused.
        assertThatThrownBy(() -> stockService.updateStock(stock.getId(), order(), null,
                List.of(bigger, line(added))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(added.getName());
    }

    /** Exactly two enforcement points: creation, and lines a correction adds. Receipts are never checked. */
    @Test
    void enforcedOnlyWhereALineComesIntoBeing() throws Exception {
        String source = codeOnly(Files.readString(Path.of("src/main/java/pride/bank/erp/stock/StockService.java")));
        assertThat(source.split("stockingRuleService\\.requireStockable\\(", -1)).hasSize(3);
        assertThat(source).doesNotContain("HEAD_OFFICE_BRANCH_ID");
    }

    // ── What the form is offered ─────────────────────────────────────────────

    /** An override lists the category for the branch, offering only the overriding commodity. */
    @Test
    void theFormIsOfferedWhatTheBranchMayStock() throws Exception {
        User stocker = branchUser();
        Commodity toner = categorisedCommodity();
        Commodity sibling = anotherInSameCategory(toner);
        stockingRuleService.updateCategoryRule(toner.getAssetType().getId(), headOfficeOnly());
        stockingRuleService.updateCommodityRule(toner.getId(), StockingRuleDTO.builder().override(true)
                .branchIds(List.of(stocker.getBranch().getId())).build());
        signInAs(stocker);

        StockableDTO stockable = stockingRuleService.stockableForCurrentUser();
        assertThat(stockable.getBranchId()).isEqualTo(stocker.getBranch().getId());
        assertThat(stockable.getAssetTypeIds()).contains(toner.getAssetType().getId());
        assertThat(stockable.getCommodityIds()).contains(toner.getId());
        if (sibling != null) assertThat(stockable.getCommodityIds()).doesNotContain(sibling.getId());
    }

    /**
     * The correction page asks about the order's branch, not the corrector's - Head Office correcting a
     * branch order is checked against the branch's rule, so it must be filtered by it too.
     */
    @Test
    void aNamedBranchIsAnsweredForThatBranch() throws Exception {
        Branch branch = aBranch();
        Commodity commodity = categorisedCommodity();
        stockingRuleService.updateCategoryRule(commodity.getAssetType().getId(), headOfficeOnly());

        StockableDTO forBranch = stockingRuleService.stockableFor(branch.getId());
        StockableDTO forHeadOffice = stockingRuleService.stockableFor(stockingRuleService.headOffice().getId());

        assertThat(forBranch.getBranchId()).isEqualTo(branch.getId());
        assertThat(forBranch.getCommodityIds()).doesNotContain(commodity.getId());
        assertThat(forHeadOffice.isHeadOffice()).isTrue();
        assertThat(forHeadOffice.getCommodityIds()).contains(commodity.getId());
    }

    // ── Who may change it ────────────────────────────────────────────────────

    /**
     * Every write to a category or commodity, the rule included, answers to the Settings key for its verb
     * - the same keys the Settings pages already gate their buttons on, so a button shown is a call admitted.
     */
    @Test
    void categoryAndCommodityWritesAreSettings() {
        int writes = 0;
        for (Class<?> c : List.of(AssetTypeController.class, CommodityController.class, StockingRuleController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                String expected = m.isAnnotationPresent(PostMapping.class) ? Constants.CREATE_SETTING
                        : m.isAnnotationPresent(PutMapping.class) ? Constants.UPDATE_SETTING
                        : m.isAnnotationPresent(DeleteMapping.class) ? Constants.DELETE_SETTING
                        : null;
                if (expected == null) continue;
                writes++;
                PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
                assertThat(guard).as(m.toString()).isNotNull();
                assertThat(guard.value()).as(m.toString()).contains(expected);
            }
        }
        assertThat(writes).isEqualTo(10);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static StockingRuleDTO headOfficeOnly() {
        return StockingRuleDTO.builder().stockAtHeadOffice(true).stockAtAllBranches(false).build();
    }

    private AssetType category() {
        return assetTypeRepository.findAll().get(0);
    }

    private Branch aBranch() {
        return nonHeadOfficeBranches(1).get(0);
    }

    private List<Branch> nonHeadOfficeBranches(int n) {
        List<Branch> found = branchRepository.findAll().stream().filter(b -> !b.isHeadOffice()).limit(n).toList();
        assertThat(found).hasSize(n);
        return found;
    }

    private User branchUser() {
        return userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && !u.getBranch().isHeadOffice())
                .filter(u -> u.getAuthorities().stream()
                        .anyMatch(a -> Constants.CREATE_INVENTORY.equals(a.getAuthority())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no branch user holds CREATE_INVENTORY"));
    }

    private Commodity categorisedCommodity() {
        return commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && !c.getAssetType().isTracksAssets())
                .findFirst().orElseThrow();
    }

    private Commodity anotherCategoryCommodity(AssetType not) {
        return commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && !c.getAssetType().isTracksAssets()
                        && !c.getAssetType().getId().equals(not.getId()))
                .findFirst().orElseThrow();
    }

    private Commodity anotherInSameCategory(Commodity of) {
        return commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && c.getAssetType().getId().equals(of.getAssetType().getId())
                        && !c.getId().equals(of.getId()))
                .findFirst().orElse(null);
    }

    private Long supplierId() {
        return supplierRepository.findAll().get(0).getId();
    }

    private Stock order() {
        String ref = "STK-RULE-" + UUID.randomUUID().toString().substring(0, 8);
        return Stock.builder().name(ref).referenceNumber(ref).lpoNumber(ref)
                .totalCost(BigDecimal.TEN).balanceCost(BigDecimal.ZERO).build();
    }

    private static StockCommodityDTO line(Commodity commodity) {
        return new StockCommodityDTO(2, 0, BigDecimal.ONE, BigDecimal.ONE, commodity.getId());
    }

    @Autowired pride.bank.erp.stock.StockRepository stockRepository;

    private long countStocks() {
        return stockRepository.count();
    }

    /**
     * Both halves: CurrentUserService decodes the JWT on the bound request, while the {@code created_by}
     * column is filled by Spring Data's auditor, which reads the SecurityContext. Saving a stock needs both.
     */
    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()));
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
