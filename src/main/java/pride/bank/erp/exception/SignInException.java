/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.io.Serial;

/**
 * A sign-in, or a password action, refused for a reason the login page names: carries the status
 * (always one the firewall admits) and the {@code errorCode} the page switches on.
 */
@Getter
public class SignInException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient HttpStatusCode status;
    private final String errorCode;

    public SignInException(HttpStatusCode status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
