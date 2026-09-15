package pride.bank.erp.branch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserResponseDTO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names boolean flags travel under.
 *
 * <h2>The trap</h2>
 * Lombok gives a primitive {@code boolean isX} the getter {@code isX()}, and Jackson strips the
 * {@code is} prefix — so the field lands on the wire as {@code x}. A boxed {@code Boolean isX} gets
 * {@code getIsX()} and keeps its name, which is why only some of these are affected and the rule
 * looks arbitrary from the outside.
 *
 * <h2>Why a test and not a convention</h2>
 * This codebase met the trap <b>four separate times and resolved it four different ways</b>:
 *
 * <ul>
 *   <li>{@code BranchWithManagersDTO.isHeadOffice} — pinned at the source, with a comment. Correct,
 *       and never propagated to {@link Branch} itself.</li>
 *   <li>Two request-page readers — check <em>both</em> spellings defensively, with a comment.</li>
 *   <li>{@code isEnabled} / {@code isAccountNonLocked} — consumers happen to read the stripped name,
 *       so they work by discovery.</li>
 *   <li>{@code isRead}, and three readers of {@code Branch.isHeadOffice} — read the Java name, got
 *       {@code undefined}, and failed silently.</li>
 * </ul>
 *
 * <p>Four encounters, four answers, and nothing to stop a fifth. Nothing in a type checker can see
 * this: it is a name agreed between two languages where neither side declares it. Only a test that
 * reads the JSON can.
 */
@SpringBootTest
class BooleanWireNameTest {

    @Autowired ObjectMapper mapper;
    @Autowired BranchRepository branches;
    @Autowired UserRepository users;

    /**
     * Three readers were broken by this one, each differently and each in silence.
     *
     * <p>The store page's Head Office fallback could never find a branch, the "HQ" badge never
     * rendered, and the user form's rule <em>"Department is required for Head Office staff"</em>
     * never fired — measured, two of eighteen Head Office staff have no department recorded.
     */
    @Test
    void headOfficeFlagTravelsAsIsHeadOffice() throws Exception {
        Branch branch = branches.findAll().stream()
                .filter(Branch::isHeadOffice)
                .findFirst()
                .orElseGet(() -> branches.findAll().get(0));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(branch));

        assertThat(json.has("isHeadOffice"))
                .as("the front end asks for `isHeadOffice`; published as `headOffice` it was "
                        + "undefined, and three separate readers failed without a word")
                .isTrue();
        assertThat(json.has("headOffice"))
                .as("and not both — two names for one flag is how readers come to disagree about "
                        + "which is authoritative, which is exactly what happened here")
                .isFalse();
    }

    /** The same flag reaches the front end nested inside a user, and must agree with itself. */
    @Test
    void theNestedBranchFlagAgreesWithTheBranchItself() throws Exception {
        User user = users.findAll().stream()
                .filter(u -> u.getBranch() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no user has a branch to serialise"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(UserResponseDTO.from(user)));

        assertThat(json.path("branch").has("isHeadOffice"))
                .as("a flag that means one thing on /branches and another inside a user is worse "
                        + "than one that is simply wrong")
                .isTrue();
    }

    /** Pinned while it has no readers — the cheapest moment to state a name rather than derive it. */
    @Test
    void actingFlagTravelsAsIsActing() throws Exception {
        JsonNode json = mapper.readTree(
                mapper.writeValueAsString(UserResponseDTO.from(users.findAll().get(0))));

        assertThat(json.has("isActing")).isTrue();
        assertThat(json.has("acting")).isFalse();
    }

    /**
     * The two that are deliberately left derived.
     *
     * <p>{@code enabled} and {@code accountNonLocked} are read under those names by the profile page
     * today, so pinning them to the Java spelling would break every working reader to fix nothing.
     * Fixing a name is worth it where it is wrong; standardising a surface that works is not.
     *
     * <p>Asserted rather than assumed, so that a later tidy-up of "inconsistent" names has to come
     * here and read why first.
     */
    @Test
    void theWorkingOnesAreLeftAlone() throws Exception {
        JsonNode json = mapper.readTree(
                mapper.writeValueAsString(UserResponseDTO.from(users.findAll().get(0))));

        assertThat(json.has("enabled")).isTrue();
        assertThat(json.has("accountNonLocked")).isTrue();
    }
}
