/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

/**
 * The shape of an audited event, deliberately coarse.
 *
 * <p>This is the "what kind of thing happened" axis — the fine detail lives in
 * {@link AuditEvent#getAction()} ({@code MOVEMENT_APPROVAL_BYPASSED}) and the sentence in
 * {@code description}. Kept coarse because it is a filter on a screen: a user narrowing to
 * "everything deleted last week" wants eight choices, not eighty.
 *
 * <p>The names match the chips the Audit Trails page already renders, so the enum and the UI share
 * one vocabulary rather than needing a translation table between them.
 */
public enum AuditEventType {
    /** A record came into existence. */
    CREATED,
    /** An existing record changed. */
    UPDATED,
    /** A record was removed, soft-deleted or voided. */
    DELETED,
    /** A session began. Invisible to Envers — logging in changes no entity. */
    LOGIN,
    /** A session was ended deliberately. See {@code LogoutService} for why some are never seen. */
    LOGOUT,
    /** A decision went in favour: an approval, an acknowledgement, a receipt. */
    APPROVED,
    /** A decision went against, or an attempt was refused. */
    REJECTED,
    /** The application acted on its own — seeders, scheduled jobs, cascades. */
    SYSTEM
}
