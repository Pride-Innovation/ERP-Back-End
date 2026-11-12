/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

/**
 * Where a title is used: at Head Office or at the branches. For filtering the Titles page only.
 *
 * <p>Deliberately not a branch reference. Branches share one set of titles, so "branch titles" is a
 * group, not a branch; and {@code Title.branch} is read by the front end as a fallback for a
 * <em>user's</em> duty station, so classifying titles through it would have moved users. Nothing
 * reads this for permissions, scope or approval routing.
 *
 * <p>Null means "not set" - not both, not neither. Stored as text, so a {@code BOTH} value can be
 * added later without a migration.
 */
public enum TitleWorkplace {
    HEAD_OFFICE,
    BRANCH
}
