/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes for the stock-take API. */
public final class StockTakeDTOs {

    private StockTakeDTOs() {
    }

    /** Opens a count sheet for a store. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OpenCountRequest {
        private Long storeId;
        /** Optional: count only this asset category rather than the whole store. */
        private Long assetTypeId;
        /** Defaults to true. Set false only if your process deliberately shows expected figures. */
        private Boolean blind;
        private String notes;
    }

    /** One counted line. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CountedLine {
        private Long commodityId;
        private Integer countedQuantity;
        private AdjustmentReason reason;
        private String note;
    }

    /** Saves counted quantities. Sent repeatedly while counting; only the lines present are touched. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SaveCountsRequest {
        private List<CountedLine> lines;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReviewRequest {
        /** Required when sending a sheet back, so the counter knows what to redo. */
        private String remarks;
    }

    /** A line as the UI sees it. System figures are withheld while a blind count is in progress. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CountLineView {
        private Long lineId;
        private Long commodityId;
        private String commodityName;
        private String assetTypeName;
        private String unit;
        /** Null while a blind count is still being counted — revealed at review. */
        private Integer systemQuantity;
        private Integer countedQuantity;
        private Integer variance;
        private AdjustmentReason reason;
        private String note;
        private boolean recountRequired;
        private LocalDateTime countedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CountView {
        private Long id;
        private String reference;
        private Long storeId;
        private String storeName;
        private String assetTypeName;
        private StockCountStatus status;
        private boolean blind;
        /**
         * So the sheet can mirror the rule that a count is never approved by whoever counted it,
         * and offer the counter Cancel on their own unfinished count. The name alone cannot do it.
         */
        private Long countedById;
        private String countedByName;
        private String approvedByName;
        private LocalDateTime submittedAt;
        private LocalDateTime approvedAt;
        private LocalDateTime postedAt;
        private String notes;
        private String reviewRemarks;

        private int totalLines;
        private int countedLines;
        private int linesWithVariance;
        private int netUnitVariance;
        private boolean readyToSubmit;

        private List<CountLineView> lines;
    }

    /** One posted adjustment, for the variance report. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdjustmentView {
        private Long id;
        private String storeName;
        private String commodityName;
        private int quantityDelta;
        private int quantityBefore;
        private int quantityAfter;
        private AdjustmentReason reason;
        private String note;
        private String countReference;
        private LocalDateTime postedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReasonSummary {
        private AdjustmentReason reason;
        private long lines;
        private long netUnits;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VarianceReport {
        private List<ReasonSummary> byReason;
        private List<AdjustmentView> adjustments;
        private long totalLines;
        private long unitsWrittenUp;
        private long unitsWrittenDown;
    }
}
