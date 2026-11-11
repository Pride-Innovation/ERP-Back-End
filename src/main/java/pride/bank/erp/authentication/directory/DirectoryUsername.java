/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

import pride.bank.erp.helper.ExtractNumberFromEmailAddress;

import java.util.Locale;

/**
 * The AD username (sAMAccountName) an account signs in as: the local part of its email, with any
 * trailing digits removed.
 *
 * <p>Several accounts may share one person's AD identity - {@code sodong@}, {@code sodong1@} and
 * {@code sodong12@} are kept distinct so one person can test as several users, and all three sign in
 * as {@code sodong}. The digits come off by the same rule the mail sender uses
 * ({@link ExtractNumberFromEmailAddress}), so an account signs in as the person its mail goes to.
 *
 * <p>Safe today because no real person's AD address ends in digits: in the November 2026 export only
 * Exchange system mailboxes and one meeting-room account did. If AD ever issues one (a second "jdoe"
 * as {@code jdoe2}), this rule signs that account in as {@code jdoe} - which is why the service also
 * checks the identity the API returns.
 */
public final class DirectoryUsername {

    private DirectoryUsername() {
    }

    public static String of(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String cleaned = new ExtractNumberFromEmailAddress(email.trim()).getCleanedEmail();
        String local = cleaned.substring(0, cleaned.indexOf('@')).trim();
        return local.isEmpty() ? null : local.toLowerCase(Locale.ROOT);
    }
}
