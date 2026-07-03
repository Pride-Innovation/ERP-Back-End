package pride.bank.erp.workflow;

/**
 * Whose reporting line a role-based approval step climbs.
 *
 * <ul>
 *   <li>{@link #REQUESTER} — the ladder/role is resolved against the person who raised the request
 *       (the default, and how every step behaved before this option existed).</li>
 *   <li>{@link #ISSUER} — resolved against the person who issued the items (from the Issuance record).
 *       Used for issuance-approval steps so, e.g., "Manager" routes to the <em>issuer's</em> manager
 *       (the admin/stores manager) rather than the requester's. Falls back to the requester when no
 *       issuance exists yet.</li>
 * </ul>
 */
public enum ApproverSubject {
    REQUESTER,
    ISSUER
}
