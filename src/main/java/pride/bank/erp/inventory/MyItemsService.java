/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementItem;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.user.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The "My Items" page: what a person holds, what of theirs is away, and what is on its way to them.
 *
 * <h2>Why "away" needed a rule of its own</h2>
 * Sending an asset for repair closes the holder's assignment when Head Office receives it, so a list
 * of assigned assets loses the laptop the moment it reaches the bench, and the old Under Repair tile
 * could only count assets the older flow had left assigned. Measured: of the seven assets user 22 had
 * handed over, three still showed. Who handed an asset over is recorded on the transfer itself
 * ({@code Movement.originHolder}), and this reads it by the same rule as the detail page
 * ({@code MovementService#holderLastSentAwayFrom}), so an item listed here always opens.
 *
 * <p>The sections do not overlap: an asset away for repair is listed once, under Away, even when the
 * older flow left it assigned.
 */
@Service
@RequiredArgsConstructor
public class MyItemsService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentHistoryRepository assignmentHistoryRepository;
    private final MovementRepository movementRepository;

    @Transactional(readOnly = true)
    public MyItemsView forUser(User user) {
        if (user == null) return new MyItemsView(List.of(), List.of(), List.of());
        Long userId = user.getId();

        List<Movement> incoming = movementRepository.findOpenMovementsComingTo(userId);
        Set<Long> comingBack = incoming.stream()
                .flatMap(m -> m.getItems().stream())
                .map(MovementItem::getAsset)
                .filter(Objects::nonNull)
                .map(Asset::getId)
                .collect(Collectors.toSet());

        // Away first, keyed by asset, so the held list can leave those out.
        Map<Long, MyItemsView.AwayItem> away = new LinkedHashMap<>();
        for (Object[] row : movementRepository.findAwayAssetsSentBy(userId, AssetService.AWAY_FROM_BRANCH_CODES)) {
            Asset asset = (Asset) row[0];
            Movement transfer = (Movement) row[1];
            away.putIfAbsent(asset.getId(), toAway(asset, transfer, comingBack.contains(asset.getId())));
        }

        Set<Long> onLoan = assignmentHistoryRepository.findOpenLoansForUser(userId).stream()
                .map(AssetAssignmentHistory::getAsset)
                .filter(Objects::nonNull)
                .map(Asset::getId)
                .collect(Collectors.toSet());

        List<MyItemsView.HeldItem> held = new ArrayList<>();
        for (Asset asset : assetRepository.findAllByAssignedToIdWithTypeAndStatus(userId)) {
            if (Boolean.TRUE.equals(asset.getDisposed())) continue;
            if (AssetService.isAwayFromItsBranch(asset)) {
                // The older flow left some assets assigned while on the bench. They are away, not
                // in hand, and belong in that section even without a transfer naming the holder.
                away.putIfAbsent(asset.getId(), toAway(asset, null, comingBack.contains(asset.getId())));
                continue;
            }
            held.add(toHeld(asset, onLoan.contains(asset.getId())));
        }

        return new MyItemsView(held, new ArrayList<>(away.values()),
                incoming.stream().map(MyItemsService::toIncoming).toList());
    }

    private static MyItemsView.HeldItem toHeld(Asset a, boolean onLoan) {
        return new MyItemsView.HeldItem(
                a.getId(), a.getAssetName(), a.getModel(), a.getEngravedNumber(), a.getSerialNumber(),
                a.getAssetType() != null ? a.getAssetType().getId() : null,
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getAssetStatus() != null ? a.getAssetStatus().getStatus() : null,
                a.getAssetStatus() != null ? a.getAssetStatus().getName() : null,
                a.getBranch() != null ? a.getBranch().getName() : null,
                onLoan);
    }

    private static MyItemsView.AwayItem toAway(Asset a, Movement transfer, boolean returning) {
        String code = a.getAssetStatus() != null ? a.getAssetStatus().getStatus() : null;
        return new MyItemsView.AwayItem(
                a.getId(), a.getAssetName(), a.getEngravedNumber(),
                a.getAssetType() != null ? a.getAssetType().getId() : null,
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                code,
                a.getAssetStatus() != null ? a.getAssetStatus().getName() : null,
                "pendingDisposal".equals(code) ? "disposal" : "repair",
                a.getCurrentStore() != null ? a.getCurrentStore().getName() : null,
                transfer != null ? transfer.getCreateDate() : null,
                transfer != null ? transfer.getId() : null,
                returning);
    }

    private static MyItemsView.IncomingMovement toIncoming(Movement m) {
        List<String> names = m.getItems().stream()
                .map(i -> i.getAsset() != null ? i.getAsset().getAssetName()
                        : i.getCommodity() != null ? i.getCommodity().getName() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        String summary = names.isEmpty() ? null
                : names.size() <= 2 ? String.join(", ", names)
                : names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " more";
        String from = m.getSourceStore() != null ? m.getSourceStore().getName()
                : m.getSourceUser() != null ? fullName(m.getSourceUser()) : null;
        return new MyItemsView.IncomingMovement(
                m.getId(),
                m.getMovementType() != null ? m.getMovementType().name() : null,
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getTrackingNumber(), from, m.getItems().size(), summary, m.getCreateDate(),
                m.getRequest() != null ? m.getRequest().getId() : null);
    }

    private static String fullName(User u) {
        String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return name.isEmpty() ? null : name;
    }
}
