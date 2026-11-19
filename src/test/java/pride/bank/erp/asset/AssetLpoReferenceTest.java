package pride.bank.erp.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The LPO on an asset: the stock order's for a received asset, a typed reference for an imported one.
 *
 * <p>An edit used to look a stock order up by the LPO the form sent, so the LPO was required on every
 * edit and any asset with no order - every imported one - could not be saved at all. The lookup never
 * relinked anything; it only locked whichever order matched. An edit now uses the asset's own order.
 */
@SpringBootTest
@Transactional
class AssetLpoReferenceTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired AssetTypeRepository assetTypeRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired CommodityRepository commodityRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theImportKeepsAnLpoReferenceTrimmedAndBlankAsNone() throws Exception {
        signInAsSuperAdmin();
        AssetType type = assetTypeRepository.findAll().get(0);

        Map<String, Object> result = assetService.bulkInsertAsset(List.of(
                row(1L, "LPO-TEST-A", "  LPO/2019/044  "),
                row(2L, "LPO-TEST-B", "   ")), type.getId());

        assertThat(result.get("inserted")).isEqualTo(2);
        Asset withRef = byEngraved("LPO-TEST-A");
        assertThat(withRef.getLpoReference()).isEqualTo("LPO/2019/044");
        assertThat(withRef.getStock()).as("a reference is never a link to an order").isNull();
        assertThat(byEngraved("LPO-TEST-B").getLpoReference()).isNull();
    }

    @Test
    void anOverlongReferenceIsARowErrorNotAFailedImport() throws Exception {
        signInAsSuperAdmin();
        AssetType type = assetTypeRepository.findAll().get(0);

        Map<String, Object> result = assetService.bulkInsertAsset(List.of(
                row(1L, "LPO-TEST-LONG", "X".repeat(101)),
                row(2L, "LPO-TEST-OK", "LPO-1")), type.getId());

        assertThat(result.get("inserted")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(String.valueOf(result.get("errors"))).contains("at most 100 characters");
    }

    /** The case that could not be saved at all: an imported asset, no order, no LPO sent. */
    @Test
    void anAssetWithNoStockOrderCanBeEditedAndItsReferenceSetAndCleared() throws Exception {
        signInAsSuperAdmin();
        AssetType type = assetTypeRepository.findAll().get(0);
        assetService.bulkInsertAsset(List.of(row(1L, "LPO-TEST-EDIT", null)), type.getId());
        Asset imported = byEngraved("LPO-TEST-EDIT");

        AssetDTO edit = editOf(imported);
        edit.setLpoNumber(null);
        edit.setLpoReference(" LPO/2020/7 ");
        assetService.updateAsset(edit, imported.getId());
        assertThat(assetRepository.findById(imported.getId()).orElseThrow().getLpoReference())
                .isEqualTo("LPO/2020/7");

        edit.setLpoReference("");
        assetService.updateAsset(edit, imported.getId());
        assertThat(assetRepository.findById(imported.getId()).orElseThrow().getLpoReference()).isNull();
    }

    /** An edit never moves an asset to another order, and a typed reference cannot sit beside an order. */
    @Test
    void aReceivedAssetKeepsItsOrderWhateverTheFormSends() throws Exception {
        signInAsSuperAdmin();
        Optional<Asset> received = assetRepository.findAll().stream()
                .filter(a -> a.getStock() != null)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .findFirst();
        if (received.isEmpty()) {
            System.out.println("asset lpo test: NO asset with a stock order - unexercised");
            return;
        }
        Asset asset = received.get();
        Long stockId = asset.getStock().getId();

        AssetDTO edit = editOf(asset);
        edit.setLpoNumber(null);
        edit.setLpoReference("SHOULD-BE-IGNORED");
        assetService.updateAsset(edit, asset.getId());

        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        Stock stock = after.getStock();
        assertThat(stock.getId()).isEqualTo(stockId);
        assertThat(stock.isCanNotBeDeleted()).as("an edited order's assets lock it").isTrue();
        assertThat(after.getLpoReference()).isNull();
    }

    /** Create counts the asset against an order, so it needs one - and says so as a 400. */
    @Test
    void creatingWithNoLpoIsRefusedPlainly() {
        signInAsSuperAdmin();
        Commodity commodity = commodityRepository.findAll().get(0);
        AssetDTO create = AssetDTO.builder()
                .assetName("No order")
                .category(commodity.getId())
                .lpoNumber("  ")
                .build();

        assertThatThrownBy(() -> assetService.createAsset(create))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stock order (LPO)");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private BulkAssetDTO row(Long no, String engraved, String lpoReference) {
        BulkAssetDTO dto = new BulkAssetDTO();
        dto.setNo(no);
        dto.setAssetName("LPO reference test");
        dto.setEngravedNumber(engraved);
        dto.setLocation(headOffice().getName());
        dto.setLpoReference(lpoReference);
        return dto;
    }

    private AssetDTO editOf(Asset a) {
        return AssetDTO.builder()
                .assetName(a.getAssetName())
                .engravedNumber(a.getEngravedNumber())
                .dateReceipt(a.getDateReceipt())
                .unitOfMeasure(a.getUnitOfMeasure())
                .purchaseCost(a.getPurchaseCost() != null ? a.getPurchaseCost().toPlainString() : null)
                .costOfTheAsset(a.getCostOfTheAsset() != null ? a.getCostOfTheAsset().toPlainString() : null)
                .assetStatus(a.getAssetStatus() != null ? a.getAssetStatus().getId() : null)
                .assetType(a.getAssetType() != null ? a.getAssetType().getId() : null)
                .branch(a.getBranch() != null ? a.getBranch().getId() : null)
                .assignedTo(a.getAssignedTo() != null ? a.getAssignedTo().getId() : null)
                .supplier(a.getSupplier() != null ? a.getSupplier().getId() : null)
                .build();
    }

    private Asset byEngraved(String engraved) {
        return assetRepository.findAll().stream()
                .filter(a -> engraved.equals(a.getEngravedNumber()))
                .findFirst().orElseThrow();
    }

    private Branch headOffice() {
        return branchRepository.findAll().stream().filter(Branch::isHeadOffice).findFirst().orElseThrow();
    }

    private void signInAsSuperAdmin() {
        User user = userRepository.findAll().stream().filter(AssetLpoReferenceTest::isSuperAdmin)
                .findFirst().orElseThrow();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static boolean isSuperAdmin(User u) {
        Stream<Role> roles = Stream.concat(
                Stream.of(u.getTitle() != null ? u.getTitle().getRole() : null),
                Stream.concat(
                        u.getAdditionalRoles() != null ? u.getAdditionalRoles().stream() : Stream.empty(),
                        u.getUnit() != null && u.getUnit().getRoles() != null ? u.getUnit().getRoles().stream() : Stream.empty()));
        return roles.anyMatch(r -> r != null && Constants.SUPER_ADMIN.equals(r.getName()));
    }
}
