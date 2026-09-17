/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import java.util.List;

/**
 * Which statuses an imported asset may legitimately start in.
 *
 * <p>The {@code status} table is shared across the whole application — it holds request states
 * ({@code requestApproved}), stock states ({@code stockClosedShort}) and approval-ladder states
 * ({@code hodApproved}) alongside asset states. Offering all of them in the import template would
 * let someone file a laptop as "BOM Approved", which means nothing and would put the asset in a
 * state no screen knows how to move it out of.
 *
 * <p>This list is the allow-list, and it is served to the frontend so the template dropdown and the
 * server-side check are the same set rather than two lists that drift apart.
 */
public final class AssetImportStatuses {

    /**
     * Asset-lifecycle status codes ({@code Status.status}), in the order they make sense to a person
     * reading the dropdown.
     */
    public static final List<String> IMPORTABLE = List.of(
            // The default for a row that names no status: flags an asset whose details still need
            // completing, which is the honest state for most of a legacy migration.
            "requireUpdate",
            "issuanceAvailable",
            "sentToStore",
            "assetAssigned",
            "assetIssued",
            "receiptAcknowledged",
            "inMaintenance",
            "inTransit",
            "pendingDisposal"
    );

    /** Applied when a row leaves Status blank. */
    public static final String DEFAULT = "requireUpdate";

    private AssetImportStatuses() {
    }

    public static boolean isImportable(String statusCode) {
        return statusCode != null && IMPORTABLE.contains(statusCode);
    }
}
