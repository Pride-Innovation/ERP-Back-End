package pride.bank.erp.movement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards two things that were both broken by returning the {@link Movement} entity directly.
 *
 * <p><b>The load.</b> {@code findById} pulls every EAGER association in one select. Movement has
 * nine, six of them {@code User}, and {@code Title.reportsTo} is EAGER and self-referencing — the
 * plan reached ~70 joined tables against MySQL's hard limit of 61, so it failed with "Too many
 * tables". That took out the detail view *and* every lifecycle action, and because the SQL died
 * before the row was read, a missing id returned 500 instead of 404. There is very little headroom
 * left, so any new EAGER association can push it back over: this test fails loudly if it does.
 *
 * <p><b>The payload.</b> Serializing the entity walked that same graph, putting every party's bcrypt
 * password hash, authorities and permission set on the wire.
 */
@SpringBootTest
class MovementDetailLoadTest {

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private MovementMapper movementMapper;

    /** The application's own mapper, so this asserts against the JSON clients actually receive. */
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional(readOnly = true)
    void missingIdReturnsEmptyRatherThanFailingTheQuery() {
        // Must not throw: the caller relies on an empty Optional to raise a 404.
        assertThat(movementRepository.findDetailById(-1L)).isEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void existingMovementLoadsAndSerialisesWithoutCredentials() throws Exception {
        Optional<Movement> first = movementRepository.findAll(PageRequest.of(0, 1)).stream().findFirst();
        if (first.isEmpty()) {
            return; // nothing seeded to assert against
        }

        Long id = first.get().getId();
        Optional<Movement> loaded = movementRepository.findDetailById(id);
        assertThat(loaded).as("detail load for movement %s", id).isPresent();

        String json = objectMapper.writeValueAsString(movementMapper.toDto(loaded.get()));

        assertThat(json)
                .as("movement payload must never carry credentials or authorisation data")
                .doesNotContain("password")
                .doesNotContain("authorities")
                .doesNotContain("permissions")
                .doesNotContain("$2a$"); // bcrypt hash prefix

        // Fields the movement screens actually read.
        assertThat(json).contains("\"id\"", "\"movementType\"", "\"status\"", "\"items\"");
    }
}
