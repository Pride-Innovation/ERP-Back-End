package pride.bank.erp.appnotification;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppNotificationDTO {
    private Long id;
    private String title;
    private String message;
    private String type;
    /** What the notification is about; the frontend turns this pair into a route. */
    private String entityType;
    private Long entityId;
    /**
     * Whether the recipient has read it.
     *
     * <h2>Why the name is pinned rather than derived</h2>
     * Lombok gives a primitive {@code boolean isRead} the getter {@code isRead()}, and Jackson strips
     * the {@code is} prefix — so this shipped on the wire as **{@code "read"}** while the frontend,
     * and its TypeScript interface, both said {@code isRead}. The field was therefore **always
     * undefined in the browser**: every notification rendered as unread no matter how many times it
     * was marked, and since the page opens filtered to unread, nothing ever left the list.
     *
     * <p>The write was working the whole time. The row was updated, the badge — which comes from a
     * separate count endpoint returning a bare number — went down correctly, and the list did not.
     * That split is what made it look like "UI only".
     *
     * <p><b>The third encounter with this trap in this codebase, handled three different ways.</b>
     * {@code isHeadOffice} has consumers that check *both* spellings with a comment explaining why;
     * {@code isEnabled} and {@code isAccountNonLocked} have consumers that happen to read the
     * stripped name and work; this one guessed wrong. The lesson is not "remember the rule" — it is
     * that a wire name nobody chose is a wire name every consumer has to rediscover. Pinned, so it is
     * stated rather than derived.
     */
    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createDate;
}
