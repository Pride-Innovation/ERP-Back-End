package pride.bank.erp.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The register's Sub-category (commodity) column: its filter, its "Not set" filter and its sort.
 *
 * <p>Expectations are computed from the entities, not from the query under test. Run as the super
 * administrator, so scope admits every asset and only the filter decides.
 */
@SpringBootTest
@Transactional
class AssetSubCategoryTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private AssetType type;

    @BeforeEach
    void setUp() {
        signInAsSuperAdmin();
        // The category with the most assets, so every case has rows to look at.
        type = live().stream()
                .filter(a -> a.getAssetType() != null)
                .collect(Collectors.groupingBy(a -> a.getAssetType().getId(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> live().stream().filter(a -> a.getAssetType() != null
                        && a.getAssetType().getId().equals(e.getKey())).findFirst().orElseThrow().getAssetType())
                .orElseThrow();
        // One asset of that category with no sub-category, so "Not set" is always exercised.
        assetRepository.saveAndFlush(Asset.builder()
                .assetName("Sub-category test, none set")
                .engravedNumber("SUBCAT-TEST-NONE")
                .assetType(type)
                .disposed(false)
                .build());
    }

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFilterReturnsExactlyTheAssetsOfThatSubCategory() {
        Commodity commodity = inType().stream().map(Asset::getCommodity).filter(Objects::nonNull)
                .findFirst().orElseThrow();
        Set<Long> expected = inType().stream()
                .filter(a -> a.getCommodity() != null && a.getCommodity().getId().equals(commodity.getId()))
                .map(Asset::getId).collect(Collectors.toSet());

        Set<Long> actual = ids(search(commodity.getId(), false, "id", "DESC"));

        System.out.printf("sub-category test: %s / %s -> %d assets%n", type.getName(), commodity.getName(), expected.size());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void notSetReturnsExactlyTheAssetsWithNoSubCategory() {
        Set<Long> expected = inType().stream().filter(a -> a.getCommodity() == null)
                .map(Asset::getId).collect(Collectors.toSet());

        Set<Long> actual = ids(search(null, true, "id", "DESC"));

        assertThat(expected).as("the test's own asset is among them").isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    /** Sorted by name, through a LEFT join: assets with none are kept, not joined away. */
    @Test
    void sortingBySubCategoryOrdersByNameAndLosesNothing() {
        List<Asset> asc = search(null, false, AssetSearchDao.SORT_BY_COMMODITY_NAME, "ASC").getContent()
                .stream().map(dao -> assetRepository.findById(dao.getId()).orElseThrow()).toList();
        Set<Long> unsorted = ids(search(null, false, "id", "DESC"));

        assertThat(asc.stream().map(Asset::getId).collect(Collectors.toSet())).isEqualTo(unsorted);
        List<String> names = asc.stream().map(a -> a.getCommodity() == null ? null : a.getCommodity().getName()).toList();
        // MySQL sorts NULL first ascending; after that the names never go backwards.
        List<String> named = names.stream().dropWhile(Objects::isNull).toList();
        assertThat(named).doesNotContainNull();
        assertThat(named).isSortedAccordingTo(Comparator.comparing(String::toLowerCase));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Page<AssetDAO> search(Long commodityId, boolean noCommodity, String sortBy, String direction) {
        return assetService.fetchAllAssets(5000, direction, 0, sortBy, null, type.getId(), null, commodityId,
                null, null, false, null, null, null, null, null, null, false, false, null, null, null,
                false, false, noCommodity);
    }

    private List<Asset> live() {
        return assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .toList();
    }

    private List<Asset> inType() {
        return live().stream().filter(a -> a.getAssetType() != null && a.getAssetType().getId().equals(type.getId()))
                .toList();
    }

    private static Set<Long> ids(Page<AssetDAO> page) {
        return page.getContent().stream().map(AssetDAO::getId).collect(Collectors.toSet());
    }

    private void signInAsSuperAdmin() {
        User user = userRepository.findAll().stream().filter(AssetSubCategoryTest::isSuperAdmin).findFirst().orElseThrow();
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
