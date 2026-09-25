/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The last place a response can still carry a status the firewall will not pass.
 *
 * <h2>What {@code @ControllerAdvice} cannot reach</h2>
 * {@link ApplicationExceptionHandler} catches anything thrown inside a controller, but an exception
 * raised in a <b>servlet filter</b> never reaches it. {@code JwtAuthenticationFilter} and
 * {@code HttpMethodOverrideFilter} both run before the dispatcher, and a failure in either is
 * handled by the container: it re-dispatches to {@code /error}, which Spring Boot's
 * {@code BasicErrorController} answers with the original status - typically <b>500</b>.
 *
 * <p>The firewall blocks the whole 5xx range, so such a response never reaches the caller as an
 * error. It arrives as the firewall's own rejection page under HTTP 200, which the browser then
 * fails to parse. That is precisely the failure this application spent a deployment chasing, and it
 * is invisible from inside the application: the server log shows a clean 500 while the user sees an
 * unrelated page.
 *
 * <p>Declaring an {@link ErrorController} bean makes Spring Boot stand down its own, so every error
 * dispatch - whatever raised it and wherever - passes through here.
 *
 * <h2>What it changes, and what it deliberately does not</h2>
 * A status the firewall admits is passed through untouched: a 404 from an unmatched route stays a
 * 404, a 401 from the authentication entry point stays a 401. Only a status that would be blocked is
 * rewritten, to {@link WafStatus#SERVER_FAULT}, carrying {@code errorCode} so the front end can tell
 * it apart from a refusal or a validation failure.
 *
 * <p>The original status is reported as {@code upstreamStatus}. Without it the substitution would
 * erase the one fact worth having when reading a log afterward - a 500 and a 503 are different
 * problems, and both would otherwise read as 440.
 */
@RestController
public class WafSafeErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
        int upstream = resolveStatus(request);

        boolean admitted = upstream < 400 || WafStatus.ALLOWED_ERROR_STATUSES.contains(upstream);
        HttpStatusCode status = admitted ? HttpStatusCode.valueOf(upstream) : WafStatus.SERVER_FAULT;

        String detail = admitted
                ? messageFor(request, upstream)
                : "Something went wrong. Please try again, or contact support if it continues.";

        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("message", detail);
        if (!admitted) {
            body.setProperty("errorCode", WafStatus.SERVER_FAULT_CODE);
            body.setProperty("upstreamStatus", upstream);
        }
        return ResponseEntity.status(status).body(body);
    }

    private static int resolveStatus(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        // No attribute means /error was reached directly rather than by an error dispatch. Treating
        // that as a fault is the safe reading: something asked for it, and nothing normal does.
        return attribute instanceof Integer status ? status : 500;
    }

    private static String messageFor(HttpServletRequest request, int status) {
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (message != null && !message.toString().isBlank()) {
            return message.toString();
        }
        return status == 404 ? "Not found" : "Request could not be completed";
    }
}
