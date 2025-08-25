package pride.bank.erp.repair;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepairDTO {
    private LocalDateTime repairStartDate;
    private LocalDateTime repairEndDate;
    private String technician;
    private String repairReason;
}
