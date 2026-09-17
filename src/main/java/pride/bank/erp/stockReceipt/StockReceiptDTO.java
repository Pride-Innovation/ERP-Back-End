/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockReceipt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.stockCommodity.StockCommodityDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request body for recording one physical delivery against a stock order.
 *
 * <p>{@code lines} carries the quantity received <b>in this batch</b> (a delta), never the new
 * cumulative total — the server adds it to what has already been received.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockReceiptDTO {

    /** Quantities received now, per commodity. Lines with a zero quantity are ignored. */
    private List<StockCommodityDTO> lines;

    /** When the goods physically arrived. Defaults to now when omitted. */
    private LocalDateTime receiptDate;

    private String deliveryNoteNumber;
    private String invoiceNumber;
    private String notes;

    /** Stable per submission; a replay of the same key returns the original receipt unchanged. */
    private String idempotencyKey;
}
