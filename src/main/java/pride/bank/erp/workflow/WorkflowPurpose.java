package pride.bank.erp.workflow;

/**
 * Which kind of request a workflow serves (V30).
 *
 * <p>A request is either for the requester personally or to restock their branch store. The two are
 * routed differently: a branch can issue personal stationery from its own store, but a restock of that
 * store has to come from Head Office. Without this a restock request for Stationery would match the
 * branch workflow and be "issued" from the very store it was meant to fill.
 */
public enum WorkflowPurpose {
    PERSONAL,
    RESTOCK,
    /** Serves both. Every workflow that existed before V30 starts here. */
    BOTH;

    /** The purpose a request carries: one of the two concrete kinds, never BOTH. */
    public static WorkflowPurpose of(boolean forStoreRestock) {
        return forStoreRestock ? RESTOCK : PERSONAL;
    }
}
