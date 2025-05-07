package pride.bank.erp.auditing.role;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RoleAuditResponse {
    private String roleName;
    private int revisionNumber;
    private String revisionType; // ADD, MOD, DEL
    private LocalDateTime createDate;
    private String createdBy;
}
