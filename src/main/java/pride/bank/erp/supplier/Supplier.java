package pride.bank.erp.supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.stock.Stock;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String contact;
    private String email;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.PERSIST)
    @JsonIgnore
    private List<Stock> stocks;
}
