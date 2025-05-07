package pride.bank.erp.auditing.user;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserAuditResponse {
    private String email;
    private String firstName;
    private String lastName;
    private String address;
    private String phone;
    private String profileImage;
    private int revisionNumber;
    private String revisionType; // ADD, MOD, DEL
    private LocalDateTime createDate;
    private String createdBy;
}
