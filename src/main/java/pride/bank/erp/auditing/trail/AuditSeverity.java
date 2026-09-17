/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

/**
 * How much attention an event deserves.
 *
 * <p>Set at the call site, not derived, because only the call site knows. Creating an asset and
 * deleting one are both {@code UPDATED}-ish bookkeeping to a generic rule; one of them is routine and
 * the other is worth a second look. The "Flagged Events" tile counts everything above {@link #INFO}.
 */
public enum AuditSeverity {
    /** Routine business. The overwhelming majority of rows. */
    INFO,
    /** Reversible but worth noticing: deletions, refused logins, disposals, discrepancies. */
    WARNING,
    /** Someone should look at this: auto-blocked accounts, bypassed approvals, permission changes. */
    CRITICAL
}
