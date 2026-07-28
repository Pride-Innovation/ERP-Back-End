package pride.bank.erp.grnReport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stockReceipt.StockReceipt;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
public class GrnReport {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // The GRN number, issued server-side from the report's own id (monotonic + unique).
    // NOTE: ddl-auto=update does NOT reliably add this unique index — verify on the DB.
    @Column(unique = true)
    private String name;
    private String documentPath;
    @Builder.Default
    private Boolean disposed = false;
    private LocalDateTime disposalDate;
    private Boolean grnDownloaded;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    @JsonIgnore
    private Stock stock;

    /**
     * The physical delivery this GRN documents — one GRN per receipt, covering every line on that
     * delivery note. Nullable because GRNs issued before receipts existed have no batch to point at;
     * {@code @NotAudited} keeps the column off {@code grn_report_aud}, since the receipt carries its
     * own audit stamps.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_receipt_id")
    @NotAudited
    @JsonIgnore
    private StockReceipt stockReceipt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;
}
