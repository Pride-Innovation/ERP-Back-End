package pride.bank.erp.stocktake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.appnotification.AppNotification;
import pride.bank.erp.appnotification.AppNotificationRepository;
import pride.bank.erp.appnotification.NotificationTarget;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.notification.Email;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.stocktake.StockTakeDTOs.CountView;
import pride.bank.erp.stocktake.StockTakeDTOs.CountedLine;
import pride.bank.erp.stocktake.StockTakeDTOs.OpenCountRequest;
import pride.bank.erp.stocktake.StockTakeDTOs.ReviewRequest;
import pride.bank.erp.stocktake.StockTakeDTOs.SaveCountsRequest;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Maker-checker on a stock count: whoever opened it may not approve or send it back, whatever they
 * hold, and any other approver at the branch may. When it is submitted, those other approvers are
 * told, in the app and by email, once the submission has committed.
 *
 * <p>Made through the real service at a branch with two people holding the stock-take permissions,
 * inside the rolled-back transaction. Mail is mocked: these are real addresses.
 */
@SpringBootTest
@Transactional
class StockCountMakerCheckerTest {

    @Autowired StockTakeService stockTakeService;
    @Autowired StockCountApprovalNotifier notifier;
    @Autowired StockCountRepository countRepository;
    @Autowired StockCountLineRepository lineRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreBalanceRepository storeBalanceRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppNotificationRepository notificationRepository;
    @Autowired JwtService jwtService;
    @MockBean Email email;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** A submitted count at a branch with at least two approvers: [opener, other approver] and the count. */
    private record Scenario(User opener, User checker, CountView count) {}

    private Scenario submittedByTheOpener() throws Exception {
        for (Store store : storeRepository.findAll()) {
            if (store.getStoreType() == StoreType.COURIER || store.getLocation() == null) continue;
            if (storeBalanceRepository.findByStore_Id(store.getId()).isEmpty()) continue;
            List<User> approvers = userRepository.findAll().stream()
                    .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(store.getLocation().getId()))
                    .filter(u -> !isSuperAdmin(u) && u.isEnabled() && !u.isBlocked())
                    .filter(u -> holds(u, Constants.APPROVE_STOCK_TAKE) && holds(u, Constants.PERFORM_STOCK_TAKE))
                    .toList();
            if (approvers.size() < 2) continue;

            signInAs(approvers.get(0));
            CountView opened;
            try {
                OpenCountRequest request = new OpenCountRequest(store.getId(), null, true, null);
                opened = stockTakeService.openCount(request);
            } catch (IllegalArgumentException | IllegalStateException alreadyOpen) {
                signOut();
                continue;
            }
            // Count every line at what the system holds, so there is no variance to explain.
            List<CountedLine> lines = lineRepository.findByStockCount_IdOrderByCommodity_NameAsc(opened.getId()).stream()
                    .map(l -> new CountedLine(l.getCommodity().getId(), l.getSystemQuantityAtStart(), null, null))
                    .toList();
            stockTakeService.saveCounts(opened.getId(), new SaveCountsRequest(lines));
            CountView submitted = stockTakeService.submit(opened.getId());
            signOut();
            System.out.printf("maker-checker test: store %d, opener %d, checker %d%n",
                    store.getId(), approvers.get(0).getId(), approvers.get(1).getId());
            return new Scenario(approvers.get(0), approvers.get(1), submitted);
        }
        System.out.println("maker-checker test: NO branch with two stock-take approvers and a free store - unexercised");
        return null;
    }

    @Test
    void theOpenerCanNeitherApproveNorSendBackAndAnotherApproverCan() throws Exception {
        Scenario s = submittedByTheOpener();
        if (s == null) return;

        signInAs(s.opener());
        assertThatThrownBy(() -> stockTakeService.approve(s.count().getId(), new ReviewRequest(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("person who opened it");
        assertThatThrownBy(() -> stockTakeService.sendBack(s.count().getId(), new ReviewRequest("recount")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("person who opened it");
        signOut();

        // Same roles and permissions, a different person: may decide it.
        signInAs(s.checker());
        assertThat(stockTakeService.approve(s.count().getId(), new ReviewRequest(null)).getStatus())
                .isEqualTo(StockCountStatus.APPROVED);
    }

    @Test
    void theApproversToldAreEveryoneElseAtTheBranchWhoMayApprove() throws Exception {
        Scenario s = submittedByTheOpener();
        if (s == null) return;
        StockCount count = countRepository.findById(s.count().getId()).orElseThrow();
        Long branch = count.getStore().getLocation().getId();

        List<User> approvers = notifier.approversFor(count);
        assertThat(approvers).extracting(User::getId)
                .contains(s.checker().getId())
                .doesNotContain(s.opener().getId());
        assertThat(approvers).allSatisfy(u -> {
            assertThat(u.getBranch().getId()).isEqualTo(branch);
            assertThat(holds(u, Constants.APPROVE_STOCK_TAKE)).isTrue();
            assertThat(u.isEnabled() && !u.isBlocked()).isTrue();
        });
        // Nobody who may approve it was left out.
        long eligible = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branch))
                .filter(u -> !u.getId().equals(s.opener().getId()) && u.isEnabled() && !u.isBlocked())
                .filter(u -> holds(u, Constants.APPROVE_STOCK_TAKE))
                .count();
        assertThat(approvers).hasSize((int) eligible);
    }

    /**
     * Nothing is sent while the submission is uncommitted - the test's transaction never commits, so
     * nothing must have been written. Delivering what was prepared writes one notification and one
     * email per approver.
     */
    @Test
    void approversAreToldOnlyOnceTheSubmissionCommits() throws Exception {
        Scenario s = submittedByTheOpener();
        if (s == null) return;

        String expected = NotificationTarget.STOCK_COUNT + ":" + s.count().getId();
        assertThat(targetsFor(s.checker())).as("not before the submission commits").doesNotContain(expected);
        verify(email, times(0)).sendStockCountApprovalEmail(any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any());

        StockCount count = countRepository.findById(s.count().getId()).orElseThrow();
        List<User> recipients = notifier.approversFor(count);
        notifier.deliver(new StockCountApprovalNotifier.Notice(count.getId(), count.getReference(),
                count.getStore().getName(), count.getStore().getLocation().getName(), "Test submitter",
                s.count().getTotalLines(), 0,
                recipients.stream().map(u -> new StockCountApprovalNotifier.Recipient(u, u.getEmail(), u.getFirstName())).toList()));

        for (User r : recipients) {
            assertThat(targetsFor(r)).as("told: user %d", r.getId()).contains(expected);
        }
        assertThat(targetsFor(s.opener())).as("the opener is not asked to approve").doesNotContain(expected);
        verify(email, times(recipients.size())).sendStockCountApprovalEmail(any(), any(), eq(count.getReference()),
                any(), any(), any(), anyInt(), anyInt(), any());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * A person's recent stock-count notifications as "TARGET:id" strings. Plain strings on purpose: a
     * failing assertion prints its subject, and printing a notification walks User.toString into
     * Token.toString and overflows the stack.
     */
    private List<String> targetsFor(User u) {
        return notificationRepository.findByRecipientIdOrderByCreateDateDesc(u.getId(), PageRequest.of(0, 20))
                .getContent().stream()
                .filter(n -> StockCountApprovalNotifier.TYPE.equals(n.getType()))
                .map((AppNotification n) -> n.getEntityType() + ":" + n.getEntityId())
                .toList();
    }

    private static boolean holds(User u, String authority) {
        return u.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static boolean isSuperAdmin(User u) {
        Stream<Role> roles = Stream.concat(
                Stream.of(u.getTitle() != null ? u.getTitle().getRole() : null),
                Stream.concat(
                        u.getAdditionalRoles() != null ? u.getAdditionalRoles().stream() : Stream.empty(),
                        u.getUnit() != null && u.getUnit().getRoles() != null ? u.getUnit().getRoles().stream() : Stream.empty()));
        return roles.anyMatch(r -> r != null && Constants.SUPER_ADMIN.equals(r.getName()));
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
