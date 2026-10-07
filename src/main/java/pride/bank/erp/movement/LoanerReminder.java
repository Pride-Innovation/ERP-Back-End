/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.appnotification.NotificationTarget;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.user.User;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells somebody their loaner is due back, at the moment it becomes due.
 *
 * <p>Loaners are returned by hand now (see {@link LoanerService}), and the one real risk of that is
 * a loaner nobody remembers - the code this replaced recorded that already happening once, with the
 * pool running down. So when a repaired asset reaches its holder, each loaner that stood in for it is
 * named in a notification. Loaners issued without naming a repair are mentioned too: the system
 * cannot tell whether they were for this asset, and a reminder the holder can dismiss costs less
 * than one they never get.
 *
 * <p>A separate component rather than a method on {@link LoanerService}, because {@link
 * MovementService} calls it on completion and {@code LoanerService} creates movements - one class
 * would make the two depend on each other.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanerReminder {

    static final String TYPE = "LOANER_RETURN_DUE";

    private final AssetAssignmentHistoryRepository assignmentRepository;
    private final MovementRepository movementRepository;
    private final AppNotificationService notificationService;

    /**
     * Called as a movement completes. Acts only on a repaired asset being handed back to a person.
     *
     * <p>Never throws: a reminder failing to send must not fail the receipt it is attached to.
     */
    public void onCompleted(Movement movement) {
        if (movement.getMovementType() != MovementType.RETURN_AFTER_REPAIR) return;
        User holder = movement.getRecipientUser();
        if (holder == null) return;

        try {
            Set<Long> returned = movement.getItems().stream()
                    .map(MovementItem::getAsset).filter(Objects::nonNull)
                    .map(Asset::getId).collect(Collectors.toSet());

            for (AssetAssignmentHistory loan : assignmentRepository.findOpenLoansForUser(holder.getId())) {
                Asset loaner = loan.getAsset();
                Asset covered = movementRepository
                        .findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(loaner.getId(), MovementType.TEMP_REPLACEMENT)
                        .filter(m -> m.getRecipientUser() != null && m.getRecipientUser().getId().equals(holder.getId()))
                        .map(Movement::getRepair)
                        .map(pride.bank.erp.repair.Repair::getAsset)
                        .orElse(null);

                // A loaner tied to a different repair is not due yet - that asset is still away.
                if (covered != null && !returned.contains(covered.getId())) continue;

                String message = covered != null
                        ? LoanerService.label(covered) + " is back from repair. Please return the temporary "
                                + LoanerService.label(loaner) + " - open My Assets on your dashboard and choose Return."
                        : "An asset of yours is back from repair. If the temporary " + LoanerService.label(loaner)
                                + " was standing in for it, please return it from My Assets on your dashboard.";

                notificationService.createAndPush(holder, "Return your temporary asset", message,
                        TYPE, NotificationTarget.LOANER, loaner.getId());
            }
        } catch (Exception e) {
            log.warn("Could not send loaner reminders for movement {}: {}", movement.getId(), e.getMessage());
        }
    }
}
