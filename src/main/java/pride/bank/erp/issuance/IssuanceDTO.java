package pride.bank.erp.issuance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssuanceDTO {
    private Long requester;
    private List<IssuanceCommodityDTO> commodities;
    private String comment;
    private List<IssueAssetsDTO> assets;
}
