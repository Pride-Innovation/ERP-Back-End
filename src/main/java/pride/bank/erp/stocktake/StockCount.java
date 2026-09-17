/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.store.Store;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One physical count of a store's consumable stock.
 *
 * <p>Counting is <b>blind</b>: the counter records what is on the shelf without seeing what the
 * system thinks is there. Showing the expected figure invites confirming it rather than counting,
 * which is the difference between a stock take that finds problems and one that rubber-stamps them.
 * System quantities are revealed at variance review.
 *
 * <p>Assets are deliberately out of scope for now — a serialized asset is <i>verified present</i>
 * item by item rather than counted, which is a different workflow.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stock_count")
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference, e.g. {@code SC/2026/000012}. Issued server-side from the id. */
    @Column(unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Optional narrowing: count only one asset category rather than the whole store. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'DRAFT'")
    @Builder.Default
    private StockCountStatus status = StockCountStatus.DRAFT;

    /**
     * Whether system quantities are withheld from the counter. Defaults to true; recorded per count
     * so the variance report can show whether a given count was done blind.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean blind = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "counted_by_id")
    private User countedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime postedAt;

    @Column(length = 1000)
    private String notes;

    /** Populated when an approver sends the sheet back, so the counter can see why. */
    @Column(length = 1000)
    private String reviewRemarks;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<StockCountLine> lines = new ArrayList<>();

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
