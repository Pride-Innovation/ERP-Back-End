package pride.bank.erp.consignment;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wire shape for a consignment.
 *
 * <p>A DTO rather than the entity for the same reason movements need one: serialising the entity
 * would drag every movement, and through them users, titles and reporting lines, onto the wire —
 * and a {@code User} carries credentials. Everything here is flat and named.
 */
@Data
@Builder
public class ConsignmentResponseDTO {

    private Long id;
    private String reference;
    private String status;

    private RefSummary sourceLocation;
    private RefSummary destLocation;
    private RefSummary landingStore;

    private RefSummary courier;
    private String courierService;
    private String trackingNumber;
    private String plateNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private LocalDateTime arrivalDate;
    private List<String> deliveryDocuments;

    private String remarks;
    private PersonSummary initiator;
    private PersonSummary receivingOfficer;

    /** How many movements ride on this journey — enough for a list row without loading them. */
    private int movementCount;

    /**
     * The movements themselves. Populated only on the detail read; a list response leaves this null
     * so a page of consignments does not fan out into every movement's item lines.
     */
    private List<MovementSummary> movements;

    private LocalDateTime createDate;

    @Data
    @Builder
    public static class RefSummary {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    public static class PersonSummary {
        private Long id;
        private String name;
    }

    /** Just enough of a movement to list it inside its journey and link through to it. */
    @Data
    @Builder
    public static class MovementSummary {
        private Long id;
        private String movementType;
        private String status;
        private Long requestId;
        private String sourceStoreName;
        private String destinationName;
        private String recipientName;
        private int itemCount;

        /**
         * The lines themselves, so the dispatch note can list everything on the van.
         *
         * <p>Carried on the detail read because the note has to be printed and signed <i>before</i>
         * dispatch — fetching each movement separately to build it would mean one round trip per
         * request on the load.
         */
        private List<ItemLine> items;
    }

    /** One thing being carried, flattened for printing. */
    @Data
    @Builder
    public static class ItemLine {
        private String name;
        /** Engraved number or serial for an asset; blank for a consumable. */
        private String reference;
        /** {@code Asset} or {@code Consumable} — what the storekeeper is counting. */
        private String kind;
        private int quantity;
    }
}
