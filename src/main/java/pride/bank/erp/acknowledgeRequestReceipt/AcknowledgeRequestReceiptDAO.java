package pride.bank.erp.acknowledgeRequestReceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.request.Request;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcknowledgeRequestReceiptDAO {
    private Long id;
    private String comment;
    private Request request;
    private User user;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;
}
