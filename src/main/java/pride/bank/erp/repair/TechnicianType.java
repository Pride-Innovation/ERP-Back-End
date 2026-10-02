/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.repair;

/**
 * Who worked on a repair: somebody on staff, or a vendor.
 *
 * <p>Chosen first on every form that records a technician, because the two are picked from
 * different lists - an internal officer from the unit that services the repair's routing, an
 * external one from the consultants registered in Settings - and a single free-text box let either
 * be typed as the other.
 */
public enum TechnicianType {
    /** A member of the Infra unit (IT repairs) or the Admin unit (Admin repairs). */
    INTERNAL,
    /** A repair consultant registered under Settings. */
    EXTERNAL
}
