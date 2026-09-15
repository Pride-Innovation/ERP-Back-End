/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import pride.bank.erp.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;

/**
 * What a password has to be, in the one place both ways of setting one can read it.
 *
 * <h2>There was no policy at all</h2>
 * {@code changePassword} checked exactly two things: that the old password matched, and that the new
 * one differed from it. A single character passed. The reset flow checked nothing whatever. For a
 * bank that is the conspicuous gap — everything else about the account model is careful, and the
 * credential itself was unconstrained.
 *
 * <h2>Both paths, one rule</h2>
 * A person can arrive at a new password two ways — changing it while signed in, or following a reset
 * link — and a rule enforced on one of them is not a rule. They were separate code in separate
 * packages with no shared notion of validity, which is exactly how the two come to disagree.
 *
 * <h2>Why these rules</h2>
 * Length does most of the work, so the minimum is the part worth being firm about. The character
 * classes are the conventional three of four rather than all four: requiring every class pushes
 * people toward predictable substitutions and a symbol bolted on the end, which buys less than the
 * extra length does. Whitespace at the ends is trimmed nowhere and rejected here, because a trailing
 * space is invisible and turns into a lockout nobody can diagnose.
 *
 * <p><b>Every failure is reported at once.</b> Telling somebody their password is too short, letting
 * them fix that, and then telling them it needs a digit is how a person ends up on their fourth
 * attempt; the message names everything that is wrong the first time.
 */
public final class PasswordPolicy {

    /** Long enough to matter, short enough that people do not write it down. */
    public static final int MIN_LENGTH = 10;

    /** Guards against a paste accident rather than against an attacker. */
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() { }

    /**
     * @throws BadRequestException naming every rule the candidate fails
     */
    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new BadRequestException("Enter a new password.");
        }

        List<String> failures = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            failures.add("be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            failures.add("be no longer than " + MAX_LENGTH + " characters");
        }
        if (!password.equals(password.trim())) {
            // Invisible on screen, and a lockout the person cannot see the cause of.
            failures.add("not start or end with a space");
        }

        int classes = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) classes++;

        if (classes < 3) {
            failures.add("include at least three of: a capital letter, a small letter, a number, "
                    + "and a symbol");
        }

        if (!failures.isEmpty()) {
            throw new BadRequestException("Your password must " + String.join("; ", failures) + ".");
        }
    }
}
