package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The wire shape of a movement.
 *
 * <p>The endpoints used to return the {@link Movement} entity itself. Because almost every
 * association on it is EAGER, Jackson walked the whole object graph and each party arrived carrying
 * their bcrypt password hash, granted authorities and full permission set, plus their title's entire
 * reporting chain up to the board — tens of kilobytes per movement, and credentials on the wire.
 *
 * <p>This carries only what the movement screens actually read. Nested records are deliberately
 * shallow: a party is a name and where they sit, not a user account.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovementResponseDTO {

    private Long id;
    private MovementType movementType;
    private MovementCategory movementCategory;
    private MovementStatus status;

    private StoreSummary sourceStore;
    private UserSummary sourceUser;
    private StoreSummary destStore;
    private UserSummary recipientUser;

    private UserSummary originHolder;
    private RefSummary originLocation;

    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private CourierSummary courier;
    private String plateNumber;
    private List<String> deliveryDocuments;

    private UserSummary receivingOfficer;
    private LocalDateTime receiptDate;
    private ReceiptStatus receiptStatus;
    private String remarks;

    private UserSummary initiator;
    private UserSummary currentApprover;
    private int approvalTierIndex;

    /** True when this movement proceeded with no approver available, on a confirmed exception. */
    private boolean approvalBypassed;
    private LocalDateTime completionDate;
    private boolean inventorySettled;
    private boolean custodyTransferSettled;

    private RequestSummary request;
    private RefSummary repair;
    private RefSummary consultant;

    /**
     * The journey this movement rides on, if any — {@code name} carries the consignment reference.
     *
     * <p>Present so a client can tell a movement that is already loaded from one still waiting, and
     * so the movement screens can explain why dispatch is unavailable: a consignment is dispatched
     * as a whole, never one movement at a time.
     */
    private ConsignmentSummary consignment;

    private List<ItemSummary> items;

    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;

    /** A bare {@code {id, name}} reference — used where the UI only ever shows or links the name. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RefSummary {
        private Long id;
        private String name;
    }

    /** A movement's journey: its reference and where it has got to. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConsignmentSummary {
        private Long id;
        private String reference;
        private String status;
    }

    /** A party to the movement. Never carries credentials, authorities or a reporting chain. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserSummary {
        private Long id;
        private String firstName;
        private String lastName;
        private String otherName;
        private String email;
        private String staffNumber;
        private RefSummary branch;
        private RefSummary title;
        private RefSummary department;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StoreSummary {
        private Long id;
        private String name;
        private String storeType;
        private RefSummary location;
        private RefSummary department;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CourierSummary {
        private Long id;
        private String name;
        private Boolean vetted;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestSummary {
        private Long id;
        private String name;
        private String status;
        /**
         * Who raised it.
         *
         * <p>Carried because being the requester is one of the ways a person is party to a movement,
         * and therefore one of the ways it counts as theirs at SELF scope. Without it the front end
         * cannot mirror the server's rule and would hide a control the server would have allowed.
         */
        private Long requesterId;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssetSummary {
        private Long id;
        private String assetName;
        private String engravedNumber;
        private String serialNumber;
        private String make;
        private String model;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommoditySummary {
        private Long id;
        private String name;
        private String groupName;
    }

    /** One line on the movement — exactly one of {@code asset} / {@code commodity} is set. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ItemSummary {
        private Long id;
        private AssetSummary asset;
        private CommoditySummary commodity;
        private int quantity;
        private String serialNumber;
        private String assetTag;
    }
}
