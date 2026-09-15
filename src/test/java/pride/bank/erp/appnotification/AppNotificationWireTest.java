package pride.bank.erp.appnotification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a notification looks like on the wire.
 *
 * <h2>The bug this exists to stop coming back</h2>
 * Lombok gives a primitive {@code boolean isRead} the getter {@code isRead()}, and Jackson strips the
 * {@code is} prefix — so the API published <b>{@code "read"}</b> while the frontend, and its
 * TypeScript interface, both said {@code isRead}. The field was therefore always {@code undefined} in
 * the browser: every notification rendered as unread however many times it was marked, and because
 * the page opens filtered to unread, nothing ever left the list.
 *
 * <p>The write worked the whole time — the row was updated and the badge went down, because the count
 * comes from a separate endpoint returning a bare number. Only the list was wrong, which is exactly
 * what made it look like the tick was "UI only".
 *
 * <p>Nothing in a type checker can see this: it is a name agreed between two languages, and neither
 * side declares it. Only a test that reads the actual JSON can.
 */
@SpringBootTest
class AppNotificationWireTest {

    @Autowired ObjectMapper mapper;

    private JsonNode serialise() throws Exception {
        AppNotificationDTO dto = AppNotificationDTO.builder()
                .id(1L)
                .title("Request Rejected")
                .message("Sent back for correction")
                .type("STEP_REJECTED")
                .entityType("REQUEST")
                .entityId(37L)
                .isRead(true)
                .build();
        return mapper.readTree(mapper.writeValueAsString(dto));
    }

    /** The name the browser reads. Derived, it was `read`; pinned, it is what both sides say. */
    @Test
    void readFlagIsPublishedAsIsRead() throws Exception {
        JsonNode json = serialise();

        assertThat(json.has("isRead"))
                .as("the frontend reads `isRead`; published as `read` it was always undefined, so "
                        + "every notification looked unread forever")
                .isTrue();
        assertThat(json.get("isRead").asBoolean()).isTrue();

        assertThat(json.has("read"))
                .as("and the derived spelling must not also appear — two names for one flag is how "
                        + "consumers come to disagree about which is authoritative")
                .isFalse();
    }

    /**
     * The target the frontend resolves into a route.
     *
     * <p>These replaced a {@code link} holding a URL built in Java, of which <b>744 of 760 pointed at
     * a route that did not exist</b>. The pair has to survive, under these names, or the notification
     * goes back to leading nowhere.
     */
    @Test
    void theTargetIsCarriedAsTypeAndId() throws Exception {
        JsonNode json = serialise();

        assertThat(json.get("entityType").asText()).isEqualTo("REQUEST");
        assertThat(json.get("entityId").asLong()).isEqualTo(37L);

        assertThat(json.has("link"))
                .as("a URL built in Java cannot know when a frontend route is renamed — that is what "
                        + "produced 744 dead links, and the column is gone")
                .isFalse();
        assertThat(json.has("requestId"))
                .as("`requestId` held a *movement* id for every movement notification; `entityId` "
                        + "says what it means")
                .isFalse();
    }

    /** Both surfaces read the same DTO, so the live push cannot drift from the fetched list. */
    @Test
    void theWebsocketPushUsesTheSameShape() {
        String source = read("src/main/java/pride/bank/erp/appnotification/AppNotificationService.java");

        assertThat(source)
                .as("the pushed payload must be the same DTO the listing returns, or a notification "
                        + "would arrive in one shape and reload in another")
                .contains("AppNotificationDTO dto = toDTO(saved)")
                .contains("convertAndSendToUser");
    }

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
