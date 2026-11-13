/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exception;

/**
 * A single-holder title already has an active holder at that location, or a title cannot become
 * single-holder because some location already has more than one. Answered as 409 with the message
 * as written - it names who holds the title, which is what the reader needs to act on.
 *
 * <p>Deliberately carries no {@code errorCode}: the front end's interceptor toasts any refusal
 * without one, so every screen that can hit this (user form, enable, unblock, import, title form)
 * shows the reason without each having to learn a new code.
 */
public class TitleOccupiedException extends RuntimeException {
    public TitleOccupiedException(String message) {
        super(message);
    }
}
