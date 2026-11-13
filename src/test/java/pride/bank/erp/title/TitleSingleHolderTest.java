/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.authentication.AuthenticationService;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.TitleOccupiedException;
import pride.bank.erp.notification.Email;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.user.BulkUserDTO;
import pride.bank.erp.user.TitleOccupancyService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserDTO;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A title marked single-holder may have one active holder per location. "Active" is not blocked,
 * and enabled or on leave: disabling or blocking an account frees the title, going on leave does not.
 *
 * <p>Everything is made inside the test's transaction and rolled back. Email is mocked, so creating,
 * importing and verifying accounts sends nothing. Assertions compare ids, never whole entities - a
 * failing assertion prints its subject, and printing a User overflows the stack.
 */
@SpringBootTest
@Transactional
class TitleSingleHolderTest {

    @MockBean private Email email;

    @Autowired private UserService userService;
    @Autowired private TitleService titleService;
    @Autowired private TitleOccupancyService occupancy;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private UserRepository userRepository;
    @Autowired private TitleRepository titleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private LeaveRepository leaveRepository;
    @Autowired private JwtService jwtService;

    private Branch branch;
    private Role role;
    private Title single;
    private int seq;

    @BeforeEach
    void setUp() {
        // Someone at a branch, acting on accounts at that same branch, so the user scope admits them.
        User actor = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !u.isBlocked() && u.getBranch() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("No enabled user with a branch"));
        branch = actor.getBranch();
        role = roleRepository.findAll().stream().findFirst().orElseThrow();
        single = titleRepository.save(Title.builder()
                .name("ZZ-SINGLE-HOLDER").shortCode("ZZSH").role(role).singleHolder(true).build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(actor));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        actor, null, actor.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private User person(Title title, boolean enabled, boolean blocked) {
        seq++;
        return userRepository.save(User.builder()
                .firstName("Zz" + seq).lastName("Holder").email("zz.holder" + seq + "@example.invalid")
                .staffNumber("ZZSH" + seq).password("not-used").gender("male")
                .title(title).branch(branch).isEnabled(enabled).blocked(blocked).build());
    }

    private UserDTO edit(User u, Title title) {
        UserDTO dto = new UserDTO();
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setEmail(u.getEmail());
        dto.setStaffNumber(u.getStaffNumber());
        dto.setGender(u.getGender());
        dto.setBranch(branch.getId());
        dto.setTitle(title.getId());
        dto.setEnabled(u.isEnabled());
        dto.setAccountNonLocked(true);
        dto.setBlocked(u.isBlocked());
        return dto;
    }

    @Test
    void aSecondActiveHolderIsRefusedNamingTheFirst() {
        User first = person(single, true, false);

        assertThatThrownBy(() -> occupancy.requireAssignable(single, branch, null))
                .isInstanceOf(TitleOccupiedException.class)
                .hasMessageContaining(first.getEmail())
                .hasMessageContaining(branch.getName());
        assertThatCode(() -> occupancy.requireAssignable(single, branch, first.getId()))
                .as("an account never counts against itself").doesNotThrowAnyException();
    }

    @Test
    void aTitleAllowingSeveralHoldersIsNeverRefused() {
        Title many = titleRepository.save(Title.builder().name("ZZ-MANY").shortCode("ZZM").role(role).build());
        person(many, true, false);
        assertThatCode(() -> occupancy.requireAssignable(many, branch, null)).doesNotThrowAnyException();
    }

    @Test
    void disablingOrBlockingFreesTheTitleButLeaveDoesNot() {
        person(single, false, false);
        person(single, true, true);
        assertThatCode(() -> occupancy.requireAssignable(single, branch, null))
                .as("a disabled and a blocked holder leave the title free").doesNotThrowAnyException();

        User away = person(single, false, false);
        leaveRepository.save(Leave.builder().leaveType("ANNUAL").reason("test").active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(5))
                .onLeaveUser(away).build());
        assertThatThrownBy(() -> occupancy.requireAssignable(single, branch, null))
                .as("a holder on leave still holds the title").isInstanceOf(TitleOccupiedException.class);
    }

    @Test
    void editingYourOwnRecordIsNeverBlockedButMovingSomeoneOntoTheTitleIs() throws Exception {
        User holder = person(single, true, false);
        Title other = titleRepository.save(Title.builder().name("ZZ-OTHER").shortCode("ZZO").role(role).build());
        User colleague = person(other, true, false);

        UserDTO own = edit(holder, single);
        own.setFirstName("Renamed");
        assertThatCode(() -> userService.updateUser(own, holder.getId())).doesNotThrowAnyException();

        assertThatThrownBy(() -> userService.updateUser(edit(colleague, single), colleague.getId()))
                .isInstanceOf(TitleOccupiedException.class);
    }

    @Test
    void enablingOrUnblockingASecondHolderIsRefusedUntilTheFirstIsDisabled() throws Exception {
        User current = person(single, true, false);
        User former = person(single, false, false);
        User blocked = person(single, true, true);

        assertThatThrownBy(() -> userService.enableUser(former.getId())).isInstanceOf(TitleOccupiedException.class);
        assertThatThrownBy(() -> userService.unblockUser(blocked.getId())).isInstanceOf(TitleOccupiedException.class);

        current.setEnabled(false);
        userRepository.save(current);
        assertThatCode(() -> userService.enableUser(former.getId())).doesNotThrowAnyException();
    }

    @Test
    void verifyingASecondHolderIsRefusedAndLeavesTheAccountOff() {
        person(single, true, false);
        User pending = person(single, false, false);

        Map<String, Object> result = authenticationService.verifyAccount(jwtService.generateToken(pending));

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(userRepository.findById(pending.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void anImportCannotGiveOneLocationTwoHoldersEvenWithinTheFile() {
        BulkUserDTO a = row("a"), b = row("b");
        Map<String, Object> result = userService.bulkInsertUsers(List.of(a, b));

        assertThat(result.get("inserted")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
    }

    private BulkUserDTO row(String tag) {
        BulkUserDTO dto = new BulkUserDTO();
        dto.setFirstName("Zz" + tag);
        dto.setLastName("Import");
        dto.setEmail("zz.import." + tag + "@example.invalid");
        dto.setStaffNumber("ZZIMP" + tag);
        dto.setGender("male");
        dto.setTitle(single.getName());
        dto.setDutyStation(branch.getName());
        return dto;
    }

    @Test
    void aTitleCannotBecomeSingleHolderWhileALocationHasTwo() throws Exception {
        Title shared = titleRepository.save(Title.builder().name("ZZ-SHARED").shortCode("ZZS").role(role).build());
        User one = person(shared, true, false);
        person(shared, true, false);

        TitleRequestDTO request = new TitleRequestDTO();
        request.setName(shared.getName());
        request.setShortCode(shared.getShortCode());
        request.setRole(role.getId());
        request.setSingleHolder(true);

        assertThatThrownBy(() -> titleService.updateTitle(request, shared.getId()))
                .isInstanceOf(TitleOccupiedException.class)
                .hasMessageContaining(branch.getName());

        one.setBlocked(true);
        userRepository.save(one);
        assertThat(titleService.updateTitle(request, shared.getId()).isSingleHolder()).isTrue();
    }

    @Test
    void anUpdateWithoutTheFlagKeepsIt() throws Exception {
        TitleRequestDTO request = new TitleRequestDTO();
        request.setName(single.getName());
        request.setShortCode(single.getShortCode());
        request.setRole(role.getId());
        assertThat(titleService.updateTitle(request, single.getId()).isSingleHolder()).isTrue();
    }

    @Test
    void conflictsAlreadyInTheDataAreReported() {
        person(single, true, false);
        person(single, true, false);

        assertThat(occupancy.allConflicts())
                .anySatisfy(c -> {
                    assertThat(c.titleId()).isEqualTo(single.getId());
                    assertThat(c.branchId()).isEqualTo(branch.getId());
                    assertThat(c.holders()).hasSize(2);
                });
    }

    /**
     * Routing used an unordered lookup that included disabled accounts and took the first row.
     * Now: never a disabled or blocked holder, always the oldest active account, on leave included.
     */
    @Test
    void routingSkipsInactiveHoldersAndIsDeterministic() {
        Title many = titleRepository.save(Title.builder().name("ZZ-ROUTE").shortCode("ZZR").role(role).build());
        User disabled = person(many, false, false);
        User blocked = person(many, true, true);
        User older = person(many, true, false);
        User newer = person(many, true, false);

        List<Long> ids = userRepository.findActiveHoldersInBranch(many.getId(), branch.getId())
                .stream().map(User::getId).toList();
        assertThat(ids).containsExactly(older.getId(), newer.getId())
                .doesNotContain(disabled.getId(), blocked.getId());
    }
}
