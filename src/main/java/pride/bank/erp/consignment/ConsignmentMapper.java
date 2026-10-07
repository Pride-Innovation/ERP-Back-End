package pride.bank.erp.consignment;

import org.springframework.stereotype.Component;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.courier.Courier;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementItem;
import pride.bank.erp.store.Store;
import pride.bank.erp.user.User;

import java.util.List;

/** Turns a {@link Consignment} into its wire shape, keeping entity graphs off the response. */
@Component
public class ConsignmentMapper {

    /** List shape: counts only, so a page of journeys does not load every movement's items. */
    public ConsignmentResponseDTO toSummary(Consignment consignment, int movementCount) {
        return base(consignment).movementCount(movementCount).build();
    }

    /** Detail shape: the same, plus what is riding on it. */
    public ConsignmentResponseDTO toDto(Consignment consignment, List<Movement> movements) {
        return base(consignment)
                .movementCount(movements.size())
                .movements(movements.stream().map(this::toMovementSummary).toList())
                .build();
    }

    private ConsignmentResponseDTO.ConsignmentResponseDTOBuilder base(Consignment c) {
        return ConsignmentResponseDTO.builder()
                .id(c.getId())
                .reference(c.getReference())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .sourceLocation(ref(c.getSourceLocation()))
                .destLocation(ref(c.getDestLocation()))
                .landingStore(ref(c.getLandingStore()))
                .courier(ref(c.getCourier()))
                .courierService(c.getCourierService())
                .trackingNumber(c.getTrackingNumber())
                .courierReference(c.getCourierReference())
                .plateNumber(c.getPlateNumber())
                .dispatchDate(c.getDispatchDate())
                .expectedDeliveryDate(c.getExpectedDeliveryDate())
                .arrivalDate(c.getArrivalDate())
                .deliveryDocuments(c.getDeliveryDocuments())
                .remarks(c.getRemarks())
                .initiator(person(c.getInitiator()))
                .receivingOfficer(person(c.getReceivingOfficer()))
                .createDate(c.getCreateDate());
    }

    private ConsignmentResponseDTO.MovementSummary toMovementSummary(Movement m) {
        String destination = m.getDestStore() != null
                ? m.getDestStore().getName()
                : (m.getRecipientUser() != null && m.getRecipientUser().getBranch() != null
                        ? m.getRecipientUser().getBranch().getName() : null);

        return ConsignmentResponseDTO.MovementSummary.builder()
                .id(m.getId())
                .movementType(m.getMovementType() != null ? m.getMovementType().name() : null)
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .requestId(m.getRequest() != null ? m.getRequest().getId() : null)
                .sourceStoreName(m.getSourceStore() != null ? m.getSourceStore().getName() : null)
                .destinationName(destination)
                .recipientName(displayName(m.getRecipientUser()))
                .itemCount(m.getItems() != null ? m.getItems().size() : 0)
                .items(m.getItems() == null ? List.of()
                        : m.getItems().stream().map(this::toItemLine).toList())
                .build();
    }

    private ConsignmentResponseDTO.ItemLine toItemLine(MovementItem item) {
        boolean isAsset = item.getAsset() != null;
        return ConsignmentResponseDTO.ItemLine.builder()
                .name(isAsset
                        ? item.getAsset().getAssetName()
                        : (item.getCommodity() != null ? item.getCommodity().getName() : null))
                // The engraved number is what a storekeeper reads off the item; serial is the
                // fallback for anything not yet engraved.
                .reference(isAsset
                        ? firstNonBlank(item.getAsset().getEngravedNumber(),
                                item.getAssetTag(), item.getSerialNumber())
                        : null)
                .kind(isAsset ? "Asset" : "Consumable")
                .quantity(isAsset ? 1 : item.getQuantity())
                .build();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private ConsignmentResponseDTO.RefSummary ref(Branch branch) {
        return branch == null ? null
                : ConsignmentResponseDTO.RefSummary.builder().id(branch.getId()).name(branch.getName()).build();
    }

    private ConsignmentResponseDTO.RefSummary ref(Store store) {
        return store == null ? null
                : ConsignmentResponseDTO.RefSummary.builder().id(store.getId()).name(store.getName()).build();
    }

    private ConsignmentResponseDTO.RefSummary ref(Courier courier) {
        return courier == null ? null
                : ConsignmentResponseDTO.RefSummary.builder().id(courier.getId()).name(courier.getName()).build();
    }

    private ConsignmentResponseDTO.PersonSummary person(User user) {
        return user == null ? null
                : ConsignmentResponseDTO.PersonSummary.builder()
                        .id(user.getId()).name(displayName(user)).build();
    }

    private String displayName(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? ("User " + user.getId()) : name;
    }
}
