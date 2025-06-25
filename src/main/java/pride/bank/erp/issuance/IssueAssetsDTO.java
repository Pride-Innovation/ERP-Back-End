package pride.bank.erp.issuance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IssueAssetsDTO {
    private Long id;
    private String engravedNumber;
}
