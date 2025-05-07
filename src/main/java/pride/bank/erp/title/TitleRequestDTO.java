package pride.bank.erp.title;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TitleRequestDTO {
    private String name;
    private Long reportsToID;
}
