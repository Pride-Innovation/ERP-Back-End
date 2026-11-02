package pride.bank.erp.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.movement.DisposalDTO;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.MovementStatus;
import pride.bank.erp.movement.RepairMovementService;
import pride.bank.erp.status.StatusRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * My Items: what a person holds, what of theirs is away for repair or disposal, and what is on its
 * way to them - and the detail page opening every item the list shows.
 *
 * <p>Expectations are computed from the entities, not from the queries under test. Runs against live
 * data inside a rolled-back transaction, and says so when a case finds nothing to exercise.
 */
@SpringBootTest
@Transactional
class MyItemsTest {

    @Autowired MyItemsService myItemsService;
    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired MovementRepository movementRepository;
    @Autowired MovementService movementService;
    @Autowired RepairMovementService repairMovementService;
    @Autowired AccessScopeService accessScopeService;
    @Autowired StatusRepository statusRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** Everyone who has handed over an asset that is still away. */
    private List<User> senders() {
        Set<Long> ids = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .filter(AssetService::isAwayFromItsBranch)
                .map(a -> movementService.holderLastSentAwayFrom(a.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) System.out.println("my items test: NO asset away with a recorded sender - unexercised");
        return userRepository.findAllById(ids);
    }

    @Test
    void theSectionsMatchTheEntitiesAndDoNotOverlap() {
        List<User> people = new java.util.ArrayList<>(senders());
        // And everyone holding something, so the held half runs for people with nothing away too.
        assetRepository.findAll().stream().map(Asset::getAssignedTo).filter(Objects::nonNull)
                .filter(u -> people.stream().noneMatch(p -> p.getId().equals(u.getId())))
                .distinct().forEach(people::add);

        for (User person : people) {
            MyItemsView view = myItemsService.forUser(person);
            Set<Long> held = view.held().stream().map(MyItemsView.HeldItem::id).collect(Collectors.toSet());
            Set<Long> away = view.away().stream().map(MyItemsView.AwayItem::id).collect(Collectors.toSet());

            List<Asset> live = assetRepository.findAll().stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                    .toList();
            Set<Long> expectedHeld = live.stream()
                    .filter(a -> a.getAssignedTo() != null && a.getAssignedTo().getId().equals(person.getId()))
                    .filter(a -> !AssetService.isAwayFromItsBranch(a))
                    .map(Asset::getId).collect(Collectors.toSet());
            Set<Long> expectedAway = assetRepository.findAll().stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                    .filter(AssetService::isAwayFromItsBranch)
                    .filter(a -> person.getId().equals(movementService.holderLastSentAwayFrom(a.getId()))
                            || (a.getAssignedTo() != null && a.getAssignedTo().getId().equals(person.getId())
                                && !Boolean.TRUE.equals(a.getDisposed())))
                    .map(Asset::getId).collect(Collectors.toSet());

            assertThat(held).as("held by %s", person.getId()).isEqualTo(expectedHeld);
            assertThat(away).as("away for %s", person.getId()).isEqualTo(expectedAway);
            assertThat(Collections.disjoint(held, away)).as("an asset is listed once").isTrue();
        }
    }

    @Test
    void incomingIsEveryOpenMovementAddressedToThemOrFulfillingTheirRequest() {
        for (User person : userRepository.findAll()) {
            Set<Long> expected = movementRepository.findAll().stream()
                    .filter(m -> m.getStatus() != MovementStatus.COMPLETED && m.getStatus() != MovementStatus.CANCELLED)
                    .filter(m -> (m.getRecipientUser() != null && m.getRecipientUser().getId().equals(person.getId()))
                            || (m.getRequest() != null && m.getRequest().getRequester() != null
                                && m.getRequest().getRequester().getId().equals(person.getId())))
                    .map(Movement::getId).collect(Collectors.toSet());
            Set<Long> actual = myItemsService.forUser(person).incoming().stream()
                    .map(MyItemsView.IncomingMovement::id).collect(Collectors.toSet());
            assertThat(actual).as("incoming for %s", person.getId()).isEqualTo(expected);
        }
    }

    /** Every item a sender's list shows opens on the detail page, for the real senders in the data. */
    @Test
    void everyAwayItemOpensForThePersonItIsListedFor() throws Exception {
        for (User sender : senders()) {
            signInAs(sender);
            for (MyItemsView.AwayItem item : myItemsService.forUser(sender).away()) {
                assetService.fetchSingleAsset(item.id());
            }
            signOut();
        }
    }

    /**
     * The person who handed an asset over may open it while it is away, and only while it is away.
     *
     * <p>Every real sender in the data is admitted by the branch rule already (they are BRANCH
     * scoped), so the case the new limb exists for - somebody at SELF, whose assignment the transfer
     * closed - is made here: such a person is named as the sender of an away asset, inside the
     * rolled-back transaction. The rule without the limb is asserted to refuse them first, so the case
     * cannot pass for the wrong reason.
     */
    @Test
    void aSelfScopedSenderMayOpenTheirAssetWhileAwayAndTheReachLapsesWhenItComesHome() throws Exception {
        for (Asset asset : assetRepository.findAll()) {
            if (Boolean.TRUE.equals(asset.getDeleted()) || !AssetService.isAwayFromItsBranch(asset)) continue;
            Movement transfer = movementRepository.findFirstByItems_Asset_IdAndMovementTypeInOrderByIdDesc(asset.getId(),
                    List.of(pride.bank.erp.movement.MovementType.REPAIR_TRANSFER,
                            pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER)).orElse(null);
            if (transfer == null) continue;

            for (User person : userRepository.findAll()) {
                if (person.getAuthorities().stream().noneMatch(a -> "READ_ASSET".equals(a.getAuthority()))) continue;
                signInAs(person);
                boolean self = accessScopeService.scopeFor(ScopeSubject.ASSETS, pride.bank.erp.access.ScopeAction.VIEW)
                        == pride.bank.erp.dashboard.DashboardScope.SELF;
                boolean refusedWithoutTheLimb = !accessScopeService.canViewAnyOf(ScopeSubject.ASSETS,
                        Arrays.asList(branchOf(asset), movementService.branchLastSentAwayFrom(asset.getId())),
                        Collections.singletonList(holderOf(asset)));
                signOut();
                if (!self || !refusedWithoutTheLimb) continue;

                transfer.setOriginHolder(person);
                movementRepository.saveAndFlush(transfer);

                signInAs(person);
                assertThat(myItemsService.forUser(person).away()).extracting(MyItemsView.AwayItem::id)
                        .as("listed as theirs").contains(asset.getId());
                assetService.fetchSingleAsset(asset.getId());
                String tag = asset.getEngravedNumber();
                if (tag != null && !tag.isBlank()) {
                    // Scanning the label must find what the record opens - the search asks the same guard.
                    assertThat(assetService.findAssetsByTagName(tag)).extracting(pride.bank.erp.asset.AssetTagMatch::getId)
                            .as("the tag search finds the sender's asset on the bench").containsExactly(asset.getId());
                }

                // Home again: no longer away, so the person is back to the ordinary rule, which refuses.
                asset.setAssetStatus(statusRepository.findByStatus("sentToStore").orElseThrow());
                assetRepository.saveAndFlush(asset);
                assertThat(accessScopeService.canViewAnyOf(ScopeSubject.ASSETS,
                        Collections.singletonList(branchOf(asset)), Collections.singletonList(holderOf(asset))))
                        .as("the ordinary rule refuses them").isFalse();
                assertThatThrownBy(() -> assetService.fetchSingleAsset(asset.getId()))
                        .as("the reach lapses when the asset comes home").isInstanceOf(AccessDeniedException.class);
                if (tag != null && !tag.isBlank()) {
                    assertThat(assetService.findAssetsByTagName(tag))
                            .as("and the tag search stops finding it, with the same answer as no such tag").isEmpty();
                }
                assertThat(myItemsService.forUser(person).away()).extracting(MyItemsView.AwayItem::id)
                        .doesNotContain(asset.getId());
                return;
            }
        }
        System.out.println("my items test: NO SELF-scoped reader the branch rule refuses - widening unexercised");
    }

    /** Written off from the bench, it is still the sender's item: the disposal records them too. */
    @Test
    void aBenchWriteOffStaysOnTheSendersList() throws Exception {
        User admin = userRepository.findAll().stream()
                .filter(u -> u.getAuthorities().stream().anyMatch(a -> "DISPOSE_ASSET".equals(a.getAuthority())))
                .filter(u -> { signInAs(u);
                    boolean all = accessScopeService.scopeFor(ScopeSubject.ASSETS, pride.bank.erp.access.ScopeAction.MANAGE)
                            == pride.bank.erp.dashboard.DashboardScope.ALL;
                    signOut(); return all; })
                .findFirst().orElse(null);
        Asset benched = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .filter(a -> a.getAssetStatus() != null && "inMaintenance".equals(a.getAssetStatus().getStatus()))
                .filter(a -> a.getCurrentStore() != null && movementService.holderLastSentAwayFrom(a.getId()) != null)
                .findFirst().orElse(null);
        if (admin == null || benched == null) {
            System.out.println("my items test: NO disposer or benched asset - bench write-off unexercised");
            return;
        }
        Long sender = movementService.holderLastSentAwayFrom(benched.getId());

        signInAs(admin);
        DisposalDTO dto = new DisposalDTO();
        dto.setAssetId(benched.getId());
        dto.setEarlyDisposalReason("Unrepairable - test");
        dto.setProceedWithoutApproval(true);
        dto.setBypassReason("test");
        Movement disposal = repairMovementService.disposeAsset(dto);
        signOut();

        assertThat(disposal.getOriginHolder()).as("the disposal records whose item it was").isNotNull();
        assertThat(disposal.getOriginHolder().getId()).isEqualTo(sender);
        User person = userRepository.findById(sender).orElseThrow();
        assertThat(myItemsService.forUser(person).away())
                .filteredOn(i -> i.id().equals(benched.getId()))
                .singleElement()
                .satisfies(i -> assertThat(i.purpose()).isEqualTo("disposal"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Long branchOf(Asset a) { return a.getBranch() != null ? a.getBranch().getId() : null; }

    private static Long holderOf(Asset a) { return a.getAssignedTo() != null ? a.getAssignedTo().getId() : null; }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
