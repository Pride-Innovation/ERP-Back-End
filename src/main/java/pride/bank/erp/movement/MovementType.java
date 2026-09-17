package pride.bank.erp.movement;

/**
 * The business reason for a movement. Determines which downstream effects the inventory ledger
 * applies and which workflow (if any) governs it.
 */
public enum MovementType {
    /** Replenish a branch's stock from Head Office (CLAUDE.md §13). */
    REPLENISHMENT,
    /** Fulfil an approved request whose source and destination are in different locations (§6). */
    ISSUANCE_FULFILLMENT,
    /** Reallocate inventory between departments within a location (§12). */
    DEPARTMENT_TRANSFER,
    /** Send a faulty asset to the Head Office IT store for repair (§14). */
    REPAIR_TRANSFER,
    /** Issue a temporary replacement asset from the IT store while a repair is ongoing (§15). */
    TEMP_REPLACEMENT,
    /** Return a repaired asset to its originating location (§16). */
    RETURN_AFTER_REPAIR,
    /** Move an irreparable / written-off asset to the Disposal store (§17). */
    DISPOSAL_TRANSFER,
    /**
     * A user hands an item back into a store — the counterpart of issuing it out.
     *
     * <p>The source is the user, not a store, so there is nothing to debit: the quantity already
     * left the store when it was issued. Completing it credits the destination store for a
     * consumable, or returns a serialized asset to that store and closes its assignment.
     */
    RETURN_TO_STORE
}
