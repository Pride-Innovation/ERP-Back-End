package pride.bank.erp.availability;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveDTO {
    private Long id;
    private String leaveType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String reason;
    private Long actingUser;
}
