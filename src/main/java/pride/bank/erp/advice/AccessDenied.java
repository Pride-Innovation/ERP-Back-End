/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import org.springframework.http.HttpStatus;

/**
 * How this application says "you may not do that".
 *
 * <h2>Why it is not 403</h2>
 * The web application firewall in front of this service <b>blocks 403 responses</b> — it passes every
 * other 4xx and replaces that one with its own block page. So a refusal sent as 403 never reaches the
 * browser: the caller sees the firewall's HTML instead of the reason, and the front end sees a body
 * it cannot parse. Every refusal this codebase is careful to phrase well — "You can only make changes
 * to records at your own branch", "This asset was sent for repair by X" — was being thrown away at
 * the last hop.
 *
 * <h2>The status is transport, the meaning is in the body</h2>
 * 403 is the semantically correct code and it is unavailable, so {@link #STATUS} is a choice about
 * what survives the firewall rather than a claim about the failure. It must therefore not be read as
 * one: <b>{@link #ERROR_CODE} is what identifies a refusal</b>, and it is what callers branch on.
 * 422 was picked because it is the only 4xx this application does not already use for something else
 * — 400, 401, 404 and 409 all carry other meanings here, and reusing one would make a refusal
 * indistinguishable from a validation failure or a missing record.
 *
 * <p>Pinned in one place so the handlers that emit it and the tests that assert it read the same
 * fact. Before this the number was written out at four test sites and two handlers, which is six
 * places to miss if the firewall's rules ever change.
 *
 * <p><b>Do not</b> use 401 here. The front end's axios interceptor treats 401 as an expired session:
 * it attempts a token refresh and, failing that, clears the session and redirects to the login page.
 * A branch officer opening a record belonging to another branch would be logged out.
 */
public final class AccessDenied {

    /** The wire status a refusal travels under. Not 403 — see above. */
    public static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    /**
     * What the front end branches on.
     *
     * <p>{@code ProblemDetail} carries this as a property, and the axios interceptor already reads
     * {@code errorCode} — that mechanism exists so one specific case can be recognised and presented
     * properly rather than toasted as a generic error.
     */
    public static final String ERROR_CODE = "ACCESS_DENIED";

    private AccessDenied() {
    }
}
