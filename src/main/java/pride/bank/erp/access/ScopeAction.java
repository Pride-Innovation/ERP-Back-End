/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.access;

/**
 * Whether a check is about reading records or changing them.
 *
 * <p>Separate because the bank's answer differs. An Infra officer may legitimately need sight of
 * every branch's equipment while having no business editing a branch's records, and a model with a
 * single "cross-branch" switch cannot express that without either over-granting or blocking the
 * report the officer actually needs.
 */
public enum ScopeAction {
    VIEW,
    MANAGE
}
