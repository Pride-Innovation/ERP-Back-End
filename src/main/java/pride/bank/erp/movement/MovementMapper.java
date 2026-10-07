package pride.bank.erp.movement;

import org.springframework.stereotype.Component;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.courier.Courier;
import pride.bank.erp.department.Department;
import pride.bank.erp.request.Request;
import pride.bank.erp.store.Store;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;

import java.util.List;
import java.util.Objects;

/**
 * Turns a {@link Movement} into its wire shape.
 *
 * <p>Every mapping step is null-tolerant: most associations on a movement are optional (a return has
 * no source store, an intra-location move has no courier, a request-driven one has no approver), so
 * a missing link must map to {@code null} rather than blow up the whole response.
 */
@Component
public class MovementMapper {

    public MovementResponseDTO toDto(Movement m) {
        if (m == null) {
            return null;
        }
        return MovementResponseDTO.builder()
                .id(m.getId())
                .movementType(m.getMovementType())
                .movementCategory(m.getMovementCategory())
                .status(m.getStatus())
                .sourceStore(store(m.getSourceStore()))
                .sourceUser(user(m.getSourceUser()))
                .destStore(store(m.getDestStore()))
                .recipientUser(user(m.getRecipientUser()))
                .originHolder(user(m.getOriginHolder()))
                .originLocation(branch(m.getOriginLocation()))
                .courierService(m.getCourierService())
                .trackingNumber(m.getTrackingNumber())
                .courierReference(m.getCourierReference())
                .dispatchDate(m.getDispatchDate())
                .expectedDeliveryDate(m.getExpectedDeliveryDate())
                .courier(courier(m.getCourier()))
                .plateNumber(m.getPlateNumber())
                // Copied rather than passed through: the entity's list is a Hibernate collection
                // still attached to the session.
                .deliveryDocuments(m.getDeliveryDocuments() == null
                        ? List.of() : List.copyOf(m.getDeliveryDocuments()))
                .receivingOfficer(user(m.getReceivingOfficer()))
                .receiptDate(m.getReceiptDate())
                .receiptStatus(m.getReceiptStatus())
                .remarks(m.getRemarks())
                .initiator(user(m.getInitiator()))
                .currentApprover(user(m.getCurrentApprover()))
                .approvalTierIndex(m.getApprovalTierIndex())
                .approvalBypassed(m.isApprovalBypassed())
                .completionDate(m.getCompletionDate())
                .inventorySettled(m.isInventorySettled())
                .custodyTransferSettled(m.isCustodyTransferSettled())
                .request(request(m.getRequest()))
                .repair(m.getRepair() == null ? null
                        : MovementResponseDTO.RefSummary.builder().id(m.getRepair().getId()).build())
                .consultant(m.getConsultant() == null ? null
                        : MovementResponseDTO.RefSummary.builder()
                        .id(m.getConsultant().getId())
                        .name(m.getConsultant().getName())
                        .build())
                .consignment(m.getConsignment() == null ? null
                        : MovementResponseDTO.ConsignmentSummary.builder()
                        .id(m.getConsignment().getId())
                        .reference(m.getConsignment().getReference())
                        .status(m.getConsignment().getStatus() != null
                                ? m.getConsignment().getStatus().name() : null)
                        .build())
                .items(m.getItems() == null ? List.of()
                        : m.getItems().stream().filter(Objects::nonNull).map(this::item).toList())
                .createDate(m.getCreateDate())
                .lastModified(m.getLastModified())
                .createdBy(m.getCreatedBy())
                .lastModifiedBy(m.getLastModifiedBy())
                .build();
    }

    public List<MovementResponseDTO> toDtoList(List<Movement> movements) {
        return movements == null ? List.of() : movements.stream().map(this::toDto).toList();
    }

    /**
     * One approval-ladder decision. The record's {@code movement} is deliberately not carried over —
     * see {@link MovementApprovalRecordDTO} for why touching that proxy broke the trail endpoint.
     */
    public MovementApprovalRecordDTO toApprovalDto(MovementApprovalRecord r) {
        if (r == null) {
            return null;
        }
        return MovementApprovalRecordDTO.builder()
                .id(r.getId())
                .actor(user(r.getActor()))
                .action(r.getAction())
                .tierRole(r.getTierRole())
                .tierIndex(r.getTierIndex())
                .comment(r.getComment())
                .createDate(r.getCreateDate())
                .build();
    }

    public List<MovementApprovalRecordDTO> toApprovalDtoList(List<MovementApprovalRecord> records) {
        return records == null ? List.of()
                : records.stream().filter(Objects::nonNull).map(this::toApprovalDto).toList();
    }

    // ── Nested ──────────────────────────────────────────────────────────────

    private MovementResponseDTO.UserSummary user(User u) {
        if (u == null) {
            return null;
        }
        return MovementResponseDTO.UserSummary.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .otherName(u.getOtherName())
                .email(u.getEmail())
                .staffNumber(u.getStaffNumber())
                .branch(branch(u.getBranch()))
                .title(title(u.getTitle()))
                .department(department(u.getDepartment()))
                .build();
    }

    private MovementResponseDTO.StoreSummary store(Store s) {
        if (s == null) {
            return null;
        }
        return MovementResponseDTO.StoreSummary.builder()
                .id(s.getId())
                .name(s.getName())
                .storeType(s.getStoreType() == null ? null : s.getStoreType().name())
                .location(branch(s.getLocation()))
                .department(department(s.getDepartment()))
                .build();
    }

    private MovementResponseDTO.CourierSummary courier(Courier c) {
        if (c == null) {
            return null;
        }
        return MovementResponseDTO.CourierSummary.builder()
                .id(c.getId())
                .name(c.getName())
                .vetted(c.isVetted())
                .build();
    }

    private MovementResponseDTO.RequestSummary request(Request r) {
        if (r == null) {
            return null;
        }
        return MovementResponseDTO.RequestSummary.builder()
                .id(r.getId())
                .name(r.getName())
                .status(r.getStatus() == null ? null : r.getStatus().getStatus())
                .requesterId(r.getRequester() == null ? null : r.getRequester().getId())
                .build();
    }

    private MovementResponseDTO.ItemSummary item(MovementItem i) {
        return MovementResponseDTO.ItemSummary.builder()
                .id(i.getId())
                .asset(asset(i.getAsset()))
                .commodity(commodity(i.getCommodity()))
                .quantity(i.getQuantity())
                .serialNumber(i.getSerialNumber())
                .assetTag(i.getAssetTag())
                .build();
    }

    private MovementResponseDTO.AssetSummary asset(Asset a) {
        if (a == null) {
            return null;
        }
        return MovementResponseDTO.AssetSummary.builder()
                .id(a.getId())
                .assetName(a.getAssetName())
                .engravedNumber(a.getEngravedNumber())
                .serialNumber(a.getSerialNumber())
                .make(a.getMake())
                .model(a.getModel())
                .build();
    }

    private MovementResponseDTO.CommoditySummary commodity(Commodity c) {
        if (c == null) {
            return null;
        }
        return MovementResponseDTO.CommoditySummary.builder()
                .id(c.getId())
                .name(c.getName())
                .groupName(c.getGroupName())
                .build();
    }

    private MovementResponseDTO.RefSummary branch(Branch b) {
        return b == null ? null
                : MovementResponseDTO.RefSummary.builder().id(b.getId()).name(b.getName()).build();
    }

    private MovementResponseDTO.RefSummary department(Department d) {
        return d == null ? null
                : MovementResponseDTO.RefSummary.builder().id(d.getId()).name(d.getName()).build();
    }

    private MovementResponseDTO.RefSummary title(Title t) {
        return t == null ? null
                : MovementResponseDTO.RefSummary.builder().id(t.getId()).name(t.getName()).build();
    }
}
