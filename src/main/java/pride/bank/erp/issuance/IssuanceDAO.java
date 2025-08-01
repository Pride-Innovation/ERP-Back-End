package pride.bank.erp.issuance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.request.Request;
import pride.bank.erp.status.Status;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IssuanceDAO {
    private Long id;
    private String comment;
    private User requester;
    private Status status;
    private Request request;
    private User issuer;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;
}
