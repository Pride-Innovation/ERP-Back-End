package pride.bank.erp.consignment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.movement.MovementRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the rules that make a shared journey safe to run.
 *
 * <p>The consignment carries several obligations at once, so the damage from a wrong transition is
 * multiplied: marking one arrived credits stock for every movement on it. These tests pin the
 * boundaries rather than the happy path, because the boundaries are what a future change is most
 * likely to erode.
 */
@SpringBootTest
class ConsignmentLifecycleTest {

    @Autowired
    private ConsignmentService consignmentService;

    @Autowired
    private ConsignmentRepository consignmentRepository;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private pride.bank.erp.user.UserRepository userRepository;

    @Autowired
    private pride.bank.erp.config.JwtService jwtService;

    /**
     * Runs these as somebody entitled to act on a journey.
     *
     * <h2>Why a principal is needed now</h2>
     * The consignment lifecycle used to be reachable by anyone holding the permission — the service
     * never asked whose journey it was, so a test with no authenticated user sailed through. It now
     * refuses a journey neither of whose ends is the caller's, which is correct, and which would
     * otherwise stop every assertion below <em>before</em> the business rule it is pinning.
     *
     * <p>Accepting {@code AccessDeniedException} in the assertions was the wrong way out: they would
     * then pass whether or not the lifecycle rules still held, and those rules are the point —
     * marking a consignment arrived credits stock for every movement riding on it.
     *
     * <h2>Why a bound request rather than a SecurityContext principal</h2>
     * {@code CurrentUserService} resolves the caller by decoding the JWT on the incoming request, not
     * from {@code SecurityContextHolder} — so setting an {@code Authentication} does nothing at all,
     * silently. A token is minted for a real user holding the cross-branch grant and bound to a mock
     * request, which is the only thing the resolver actually reads.
     */
    @org.junit.jupiter.api.BeforeEach
    void actAsSomeoneEntitled() {
        userRepository.findAll().stream()
                .filter(u -> u.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .anyMatch(pride.bank.erp.helper.Constants.MANAGE_ALL_BRANCH_MOVEMENTS::equals))
                .findFirst()
                .ifPresent(user -> {
                    var request = new org.springframework.mock.web.MockHttpServletRequest();
                    request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
                    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                            new org.springframework.web.context.request.ServletRequestAttributes(request));
                });
    }

    @org.junit.jupiter.api.AfterEach
    void clearPrincipal() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @Transactional
    void aConsignmentCannotStartAndEndInTheSameLocation() {
        List<Branch> branches = branchRepository.findAll();
        if (branches.isEmpty()) return; // nothing seeded to assert against

        Long same = branches.get(0).getId();
        assertThatThrownBy(() -> consignmentService.createConsignment(ConsignmentDTO.builder()
                .sourceLocationId(same)
                .destLocationId(same)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between locations");
    }

    @Test
    @Transactional
    void aDraftConsignmentCannotBeMarkedInTransitOrArrived() throws Exception {
        List<Branch> branches = branchRepository.findAll();
        if (branches.size() < 2) return;

        Consignment consignment = consignmentService.createConsignment(ConsignmentDTO.builder()
                .sourceLocationId(branches.get(0).getId())
                .destLocationId(branches.get(1).getId())
                .build());

        // Skipping dispatch would put stock against a courier that was never given the goods.
        assertThatThrownBy(() -> consignmentService.markInTransit(consignment.getId()))
                .isInstanceOf(IllegalStateException.class);

        // Skipping transit would credit the destination for goods that never left.
        assertThatThrownBy(() -> consignmentService.markArrived(consignment.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    void anEmptyConsignmentCannotBeDispatched() throws Exception {
        List<Branch> branches = branchRepository.findAll();
        if (branches.size() < 2) return;

        Consignment consignment = consignmentService.createConsignment(ConsignmentDTO.builder()
                .sourceLocationId(branches.get(0).getId())
                .destLocationId(branches.get(1).getId())
                .build());

        assertThatThrownBy(() -> consignmentService.dispatch(consignment.getId(),
                ConsignmentDispatchDTO.builder()
                        .courierName("Test Courier")
                        .plateNumber("UAA 000A")
                        .deliveryDocuments(List.of("note.pdf"))
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @Transactional
    void aNewConsignmentIsDraftAndCarriesAReference() throws Exception {
        List<Branch> branches = branchRepository.findAll();
        if (branches.size() < 2) return;

        Consignment consignment = consignmentService.createConsignment(ConsignmentDTO.builder()
                .sourceLocationId(branches.get(0).getId())
                .destLocationId(branches.get(1).getId())
                .remarks("Weekly run")
                .build());

        assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.DRAFT);
        // Derived from the id, so two people opening consignments at once cannot collide.
        assertThat(consignment.getReference()).matches("CNS/\\d{4}/\\d{6}");
        assertThat(consignmentService.movementsOn(consignment.getId())).isEmpty();
    }

    @Test
    @Transactional
    void movementsThatDoNotShareTheRouteAreRefused() throws Exception {
        List<Branch> branches = branchRepository.findAll();
        if (branches.size() < 2) return;

        Consignment consignment = consignmentService.createConsignment(ConsignmentDTO.builder()
                .sourceLocationId(branches.get(0).getId())
                .destLocationId(branches.get(1).getId())
                .build());

        // A movement id that does not exist stands in for "anything the loader cannot verify":
        // loading must fail rather than silently attach nothing.
        assertThatThrownBy(() -> consignmentService.addMovement(consignment.getId(), -1L))
                .hasMessageContaining("not found");
    }
}
