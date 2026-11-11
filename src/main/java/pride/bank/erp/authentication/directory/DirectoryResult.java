/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

/**
 * What the LDAP Authentication API said. {@code authenticated} is true only when the API said so
 * explicitly; every other shape - a refusal, a fault, a timeout, an unreadable body - is false, so a
 * caller that checks the flag first cannot open a session by accident.
 *
 * @param code     the API's code, or {@link #UNAVAILABLE} / {@link #UNEXPECTED} when it gave none
 * @param username the AD username the API vouched for (only when authenticated)
 * @param email    the AD email address it returned (only when authenticated, may be null)
 */
public record DirectoryResult(boolean authenticated, String code, String username, String email) {

    public static final String AUTHENTICATED = "AUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String USER_THROTTLED = "USER_THROTTLED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String INVALID_CLIENT = "INVALID_CLIENT";
    public static final String CLIENT_INACTIVE = "CLIENT_INACTIVE";
    /** No answer at all: timeout, connection refused, not configured. */
    public static final String UNAVAILABLE = "UNAVAILABLE";
    /** An answer nobody documented. */
    public static final String UNEXPECTED = "UNEXPECTED";

    public static DirectoryResult success(String username, String email) {
        return new DirectoryResult(true, AUTHENTICATED, username, email);
    }

    public static DirectoryResult failure(String code) {
        return new DirectoryResult(false, code == null ? UNEXPECTED : code, null, null);
    }

    /**
     * Worth one more try: the two answers the API documents as "retry once". Both say the request was
     * not processed. A timeout is deliberately not one - the API may have checked the password before
     * the answer was lost, and a second attempt would count a wrong password twice against the
     * employee's five-in-fifteen-minutes limit, which every bank application shares.
     */
    boolean retryable() {
        return !authenticated && (RATE_LIMITED.equals(code) || SERVICE_UNAVAILABLE.equals(code));
    }
}
