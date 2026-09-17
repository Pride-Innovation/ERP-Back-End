/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

/**
 * Where a faulty asset of a given category is sent for repair (CLAUDE.md movement flow §14).
 * Configured per {@link AssetType} so, for example, Computers route to the in-house IT store while
 * Furniture routes to the Admin store or an external consultant — "IT does not repair furniture".
 */
public enum RepairDestination {
    /** In-house electronics/IT workshop — the Head Office IT store. */
    IT,
    /** In-house administration/facilities team — the Head Office Admin store. */
    ADMIN,
    /** An external repair consultant/vendor; the movement links a {@code Consultant} and requires documents. */
    EXTERNAL
}
