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
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single-asset read carries the holder's id, and the edit form's round trip keeps the holder.
 *
 * <p>{@code buildAssetDao} copied the holder's name, email and branch but not the id. The edit form
 * binds the holder by id, so it opened with Assigned To empty, and saving it as it opened sent no
 * holder. {@code updateAsset} treats that as a reassignment to nobody, and the history row it writes
 * has a NOT NULL user - so every untouched save of a held asset failed.
 */
@SpringBootTest
@Transactional
class AssetHolderRoundTripTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSingleAssetReadCarriesTheHoldersId() throws Exception {
        Optional<Asset> held = heldAsset();
        if (held.isEmpty()) return;
        signInAsSuperAdmin();

        AssetDAO dao = assetService.fetchSingleAsset(held.get().getId());

        assertThat(dao.getAssignedTo()).isNotNull();
        assertThat(dao.getAssignedTo().getId()).isEqualTo(held.get().getAssignedTo().getId());
    }

    /** What the edit page does: read the asset, bind its fields by id, save without touching them. */
    @Test
    void savingTheEditFormAsItOpensKeepsTheHolder() throws Exception {
        Optional<Asset> held = heldAsset();
        if (held.isEmpty()) return;
        signInAsSuperAdmin();
        Long assetId = held.get().getId();
        Long holderId = held.get().getAssignedTo().getId();

        AssetDAO read = assetService.fetchSingleAsset(assetId);
        AssetDTO asOpened = AssetDTO.builder()
                .assetName(read.getAssetName())
                .engravedNumber(read.getEngravedNumber())
                .dateReceipt(read.getDateReceipt())
                .unitOfMeasure(read.getUnitOfMeasure())
                .purchaseCost(read.getPurchaseCost())
                .costOfTheAsset(read.getCostOfTheAsset())
                .assetStatus(read.getAssetStatus() != null ? read.getAssetStatus().getId() : null)
                .assetType(read.getAssetType() != null ? read.getAssetType().getId() : null)
                .branch(read.getBranch() != null ? read.getBranch().getId() : null)
                .supplier(read.getSupplier() != null ? read.getSupplier().getId() : null)
                .assignedTo(read.getAssignedTo() != null ? read.getAssignedTo().getId() : null)
                .build();
        assetService.updateAsset(asOpened, assetId);

        Asset after = assetRepository.findById(assetId).orElseThrow();
        assertThat(after.getAssignedTo()).as("the holder survives an untouched save").isNotNull();
        assertThat(after.getAssignedTo().getId()).isEqualTo(holderId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<Asset> heldAsset() {
        Optional<Asset> found = assetRepository.findAll().stream()
                .filter(a -> a.getAssignedTo() != null)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .findFirst();
        if (found.isEmpty()) System.out.println("asset holder test: NO held asset - unexercised");
        return found;
    }

    private void signInAsSuperAdmin() {
        User user = userRepository.findAll().stream().filter(AssetHolderRoundTripTest::isSuperAdmin)
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
