/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockReceipt;

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
import pride.bank.erp.stock.Stock;
import pride.bank.erp.store.Store;

import java.time.LocalDateTime;

/**
 * One <b>physical</b> delivery against a {@link Stock} order.
 *
 * <p>A purchase order is frequently delivered in several batches. Each batch that physically arrives
 * at a store is a receipt: it has its own date, its own supplier delivery note, its own receiving
 * officer, and produces exactly one GRN covering <i>every</i> line on that delivery — not one GRN
 * per commodity, which is what the previous per-row "Receive more" flow produced.
 *
 * <p>The receipt date matters beyond bookkeeping: assets created from a batch are stamped with
 * <i>this</i> receipt's date rather than the mutable {@code Stock.deliveryDate} header, so a later
 * batch can no longer rewrite the depreciation start date of an earlier one.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stock_receipt")
public class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order this batch was delivered against. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    @JsonIgnore
    private Stock stock;

    /** When the goods physically arrived. Basis for asset age/depreciation of this batch. */
    @Column(nullable = false)
    private LocalDateTime receiptDate;

    /** The supplier's own delivery-note / waybill reference for this batch. */
    private String deliveryNoteNumber;

    /** The supplier's invoice reference covering this batch, when one accompanied it. */
    private String invoiceNumber;

    /** Free-text condition/discrepancy note recorded by the receiving officer. */
    @Column(length = 1000)
    private String notes;

    /** The store the goods were received into. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    private Store store;

    /**
     * Client-supplied and stable per submission. A retried or double-clicked receipt resolves to
     * the receipt already recorded instead of receiving the same batch twice (which would inflate
     * the store balance, the asset register and the GRN trail all at once).
     */
    @Column(unique = true)
    private String idempotencyKey;

    @Builder.Default
    private Boolean disposed = false;
    private LocalDateTime disposalDate;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;

    /** The receiving officer — resolved from the authenticated user at receipt time. */
    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;
}
