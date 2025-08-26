package pride.bank.erp.repair;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.asset.Asset;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Repair {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private LocalDateTime repairStartDate;
    private LocalDateTime repairEndDate;
    private String technician;
    private String repairReason;

    @ElementCollection
    @CollectionTable(name = "repair_documents", joinColumns = @JoinColumn(name = "repair_id"))
    @Column(name = "document")
    private List<String> documents;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id")
    @JsonIgnore
    private Asset asset;
}
