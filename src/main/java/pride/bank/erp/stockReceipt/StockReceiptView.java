/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockReceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read model for a receipt and the lines it covered, as shown on the stock's Receipts tab. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReceiptView {

    private Long id;
    private LocalDateTime receiptDate;
    private String deliveryNoteNumber;
    private String invoiceNumber;
    private String notes;
    private String storeName;
    private String grnNumber;
    private Long grnReportId;
    private Long receivedBy;
    private String receivedByName;
    private int totalQuantityReceived;
    private List<Line> lines;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        private Long commodityId;
        private String commodityName;
        private int quantityReceived;
        private int orderedQuantity;
        private int cumulativeDelivered;
        private BigDecimal costPrice;
        private BigDecimal purchasePrice;
    }
}
