/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import org.springframework.http.HttpStatusCode;

import java.util.Set;

/**
 * The only response statuses the firewall lets through, and the two carriers this application uses
 * for meanings that have nowhere else to go.
 *
 * <h2>The constraint</h2>
 * The WAF in front of this service passes <b>2xx</b> and <b>3xx</b> wholesale, blocks <b>every
 * 5xx</b>, and admits only seven codes from the 4xx range:
 *
 * <pre>400, 401, 404, 407, 409, 417, 440</pre>
 *
 * Anything else is replaced with the firewall's own rejection page — served, confusingly, with
 * <b>HTTP 200</b> and an HTML body. So a blocked status does not reach the caller as an error: it
 * reaches them as a success carrying a page they cannot parse. That is why this matters more than a
 * status code usually would, and why {@code WafStatusCoverageTest} fails the build over it.
 *
 * <h2>Why two carriers, and why these two</h2>
 * Five of the seven admitted codes already mean something here — 400 validation, 401 authentication,
 * 404 absence, 409 conflict, and 407 is unusable (see below). That leaves <b>417</b> and <b>440</b>
 * for the two meanings with no home: a refusal, and an unexpected fault.
 *
 * <p><b>407 must never be used.</b> It is on the firewall's list, but browsers read it as <i>Proxy
 * Authentication Required</i> and may present the user with a credential dialog for a proxy that
 * does not exist. An allowed code is not automatically a usable one.
 *
 * <h2>History worth knowing</h2>
 * Refusals were moved off 403 to <b>422</b> when the firewall's block on 403 was discovered. 422 is
 * not on this list either, so every permission refusal in the application was being blocked from
 * that day until this file existed. The lesson is the reason for the test: a status constraint that
 * lives only in someone's memory is one nobody checks against new code.
 */
public final class WafStatus {

    /**
     * The 4xx codes the firewall admits. Statuses below 400 pass regardless; 5xx never does.
     *
     * <p>Read by {@code WafStatusCoverageTest}, so the rule and its enforcement cannot drift.
     */
    public static final Set<Integer> ALLOWED_ERROR_STATUSES =
            Set.of(400, 401, 404, 407, 409, 417, 440);

    /**
     * 417 Expectation Failed — "I know who you are, and the answer is no."
     *
     * <p>Not 401: the front end reads 401 as an expired session, attempts a refresh and then signs
     * the user out. A branch officer opening another branch's record would be logged out for it.
     */
    public static final HttpStatusCode ACCESS_DENIED = HttpStatusCode.valueOf(417);

    /**
     * 440 — an unexpected fault. This is what a 500 becomes.
     *
     * <p>Non-standard (it originates with IIS as "Login Time-out") and deliberately so: it is the
     * one code on the firewall's list that carries no meaning this application has already spent,
     * and browsers apply no special handling to it. The honest status would be 500; the firewall
     * does not permit honesty here, so the number is transport and {@code errorCode} carries the
     * meaning.
     */
    public static final HttpStatusCode SERVER_FAULT = HttpStatusCode.valueOf(440);

    /** What the front end branches on when the status alone cannot say enough. */
    public static final String SERVER_FAULT_CODE = "SERVER_ERROR";

    private WafStatus() {
    }
}
