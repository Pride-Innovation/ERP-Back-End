package pride.bank.erp.appnotification;

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
    private Long requestId;
    private String link;
    private boolean isRead;
    private LocalDateTime createDate;
}
