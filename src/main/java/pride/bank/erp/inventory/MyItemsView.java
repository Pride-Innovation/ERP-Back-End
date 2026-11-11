/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the signed-in person is accountable for, in three parts: what they hold, what of theirs is
 * away for repair or disposal, and what is on its way to them.
 *
 * <p>A short summary rather than the asset entity: the page needs a dozen fields, and the entity
 * brings the whole object graph (stock, supplier, holder) with it.
 */
public record MyItemsView(List<HeldItem> held, List<AwayItem> away, List<IncomingMovement> incoming) {

    /** An asset assigned to them now. {@code onLoan} means an open temporary assignment. */
    public record HeldItem(
            Long id, String name, String model, String engravedNumber, String serialNumber,
            Long assetTypeId, String assetTypeName, String statusCode, String statusName,
            String branchName, boolean onLoan) {}

    /**
     * An asset they handed over that is still away. {@code purpose} is "repair" or "disposal";
     * {@code heldAt} is the store it is in. {@code returning} is true while a movement is bringing it
     * back to them.
     */
    public record AwayItem(
            Long id, String name, String engravedNumber, Long assetTypeId, String assetTypeName,
            String statusCode, String statusName, String purpose, String heldAt,
            LocalDateTime since, Long movementId, boolean returning) {}

    /** A movement not yet completed that is addressed to them, or fulfils a request they raised. */
    public record IncomingMovement(
            Long id, String movementType, String status, String trackingNumber, String fromName,
            int itemCount, String itemSummary, LocalDateTime createDate, Long requestId) {}
}
