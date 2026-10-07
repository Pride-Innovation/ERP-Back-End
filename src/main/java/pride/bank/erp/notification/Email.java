/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.notification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.request.CommodityWithQuantityDTO;
import pride.bank.erp.request.Request;
import pride.bank.erp.stock.Stock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;
import static pride.bank.erp.notification.EmailLayout.Tone;
import static pride.bank.erp.notification.EmailLayout.callout;
import static pride.bank.erp.notification.EmailLayout.codeBox;
import static pride.bank.erp.notification.EmailLayout.details;
import static pride.bank.erp.notification.EmailLayout.esc;
import static pride.bank.erp.notification.EmailLayout.greeting;
import static pride.bank.erp.notification.EmailLayout.page;
import static pride.bank.erp.notification.EmailLayout.paragraph;
import static pride.bank.erp.notification.EmailLayout.strong;

/**
 * Every email the application sends. Each template supplies a title, a label and a body; the page
 * around them - masthead, "Assets Management", button, footer - is {@link EmailLayout}'s alone, so
 * the messages cannot drift apart again.
 */
@Service
@RequiredArgsConstructor
public class Email {

    private static final Logger logger = LoggerFactory.getLogger(Email.class);
    private final MailDispatcher mailDispatcher;

    // The From address belongs to MailDispatcher, which builds the message.

    @Value("${application.verification.url}")
    private String verificationUrl;

    @Value("${application.security.jwt.expiration}")
    private long tokenExpirationTime;

    /** Front-end routes the buttons open - path segments, which is what the router matches. */
    private static final String REQUEST_VIEW_PATH = "assets-mgt/asset-request/view";
    private static final String STOCK_VIEW_PATH = "assets-mgt/inventory/view";

    /**
     * Sends an email with an optional password.
     *
     * <p>Deliberately <b>not</b> {@code @Async}: the templates below read JPA entities
     * ({@code request.getRequester().getBranch()}, {@code stock.getSupplier()}), which is only safe
     * while the caller's persistence context is still open. Rendering therefore happens here, on the
     * caller's thread, and only the finished HTML is handed to {@link MailDispatcher} to transmit
     * off-thread — so the SMTP round-trip still doesn't block the request.
     */
    public boolean sendEmailVerification(
            String email,
            String subject,
            String token,
            String headerText,
            String buttonText,
            String password,
            String type,
            Request request,
            Stock stock,
            String startDate,
            String endDate,
            String reason
    ) {
        try {
            String htmlContent;

            if ("authentication".equalsIgnoreCase(type)) {
                Instant expirationInstant = Instant.now().plusMillis(tokenExpirationTime);
                String expirationTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(expirationInstant);
                htmlContent = getAuthenticationEmailContent(token, expirationTime, headerText, buttonText, password, reason);
            } else if ("requests".equalsIgnoreCase(type)) {
                htmlContent = getRequestEmailContent(request, headerText, buttonText, password);
            } else if ("stocks".equalsIgnoreCase(type)) {
                htmlContent = getStockEmailContent(stock, headerText, buttonText, password);
            } else if ("leave".equalsIgnoreCase(type)) {
                htmlContent = getLeaveEmailContent(token, headerText, buttonText, password, startDate, endDate, reason);
            } else if ("notification".equalsIgnoreCase(type)) {
                htmlContent = getNotificationContent(token, password);
            } else {
                logger.error("Invalid email type: {}", type);
                return false;
            }

            // Rendering is done; from here nothing touches an entity, so the send can go off-thread.
            mailDispatcher.send(cleanRecipient(email), subject, htmlContent, type);
            return true;
        } catch (Exception e) {
            logger.error("Failed to build email for {}", email, e);
            return false;
        }
    }

    /**
     * Movement mail — an approval request, or the outcome once the ladder has finished.
     *
     * <p>Takes only primitives, deliberately. Every other template here reads JPA entities while
     * rendering, which is why {@link Email} cannot be {@code @Async} as a whole; this one has
     * nothing lazy to touch, so a caller can hand it values pulled inside its own transaction and
     * stop worrying about the session boundary.
     *
     * <p>Never throws. A movement must not fail to be created, approved or rejected because an SMTP
     * host is unreachable — the in-app notification has already been written by then, and mail is
     * the redundant channel, not the authoritative one.
     *
     * @param outcome  null for "needs your approval"; otherwise the decision reached
     *                 ({@code APPROVED} / {@code REJECTED}), which flips the label and the wording
     * @param comment  the approver's note, when there is one
     */
    public boolean sendMovementEmail(
            String recipientEmail,
            String recipientName,
            Long movementId,
            String movementType,
            String route,
            String link,
            String outcome,
            String comment
    ) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            logger.warn("No email address for movement {} notification — skipping mail", movementId);
            return false;
        }
        try {
            boolean rejected = "REJECTED".equalsIgnoreCase(outcome);
            boolean decided = outcome != null && !outcome.isBlank();

            String subject = decided
                    ? format("Movement #%d %s", movementId, rejected ? "rejected" : "approved")
                    : format("Approval needed — Movement #%d", movementId);

            mailDispatcher.send(cleanRecipient(recipientEmail), subject,
                    getMovementEmailContent(recipientName, movementId, movementType, route, link,
                            decided, rejected, comment),
                    "movement");
            return true;
        } catch (Exception e) {
            logger.error("Failed to build movement email for {} (movement {})", recipientEmail, movementId, e);
            return false;
        }
    }

    String getMovementEmailContent(String recipientName, Long movementId, String movementType, String route,
                                   String link, boolean decided, boolean rejected, String comment) {
        String number = strong("#" + movementId);
        String title;
        String label;
        Tone tone;
        String lead;
        if (!decided) {
            title = "A movement needs your approval";
            label = "Approval required";
            tone = Tone.ACTION;
            lead = "Movement " + number + " is waiting on your decision. Nothing moves, and no stock leaves "
                    + "its store, until it is approved.";
        } else if (rejected) {
            title = "Movement rejected";
            label = "Rejected";
            tone = Tone.DANGER;
            lead = "Movement " + number + " was rejected and will not proceed.";
        } else {
            title = "Movement approved";
            label = "Approved";
            tone = Tone.SUCCESS;
            lead = "Movement " + number + " has cleared its approval ladder and is ready to be dispatched.";
        }

        String body = greeting(recipientName)
                + paragraph(lead)
                + details("Movement details", List.of(
                new String[]{"Movement", "#" + movementId},
                new String[]{"Type", movementType},
                new String[]{"Route", route}))
                + (comment == null || comment.isBlank() ? "" : callout("Approver's comment", esc(comment), tone));

        return page(title, tone, label, format("Movement #%d — %s", movementId, title.toLowerCase()),
                body, decided ? "View movement" : "Review movement", link);
    }

    /**
     * Account creation and password reset. The {@code reason} parameter is dual-purpose:
     * {@code "reset"} produces a password-reset link; any other text is the recipient's full name
     * for the greeting; null gives a generic greeting and the standard verify link.
     */
    String getAuthenticationEmailContent(
            String token,
            String expirationTime,
            String headerText,
            String buttonText,
            String password,
            String reason
    ) {
        boolean isReset = Objects.equals(reason, "reset");
        String link = token == null ? null
                : isReset
                ? format("%s/reset-password?token=%s", verificationUrl, token)
                : format("%s/verify?token=%s", verificationUrl, token);
        String recipientName = (!isReset && reason != null && !reason.isBlank()) ? reason : null;

        StringBuilder body = new StringBuilder(greeting(recipientName));
        if (isReset) {
            body.append(paragraph("We received a request to reset the password for your Assets Management "
                    + "account. Use the button below to choose a new one."));
        } else if (recipientName != null) {
            body.append(paragraph("Your Pride Bank Assets Management account has been created. Verify it "
                    + "using the button below, then sign in with the temporary password shown here."));
        } else {
            body.append(paragraph("Please follow the instructions below to proceed."));
        }

        if (password != null) {
            body.append(codeBox("Temporary password", password,
                    "You will be asked to change it when you first sign in."));
        }
        if (link != null && expirationTime != null) {
            body.append(callout(null, "This link expires on " + strong(expirationTime)
                    + ". If it has expired, ask your system administrator to send a new one.", Tone.INFO));
        }
        body.append(callout("Did not expect this email?",
                esc("Ignore it and let the IT department know. Never share your password with anyone."),
                Tone.DANGER));

        return page(headerText == null ? "Your account" : headerText, Tone.INFO,
                isReset ? "Password reset" : "Account security",
                isReset ? "Reset your Assets Management password" : "Verify your Assets Management account",
                body.toString(), buttonText, link);
    }

    /**
     * A stores request awaiting the recipient, or an update on one ({@code comment} set).
     *
     * @param recipientName who the mail is addressed to
     */
    String getRequestEmailContent(Request request, String recipientName, String buttonText, String comment) {
        String requester = personName(request);
        String link = format("%s/%s/%s", verificationUrl, REQUEST_VIEW_PATH, request.getId());
        boolean update = comment != null && !comment.isBlank();

        String body = greeting(recipientName)
                + (update
                ? paragraph("There is an update on request " + strong("#" + request.getId()) + ".")
                + callout(null, esc(comment), Tone.INFO)
                : paragraph("A stores request raised by " + strong(requester) + " is "
                + "<strong style='color:" + EmailLayout.BRAND + ";'>awaiting your action</strong>."))
                + requestDetails(request)
                + requestItems(request);

        return page(update ? "Stores request update" : "Stores request awaiting your action",
                update ? Tone.INFO : Tone.ACTION,
                update ? "Update" : "Action required",
                format("Request #%d — %s", request.getId(), update ? "update" : "awaiting your action"),
                body, buttonText == null ? "View request" : buttonText, link);
    }

    /**
     * New stock received. {@code headerText} is the greeting line the caller composed ("Dear
     * Computers Team,"); {@code message} is server-built HTML naming the items, and is trusted.
     */
    String getStockEmailContent(Stock stock, String headerText, String buttonText, String message) {
        String link = format("%s/%s/%s", verificationUrl, STOCK_VIEW_PATH, stock.getId());
        String received = stock.getCreateDate() == null ? null
                : DateTimeFormatter.ofPattern("d MMM yyyy").format(stock.getCreateDate());

        String greetingLine = headerText != null && headerText.trim().toLowerCase().startsWith("dear")
                ? paragraph(esc(headerText.trim()))
                : greeting(headerText);

        String body = greetingLine
                + paragraph(message != null ? message : "Please update the system with the newly received items.")
                + details("Stock details", List.of(
                new String[]{"LPO number", stock.getLpoNumber()},
                new String[]{"Supplier", stock.getSupplier() != null ? stock.getSupplier().getName() : null},
                new String[]{"Branch", stock.getBranch() != null ? stock.getBranch().getName() : null},
                new String[]{"Date received", received}));

        return page("New stock received", Tone.ACTION, "Action required",
                format("Stock received under LPO %s", stock.getLpoNumber()),
                body, buttonText == null ? "View stock" : buttonText, link);
    }

    /** An asset assigned to someone. */
    String getNotificationContent(String recipientName, String assetName) {
        String body = greeting(recipientName)
                + paragraph("Asset " + strong(assetName) + " has been assigned to you. It now appears under "
                + "<strong>My Assets</strong> on your dashboard.")
                + paragraph("If you have not received it, or believe it was assigned in error, please contact "
                + "the Administration department.");

        return page("An asset has been assigned to you", Tone.INFO, "Asset assignment",
                format("%s has been assigned to you", assetName), body, null, null);
    }

    /** Somebody has gone on leave and named the recipient as the acting officer. */
    String getLeaveEmailContent(
            String actingUserName,
            String leaveUserName,
            String leaveType,
            String actingTitle,
            String startDate,
            String endDate,
            String leaveReason
    ) {
        String body = greeting(actingUserName)
                + paragraph(strong(leaveUserName) + " has gone on leave and named you as acting officer for "
                + "the period below. Please take over the responsibilities of their role until they return.")
                + details("Acting assignment", List.of(
                new String[]{"Colleague on leave", leaveUserName},
                new String[]{"Role you are covering", actingTitle},
                new String[]{"Leave type", leaveType},
                new String[]{"From", startDate},
                new String[]{"To", endDate}))
                + (leaveReason == null || leaveReason.isBlank() ? "" : callout("Reason given", esc(leaveReason), Tone.INFO));

        return page("You are the acting officer", Tone.INFO, "Acting assignment",
                format("You are acting for %s from %s", leaveUserName, startDate), body, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Workflow step email
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sends a workflow step notification email.
     *
     * @param email        Recipient email address
     * @param subject      Email subject
     * @param greeting     Recipient's first name or "Team"
     * @param stepTitle    The step name e.g. "Manager Approval Required"
     * @param instructions Step-specific instruction text, or the rejection comment
     * @param request      The request entity
     * @param step         The current approval step (may be null for rejection)
     * @param stepStatus   "PENDING", "REJECTED", "CC", "COMPLETION"
     */
    public boolean sendWorkflowEmail(
            String email,
            String subject,
            String greeting,
            String stepTitle,
            String instructions,
            Request request,
            Object step,
            String stepStatus
    ) {
        if (email == null || email.isBlank()) return false;
        try {
            // Reads the request graph — must stay on the caller's thread (see sendEmailVerification).
            String htmlContent = getWorkflowEmailContent(greeting, stepTitle, instructions, request, stepStatus);

            mailDispatcher.send(cleanRecipient(email), subject, htmlContent, "workflow");
            return true;
        } catch (Exception e) {
            logger.error("Failed to build workflow email for {}", email, e);
            return false;
        }
    }

    /**
     * The single place recipient addresses are cleaned before SMTP send. Trailing
     * digits in the local part are stripped (see {@link ExtractNumberFromEmailAddress}),
     * so addresses stored as sodong1@…, sodong2@… — kept distinct to satisfy the
     * email-unique constraint — all resolve to the single real mailbox sodong@….
     * Every send routes through here, so callers pass the raw stored address and never
     * clean it themselves.
     */
    private String cleanRecipient(String email) {
        String cleaned = new ExtractNumberFromEmailAddress(email).getCleanedEmail();
        if (!Objects.equals(email, cleaned)) {
            logger.debug("Recipient rewritten for SMTP: {} -> {}", email, cleaned);
        }
        return cleaned;
    }

    /**
     * {@code COMPLETION} used to fall through to the default and arrive labelled "Action required"
     * on a message telling the reader something had finished. It has its own label now.
     */
    String getWorkflowEmailContent(
            String greetingName,
            String stepTitle,
            String instructions,
            Request request,
            String stepStatus
    ) {
        String status = stepStatus == null ? "PENDING" : stepStatus.toUpperCase();
        Tone tone;
        String label;
        String instructionsHeading;
        switch (status) {
            case "REJECTED" -> { tone = Tone.DANGER; label = "Rejected"; instructionsHeading = "Reason given"; }
            case "CC" -> { tone = Tone.INFO; label = "For your information"; instructionsHeading = null; }
            case "COMPLETION" -> { tone = Tone.SUCCESS; label = "Completed"; instructionsHeading = null; }
            default -> { tone = Tone.ACTION; label = "Action required"; instructionsHeading = null; }
        }

        String link = format("%s/%s/%d", verificationUrl, REQUEST_VIEW_PATH, request.getId());
        String text = instructions == null || instructions.isBlank() ? null : esc(instructions);

        String body = greeting(greetingName)
                + (text == null ? ""
                : instructionsHeading != null ? callout(instructionsHeading, text, tone) : paragraph(text))
                + requestDetails(request)
                + requestItems(request);

        return page(stepTitle == null ? "Request update" : stepTitle, tone, label,
                format("Request #%d — %s", request.getId(), request.getName() == null ? label : request.getName()),
                body, "CC".equals(status) ? null : "View request", link);
    }

    // ── Request pieces shared by the request and workflow mails ──────────────────────────────────

    private static String personName(Request request) {
        if (request.getRequester() == null) return null;
        return (Objects.toString(request.getRequester().getFirstName(), "") + " "
                + Objects.toString(request.getRequester().getLastName(), "")).trim();
    }

    private static String requestDetails(Request request) {
        String branch = request.getRequester() != null && request.getRequester().getBranch() != null
                ? request.getRequester().getBranch().getName() : null;
        return details("Request details", List.of(
                new String[]{"Request", "#" + request.getId()},
                new String[]{"Title", request.getName()},
                new String[]{"Requested by", personName(request)},
                new String[]{"Duty station", branch},
                new String[]{"Priority", request.getPriority()}));
    }

    private static String requestItems(Request request) {
        List<CommodityWithQuantityDTO> commodities = request.getCommodities();
        if (commodities == null || commodities.isEmpty()) return "";
        List<String[]> rows = new ArrayList<>();
        int total = 0;
        int n = 1;
        for (CommodityWithQuantityDTO c : commodities) {
            String name = c.getCommodity() != null ? c.getCommodity().getName() : "Item";
            String unit = c.getCommodity() != null ? c.getCommodity().getGroupName() : "";
            rows.add(new String[]{String.valueOf(n++), name, unit, String.valueOf(c.getQuantity())});
            total += c.getQuantity();
        }
        return EmailLayout.table("Requested items", new String[]{"#", "Item", "Unit", "Qty"}, rows, 3,
                rows.size() > 1 ? new String[]{"", "Total", "", String.valueOf(total)} : null);
    }
}
