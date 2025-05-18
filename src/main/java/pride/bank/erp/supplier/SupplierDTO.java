package pride.bank.erp.supplier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupplierDTO {
    private String name;
    private String address;
    private String telephone;
    private String email;
    private Long commodity;
}
