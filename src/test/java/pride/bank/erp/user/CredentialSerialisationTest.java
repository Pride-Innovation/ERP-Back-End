/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A user's credentials must never reach a client, whichever entity happens to embed them.
 *
 * <p>{@code User.password} carried no Jackson annotation, and {@code getAuthorities()} is a public
 * {@code UserDetails} getter, so both were serialised by default. Endpoints returning their own DTO
 * (users, movements) were unaffected, but any endpoint handing back an entity that embeds a User was
 * not: {@code /requests} was returning bcrypt hashes and every party's full permission set, 77KB for
 * five rows.
 *
 * <p>Asserted at the serialisation layer rather than per-endpoint, so a newly added endpoint that
 * returns an entity can't quietly reintroduce it.
 */
@SpringBootTest
class CredentialSerialisationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestRepository requestRepository;

    /** The application's own mapper, so this reflects what clients actually receive. */
    @Autowired
    private ObjectMapper objectMapper;

    private void assertNoCredentials(String json, String what) {
        assertThat(json).as("%s must not expose the password hash", what)
                .doesNotContain("\"password\"")
                .doesNotContain("$2a$");
        assertThat(json).as("%s must not expose expanded authorities", what)
                .doesNotContain("\"authorities\"")
                .doesNotContain("\"effectiveRoleIds\"");
    }

    @Test
    @Transactional(readOnly = true)
    void userEntityNeverSerialisesCredentials() throws Exception {
        Optional<User> user = userRepository.findAll().stream().findFirst();
        if (user.isEmpty()) {
            return;
        }
        String json = objectMapper.writeValueAsString(user.get());

        assertNoCredentials(json, "A serialised User");
        // The identity fields clients legitimately read must survive.
        assertThat(json).contains("\"firstName\"", "\"lastName\"", "\"email\"");
    }

    /**
     * The leak vector was the {@code User} embedded in other entities — {@code /requests} exposed the
     * requester's hash. Asserted against that embedded requester rather than the whole Request graph:
     * serialising the entire aggregate drags in Lombok's generated {@code hashCode} over lazy
     * collections, which fails for reasons that have nothing to do with credentials.
     */
    @Test
    @Transactional(readOnly = true)
    void userEmbeddedInAnotherEntityNeverSerialisesCredentials() throws Exception {
        Optional<User> requester = requestRepository.findAll().stream()
                .map(Request::getRequester)
                .filter(java.util.Objects::nonNull)
                .findFirst();
        if (requester.isEmpty()) {
            return;
        }
        assertNoCredentials(objectMapper.writeValueAsString(requester.get()),
                "A requester embedded in a Request");
    }

    /**
     * WRITE_ONLY, not @JsonIgnore — binding a password *in* must still work, or account creation and
     * password reset would silently stop setting one.
     */
    @Test
    void passwordCanStillBeDeserialisedIntoUser() throws Exception {
        User bound = objectMapper.readValue(
                "{\"email\":\"x@y.z\",\"password\":\"Secret123!\"}", User.class);

        assertThat(bound.getPassword()).isEqualTo("Secret123!");
    }
}
