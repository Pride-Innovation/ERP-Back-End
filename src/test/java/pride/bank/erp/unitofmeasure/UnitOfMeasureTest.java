package pride.bank.erp.unitofmeasure;

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
import pride.bank.erp.asset.*;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityDTO;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.AssetTypeCommoditySeeder;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Units of measure: one managed list, and every place that stores a unit going through it.
 *
 * <p>Before V34 a commodity's unit was free text and an asset's came from four values written into the
 * page; nothing linked them, received assets got no unit, and the commodity seeder matched on the unit -
 * so changing one in Settings would have made every restart create a duplicate commodity.
 */
@SpringBootTest
@Transactional
class UnitOfMeasureTest {

    @Autowired UnitOfMeasureService unitService;
    @Autowired UnitOfMeasureRepository unitRepository;
    @Autowired CommodityService commodityService;
    @Autowired CommodityRepository commodityRepository;
    @Autowired AssetTypeRepository assetTypeRepository;
    @Autowired AssetTypeCommoditySeeder commoditySeeder;
    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ── What V34 left behind ────────────────────────────────────────────────

    @Test
    void everyUnitInUseIsOnTheListAndSpelledOneWay() {
        Set<String> listed = unitRepository.findAll().stream().map(UnitOfMeasure::getName).collect(Collectors.toSet());
        Set<String> commodityUnits = commodityRepository.findAll().stream().map(Commodity::getGroupName)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> assetUnits = assetRepository.findAll().stream().map(Asset::getUnitOfMeasure)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        assertThat(listed).containsAll(commodityUnits).containsAll(assetUnits);
        assertThat(listed).allSatisfy(n -> assertThat(n).isEqualTo(UnitOfMeasureService.normalise(n)));
        assertThat(listed).doesNotContain("ream", "piece", "pcs");
    }

    // ── The list ────────────────────────────────────────────────────────────

    @Test
    void addingNormalisesAndRefusesBlanksAndDuplicates() {
        UnitOfMeasure added = unitService.create("  Test   Cartons ");
        assertThat(added.getName()).isEqualTo("test cartons");
        assertThat(added.isActive()).isTrue();

        assertThatThrownBy(() -> unitService.create("TEST CARTONS")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already on the list");
        assertThatThrownBy(() -> unitService.create("   ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> unitService.create("x".repeat(51))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aSwitchedOffUnitIsNotOfferedOrAcceptedForANewChoice() throws Exception {
        UnitOfMeasure unit = unitService.create("test crates");
        unitService.setActive(unit.getId(), false);

        assertThat(unitService.list(false)).extracting(UnitOfMeasure::getName).doesNotContain("test crates");
        assertThat(unitService.list(true)).extracting(UnitOfMeasure::getName).contains("test crates");
        assertThatThrownBy(() -> unitService.resolve("test crates", null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("switched off");
        // ...but a record already carrying it can still be saved.
        assertThat(unitService.resolve("Test Crates", "test crates")).isEqualTo("test crates");
        assertThatThrownBy(() -> unitService.create("test crates")).hasMessageContaining("Switch it back on");
    }

    @Test
    void anUnknownUnitIsRefusedNamingWhatMayBeChosen() {
        assertThatThrownBy(() -> unitService.resolve("bushels", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a unit of measure").hasMessageContaining("pieces");
        assertThat(unitService.resolve("  PIECES ", null)).isEqualTo("pieces");
        assertThat(unitService.resolve("  ", null)).isNull();
    }

    /** The seeder fills a new database's list, and never switches a unit back on. */
    @Test
    void seedingFromCommoditiesIsCreateOnly() throws Exception {
        UnitOfMeasure pieces = unitRepository.findByNameIgnoreCase("pieces").orElseThrow();
        unitService.setActive(pieces.getId(), false);
        long before = unitRepository.count();

        unitService.seedFromCommodities();

        assertThat(unitRepository.count()).isEqualTo(before);
        assertThat(unitRepository.findById(pieces.getId()).orElseThrow().isActive()).isFalse();
    }

    // ── Commodities ─────────────────────────────────────────────────────────

    @Test
    void aCommodityTakesItsUnitFromTheList() throws Exception {
        AssetType type = assetTypeRepository.findAll().get(0);
        Commodity created = commodityService.createCommodity(dto("UoM test commodity", "Boxes", type.getId()));
        assertThat(created.getGroupName()).isEqualTo("boxes");

        assertThatThrownBy(() -> commodityService.createCommodity(dto("UoM test 2", "bushels", type.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> commodityService.createCommodity(dto("UoM test 3", " ", type.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    /**
     * The trap the seeder had: it matched a commodity by name AND unit, and overwrote the unit. A
     * commodity whose unit was changed in Settings would no longer match, and every restart would
     * create a duplicate beside it.
     */
    @Test
    void theSeederNeitherDuplicatesNorRevertsACommodityWhoseUnitWasChanged() {
        Commodity seeded = commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && "pieces".equals(c.getGroupName()))
                .findFirst().orElseThrow();
        seeded.setGroupName("boxes");
        commodityRepository.saveAndFlush(seeded);
        long before = commodityRepository.count();

        commoditySeeder.seedAssetTypesAndCommodities();

        assertThat(commodityRepository.count()).as("no duplicate created").isEqualTo(before);
        assertThat(commodityRepository.findById(seeded.getId()).orElseThrow().getGroupName())
                .as("the unit set in Settings is kept").isEqualTo("boxes");
    }

    // ── Assets ──────────────────────────────────────────────────────────────

    @Test
    void theImportTakesTheCommodityUnitWhenBlankAndRefusesAnUnknownOne() throws Exception {
        signInAsSuperAdmin();
        Commodity commodity = commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && c.getGroupName() != null).findFirst().orElseThrow();
        Long typeId = commodity.getAssetType().getId();

        Map<String, Object> result = assetService.bulkInsertAsset(List.of(
                row(1L, "UOM-TEST-A", commodity.getName(), null),
                row(2L, "UOM-TEST-B", commodity.getName(), "  BOXES "),
                row(3L, "UOM-TEST-C", commodity.getName(), "bushels")), typeId);

        assertThat(result.get("inserted")).isEqualTo(2);
        assertThat(String.valueOf(result.get("errors"))).contains("not a unit of measure");
        assertThat(byEngraved("UOM-TEST-A").getUnitOfMeasure()).isEqualTo(commodity.getGroupName());
        assertThat(byEngraved("UOM-TEST-B").getUnitOfMeasure()).isEqualTo("boxes");
    }

    /** Decision (b): the commodity's unit by default, and an asset may differ from it. */
    @Test
    void anEditKeepsAChosenUnitAndFallsBackToTheCommodity() throws Exception {
        signInAsSuperAdmin();
        Commodity commodity = commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && "pieces".equals(c.getGroupName())).findFirst().orElseThrow();
        assetService.bulkInsertAsset(List.of(row(1L, "UOM-TEST-EDIT", commodity.getName(), null)),
                commodity.getAssetType().getId());
        Asset asset = byEngraved("UOM-TEST-EDIT");

        AssetDTO edit = editOf(asset);
        edit.setUnitOfMeasure("boxes");
        assetService.updateAsset(edit, asset.getId());
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getUnitOfMeasure()).isEqualTo("boxes");

        edit.setUnitOfMeasure("");
        assetService.updateAsset(edit, asset.getId());
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getUnitOfMeasure()).isEqualTo("pieces");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static CommodityDTO dto(String name, String unit, Long typeId) {
        CommodityDTO dto = new CommodityDTO();
        dto.setName(name);
        dto.setGroupName(unit);
        dto.setAssetType(typeId);
        return dto;
    }

    private BulkAssetDTO row(Long no, String engraved, String commodity, String unit) {
        BulkAssetDTO dto = new BulkAssetDTO();
        dto.setNo(no);
        dto.setAssetName("Unit of measure test");
        dto.setEngravedNumber(engraved);
        dto.setCommodity(commodity);
        dto.setLocation(branchRepository.findAll().stream().filter(Branch::isHeadOffice).findFirst().orElseThrow().getName());
        dto.setUnitOfMeasure(unit);
        return dto;
    }

    private AssetDTO editOf(Asset a) {
        return AssetDTO.builder()
                .assetName(a.getAssetName())
                .engravedNumber(a.getEngravedNumber())
                .dateReceipt(a.getDateReceipt())
                .assetStatus(a.getAssetStatus() != null ? a.getAssetStatus().getId() : null)
                .assetType(a.getAssetType() != null ? a.getAssetType().getId() : null)
                .branch(a.getBranch() != null ? a.getBranch().getId() : null)
                .assignedTo(a.getAssignedTo() != null ? a.getAssignedTo().getId() : null)
                .build();
    }

    private Asset byEngraved(String engraved) {
        return assetRepository.findAll().stream().filter(a -> engraved.equals(a.getEngravedNumber()))
                .findFirst().orElseThrow();
    }

    private void signInAsSuperAdmin() {
        User user = userRepository.findAll().stream().filter(UnitOfMeasureTest::isSuperAdmin).findFirst().orElseThrow();
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
