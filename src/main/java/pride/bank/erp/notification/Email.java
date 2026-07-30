/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.notification;

import lombok.NonNull;
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
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
public class Email {

    private static final Logger logger = LoggerFactory.getLogger(Email.class);
    private final MailDispatcher mailDispatcher;

    // The From address now belongs to MailDispatcher, which builds the message.

    @Value("${application.verification.url}")
    private String verificationUrl;

    @Value("${application.security.jwt.expiration}")
    private long tokenExpirationTime;

    // Brand colors
    private static final String TEAL_PRIMARY = "#05544B";
    private static final String TEAL_SECONDARY = "#0a6b5d";
    private static final String GOLD_PRIMARY = "#BC892C";
    private static final String GOLD_SECONDARY = "#d4a545";
    private static final String assetViewPath = "assets-mgt/asset-request/view";

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
                String expirationTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
     * Generates the HTML content for authentication emails (account creation & password reset).
     * The {@code reason} parameter is dual-purpose:
     * - "reset"        → generates a password-reset link
     * - any other text → treated as the recipient's full name for a personalised greeting
     * - null           → generic greeting, standard verify link
     */
    private String getAuthenticationEmailContent(
            String token,
            String expirationTime,
            String headerText,
            String buttonText,
            String password,
            String reason
    ) {
        boolean isReset = Objects.equals(reason, "reset");

        String verifyLink = (token != null)
                ? (isReset
                ? format("%s/reset-password?token=%s", verificationUrl, token)
                : format("%s/verify?token=%s", verificationUrl, token))
                : "#";

        // Personalised greeting: use reason as name when it's not a control keyword
        String greeting = getString(reason, isReset);

        // Password card — shown only for new-account emails
        String passwordSection = (password != null)
                ? format(
                "<div style='border: 2px solid %s; border-radius: 14px; overflow: hidden; margin: 28px 0;'>" +
                        "  <div style='background: %s; padding: 12px 20px;'>" +
                        "    <p style='margin: 0; color: #ffffff; font-size: 13px; font-weight: 700; " +
                        "       letter-spacing: 1px; text-transform: uppercase;'>&#128274; Temporary Password</p>" +
                        "  </div>" +
                        "  <div style='background: #fafafa; padding: 20px; text-align: center;'>" +
                        "    <code style='display: inline-block; font-family: \"Courier New\", Courier, monospace; " +
                        "         font-size: 22px; font-weight: 700; color: %s; background: #ffffff; " +
                        "         border: 1.5px dashed %s; border-radius: 8px; padding: 10px 24px; " +
                        "         letter-spacing: 4px;'>%s</code>" +
                        "    <p style='color: #718096; font-size: 13px; margin: 14px 0 0 0;'>" +
                        "      &#9888; You will be prompted to change this password upon your first login.</p>" +
                        "  </div>" +
                        "</div>",
                TEAL_PRIMARY, TEAL_PRIMARY, TEAL_PRIMARY, GOLD_PRIMARY, password)
                : "";

        // Security / link-expiry notice
        String expirySection = (expirationTime != null)
                ? format(
                "<div style='background: #f0faf9; border-left: 5px solid %s; border-radius: 0 10px 10px 0; " +
                        "     padding: 16px 20px; margin: 24px 0;'>" +
                        "  <p style='margin: 0; color: #2d3748; font-size: 14px; line-height: 1.6;'>" +
                        "    <strong style='color: %s;'>&#9200; Link expires on:</strong>&nbsp; %s</p>" +
                        "  <p style='margin: 6px 0 0 0; color: #718096; font-size: 13px;'>" +
                        "    If the link has expired, please contact your system administrator to resend the verification.</p>" +
                        "</div>",
                TEAL_PRIMARY, TEAL_PRIMARY, expirationTime)
                : "";

        return format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "  <meta charset='UTF-8'>" +
                        "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "  <title>%s — Pride Bank</title>" +
                        "</head>" +
                        "<body style='margin:0; padding:0; font-family:-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, " +
                        "     \"Helvetica Neue\", Arial, sans-serif; background-color:#f0f4f8;'>" +

                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f0f7ff 100%%); " +
                        "     min-height: 100vh; padding: 40px 16px;'>" +
                        "<div style='max-width: 620px; margin: 0 auto; background: #ffffff; border-radius: 20px; " +
                        "     overflow: hidden; box-shadow: 0 20px 60px rgba(0,0,0,0.10);'>" +

                        // ── Header ──────────────────────────────────────────────────────
                        // background-color is the solid fallback for email clients (e.g. Outlook) that
                        // do not support CSS linear-gradient; background-image adds the gradient for
                        // clients that do support it, without overriding the fallback color.
                        "<div style='background-color: " + TEAL_PRIMARY + "; background-image: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 44px 32px; text-align: center;'>" +
                        "  <img src='cid:logoImage' alt='Pride Bank Limited' " +
                        "       style='max-width: 260px; height: auto; filter: brightness(0) invert(1); margin-bottom: 24px;' />" +
                        "  <div style='display: inline-block; background: rgba(255,255,255,0.15); " +
                        "       border: 1px solid rgba(255,255,255,0.35); border-radius: 50px; " +
                        "       padding: 6px 20px; margin-bottom: 16px;'>" +
                        "    <span style='color: #ffffff; font-size: 12px; font-weight: 700; " +
                        "          letter-spacing: 2px; text-transform: uppercase;'>Employee Portal</span>" +
                        "  </div>" +
                        "  <h1 style='color: #ffffff; margin: 0; font-size: 26px; font-weight: 600; " +
                        "       letter-spacing: 0.3px; text-shadow: 0 2px 4px rgba(0,0,0,0.4);'>%s</h1>" +
                        "  <div style='width: 48px; height: 3px; background: %s; margin: 18px auto 0; border-radius: 2px;'></div>" +
                        "</div>" +

                        // ── Body ────────────────────────────────────────────────────────
                        "<div style='padding: 44px 40px; background: #ffffff;'>" +

                        // Greeting & intro
                        "%s" +

                        // CTA button
                        "<div style='text-align: center; margin: 36px 0;'>" +
                        "  <a href='%s' style='display: inline-block; padding: 16px 40px; " +
                        "     color: #ffffff; background-color: " + TEAL_PRIMARY + "; background-image: linear-gradient(135deg, %s 0%%, %s 100%%); " +
                        "       text-decoration: none; border-radius: 50px; font-weight: 700; font-size: 15px; " +
                        "       letter-spacing: 0.5px; text-transform: uppercase; " +
                        "       box-shadow: 0 8px 24px rgba(5,84,75,0.35);'>%s</a>" +
                        "  <p style='color: #a0aec0; font-size: 12px; margin: 14px 0 0 0;'>" +
                        "    Button not working? <a href='%s' style='color: %s; word-break: break-all;'>Click here</a>" +
                        "  </p>" +
                        "</div>" +

                        // Expiry notice
                        "%s" +

                        // Temporary password
                        "%s" +

                        // Security disclaimer
                        "<div style='background: #fff8f8; border: 1px solid #fecaca; border-radius: 12px; " +
                        "     padding: 18px 20px; margin-top: 36px;'>" +
                        "  <p style='color: #7f1d1d; font-size: 13px; margin: 0; line-height: 1.6;'>" +
                        "    <strong>&#128274; Security Notice:</strong> If you did not expect this email, " +
                        "    please disregard it and notify your IT department immediately. " +
                        "    Do not share your temporary password with anyone." +
                        "  </p>" +
                        "</div>" +

                        "</div>" +   // end body

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",

                // format arguments
                headerText,                         // <title>
                TEAL_PRIMARY, TEAL_SECONDARY,       // header gradient
                headerText,                         // h1 text
                GOLD_PRIMARY,                       // h1 underline
                greeting,                           // personalised greeting + intro
                verifyLink,                         // CTA href
                TEAL_PRIMARY, TEAL_SECONDARY,       // CTA gradient
                buttonText,                         // CTA label
                verifyLink, TEAL_PRIMARY,           // fallback link
                expirySection,                      // expiry block
                passwordSection                     // password card
        );
    }

    private static @NonNull String getString(String reason, boolean isReset) {
        String recipientName = (!isReset && reason != null && !reason.isBlank()) ? reason : null;
        return recipientName != null
                ? format(
                "<p style='color: #2d3748; font-size: 18px; font-weight: 500; margin: 0 0 8px 0;'>" +
                        "Dear <strong style='color: %s;'>%s</strong>,</p>" +
                        "<p style='color: #4a5568; font-size: 15px; line-height: 1.7; margin: 0 0 28px 0;'>" +
                        "Welcome to Pride Bank. Your employee account has been created and is ready for activation. " +
                        "Please review your credentials below and verify your account to get started.</p>",
                TEAL_PRIMARY, recipientName)
                : "<p style='color: #4a5568; font-size: 15px; line-height: 1.7; margin: 0 0 28px 0;'>" +
                "Please follow the instructions below to proceed.</p>";
    }

    /**
     * Generates the HTML content for request emails.
     * FIXED: All white text visibility issues
     */

    private String getRequestEmailContent(Request request, String approver, String buttonText, String comment) {
        StringBuilder tableRows = getStringBuilder(request);
        int totalQuantity = getTotalQuantity(request);

        // FIXED: Total row with proper white text on colored background
        String totalRow = String.format(
                "<tr style='background-color: " + TEAL_PRIMARY + "; background-image: linear-gradient(135deg, %s 0%%, %s 100%%);'>" +
                        "<td style='padding: 18px 24px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase; color: #ffffff; border: none;' colspan='3'>Total Quantity</td>" +
                        "<td style='padding: 18px 24px; font-weight: 700; font-size: 20px; color: #ffffff; border: none;'>%d</td>" +
                        "</tr>",
                TEAL_PRIMARY, TEAL_SECONDARY, totalQuantity
        );

        String requesterInfoSection = getRequesterInfoSection(request);

        String viewRequestLink = String.format("%s/%s/%s", verificationUrl, assetViewPath, request.getId());
        String text = getString(request, comment);

        return String.format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "<meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "<title>Store Request Notification</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f0f4f8;'>" +
                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f9f2e7 100%%); min-height: 100vh; padding: 40px 20px;'>" +
                        "<div style='max-width: 900px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,0.15);'>" +

                        // Header Section
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 40px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 300px; height: auto; filter: brightness(0) invert(1); margin-bottom: 25px;' />" +
                        "<h1 style='color: #ffffff; margin: 0; font-size: 32px; font-weight: 400; letter-spacing: 1px; text-shadow: 0 2px 4px rgba(0,0,0,0.4);'>Store Request Notification</h1>" +
                        "<div style='width: 80px; height: 4px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content Section
                        "<div style='padding: 50px 40px; background-color: #ffffff;'>" +
                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 28px; font-weight: 600;'>Dear %s,</h2>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin-bottom: 35px;'></div>" +

                        "%s" +

                        // Modern Table
                        "<div style='margin: 40px 0; overflow: hidden; border-radius: 16px; box-shadow: 0 8px 32px rgba(5,84,75,0.15); background: #ffffff;'>" +
                        "<table style='width: 100%%; border-collapse: collapse; background: #ffffff;'>" +
                        "<thead>" +
                        "<tr style='background-color: %s !important;'>" +
                        "<th style='padding: 24px; text-align: left; color: #ffffff !important; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; text-transform: uppercase; border: none; background-color: %s !important;'>S/N</th>" +
                        "<th style='padding: 24px; text-align: left; color: #ffffff !important; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; text-transform: uppercase; border: none; background-color: %s !important;'>Item Name</th>" +
                        "<th style='padding: 24px; text-align: left; color: #ffffff !important; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; text-transform: uppercase; border: none; background-color: %s !important;'>Category</th>" +
                        "<th style='padding: 24px; text-align: left; color: #ffffff !important; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; text-transform: uppercase; border: none; background-color: %s !important;'>Quantity</th>" +
                        "</tr>" +
                        "</thead>" +
                        "<tbody style='background: #ffffff;'>%s</tbody>" +
                        "<tfoot>%s</tfoot>" +
                        "</table>" +
                        "</div>" +

                        // NEW: Requester info section
                        "%s" +

                        // Call to Action
                        "<div style='text-align: center; margin: 50px 0; background-color: #ffffff;'>" +
                        "<p style='color: #4a5568; font-size: 16px; margin-bottom: 30px; font-weight: 500;'>To review and take action on this request, click the button below:</p>" +
                        "<a href='%s' style='display: inline-block; padding: 20px 45px; color: #ffffff; background-color: " + TEAL_PRIMARY + "; background-image: linear-gradient(135deg, %s 0%%, %s 100%%); text-decoration: none; border-radius: 50px; font-weight: 700; font-size: 16px; letter-spacing: 0.5px; text-transform: uppercase; box-shadow: 0 12px 35px rgba(5,84,75,0.4); transition: all 0.3s ease;'>%s</a>" +
                        "</div>" +

                        // Disclaimer
                        "<div style='background: #fffbf0; border: 2px solid %s; border-radius: 12px; padding: 25px; margin-top: 40px;'>" +
                        "<p style='color: #744210; font-size: 14px; margin: 0; line-height: 1.6;'>" +
                        "<strong style='color: %s;'>⚠️ AUTOMATED NOTIFICATION:</strong> This is an automated message from the Asset Management System. Please do not reply to this email." +
                        "</p>" +
                        "</div>" +
                        "</div>" +

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                // Arguments in correct order (now 21 total with the new section):
                TEAL_PRIMARY, TEAL_SECONDARY,              // 1,2: Header background gradient
                GOLD_PRIMARY,                              // 3: Header underline
                TEAL_PRIMARY, approver,                    // 4,5: Dear section
                GOLD_PRIMARY,                              // 6: Dear underline
                text,                                      // 7: Content text
                TEAL_PRIMARY,                              // 8: Table header background
                TEAL_PRIMARY, TEAL_PRIMARY, TEAL_PRIMARY, TEAL_PRIMARY, // 9,10,11,12: Individual th backgrounds
                tableRows, totalRow,                       // 13,14: Table content
                requesterInfoSection,                      // 15: NEW: Requester info section
                viewRequestLink,                           // 16: Button link
                TEAL_PRIMARY, TEAL_SECONDARY, buttonText,  // 17,18,19: Button styling and text
                GOLD_PRIMARY,                              // 20: Disclaimer border
                GOLD_PRIMARY                               // 21: Disclaimer text color
        );
    }

    private static String getRequesterInfoSection(Request request) {
        String requesterName = String.format("%s %s",
                request.getRequester().getFirstName(),
                request.getRequester().getLastName());
        String branchName = request.getRequester().getBranch().getName();

        return String.format(
                "<div style='margin: 30px 0; padding: 20px; background: #f8fafc; border-left: 4px solid %s; border-radius: 0 8px 8px 0;'>" +
                        "<div style='display: flex; flex-wrap: wrap;'>" +
                        "<div style='flex: 1; min-width: 200px; margin-bottom: 10px;'>" +
                        "<p style='margin: 0; color: #64748b; font-size: 14px;'>Requested By:</p>" +
                        "<p style='margin: 5px 0 0; color: %s; font-weight: 600; font-size: 16px;'>%s</p>" +
                        "</div>" +
                        "<div style='flex: 1; min-width: 200px;'>" +
                        "<p style='margin: 0; color: #64748b; font-size: 14px;'>Duty Station:</p>" +
                        "<p style='margin: 5px 0 0; color: %s; font-weight: 600; font-size: 16px;'>%s</p>" +
                        "</div>" +
                        "</div>" +
                        "</div>",
                GOLD_PRIMARY,
                TEAL_PRIMARY, requesterName,
                TEAL_PRIMARY, branchName
        );
    }

    private static String getString(Request request, String comment) {
        String requester = format("%s %s", request.getRequester().getFirstName(), request.getRequester().getLastName());

        return comment != null
                ? format("<div style='background: linear-gradient(135deg, #f0fff4 0%%, #e6fffa 100%%); border-left: 6px solid %s; padding: 25px; margin: 30px 0; " +
                "border-radius: 0 12px 12px 0; box-shadow: 0 4px 15px rgba(5,84,75,0.1);'><p style='margin: 0; color: #2d3748; font-size: 16px; line-height: 1.7; " +
                "font-style: italic;'>%s</p></div>", TEAL_PRIMARY, comment)
                : format("<p style='color: #4a5568; font-size: 17px; line-height: 1.8; margin: 30px 0;'>Please be informed that the following request has been " +
                "<strong style='color: %s; font-weight: 600;'> initiated </strong> by <strong style='color: %s; font-weight: 600;'>%s</strong> for the following item(s) " +
                "and is <strong style='color: %s; font-weight: 600;'>awaiting your action</strong> :</p>", TEAL_PRIMARY, TEAL_PRIMARY, requester, TEAL_PRIMARY);
    }


    /**
     * FIXED: Table rows with proper visibility
     */
    private static StringBuilder getStringBuilder(Request request) {
        StringBuilder tableRows = new StringBuilder();
        List<CommodityWithQuantityDTO> commodities = request.getCommodities();

        int count = 1;
        for (CommodityWithQuantityDTO commodity : commodities) {
            boolean isEven = count % 2 == 0;
            String rowStyle = isEven
                    ? "background: #f8fffe;"
                    : "background: #ffffff;";

            tableRows.append(String.format(
                    "<tr style='%s border-bottom: 1px solid #e2e8f0;'>" +
                            "<td style='padding: 20px 24px; color: %s; font-size: 15px; font-weight: 600; border: none;'>%d</td>" +
                            "<td style='padding: 20px 24px; color: #2d3748; font-size: 15px; font-weight: 500; border: none;'>%s</td>" +
                            "<td style='padding: 20px 24px; color: #4a5568; font-size: 15px; border: none;'>%s</td>" +
                            "<td style='padding: 20px 24px; color: %s; font-size: 17px; font-weight: 700; border: none;'>%d</td>" +
                            "</tr>",
                    rowStyle,
                    TEAL_PRIMARY, count++,
                    commodity.getCommodity().getName(),
                    commodity.getCommodity().getGroupName(),
                    GOLD_PRIMARY, commodity.getQuantity()
            ));
        }
        return tableRows;
    }

    private int getTotalQuantity(Request request) {
        return request.getCommodities().stream()
                .mapToInt(CommodityWithQuantityDTO::getQuantity)
                .sum();
    }

    /**
     * FIXED: Stock email visibility issues
     */
    private String getStockEmailContent(
            Stock stock,
            String headerText,
            String buttonText,
            String password
    ) {
        String viewStockLink = format("%s/stocks/%s", verificationUrl, stock.getId());
        String dateReceived = DateTimeFormatter.ofPattern("dd MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(stock.getCreateDate());

        return format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "<meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "<title>Stock Notification</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f0f4f8;'>" +
                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f9f2e7 100%%); min-height: 100vh; padding: 40px 20px;'>" +
                        "<div style='max-width: 700px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,0.1);'>" +

                        // Header - FIXED
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 30px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 280px; height: auto; filter: brightness(0) invert(1); margin-bottom: 20px;' />" +
                        "<h1 style='color: #ffffff; margin: 0; font-size: 28px; font-weight: 400; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);'>Stock Notification</h1>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content - FIXED
                        "<div style='padding: 50px 40px; background-color: #ffffff;'>" +
                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 24px; font-weight: 600;'>%s</h2>" +
                        "<div style='width: 50px; height: 3px; background: %s; margin-bottom: 30px;'></div>" +

                        "<p style='color: #4a5568; font-size: 16px; line-height: 1.7; margin: 25px 0;'>%s</p>" +

                        // Stock Details
                        "<div style='background: linear-gradient(135deg, #f7fafc 0%%, #edf2f7 100%%); border-radius: 16px; padding: 30px; margin: 30px 0; border-left: 6px solid %s;'>" +
                        "<div style='display: grid; gap: 15px;'>" +
                        "<div style='display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #e2e8f0;'>" +
                        "<span style='color: %s; font-weight: 600;'>Date Received:</span>" +
                        "<span style='color: #2d3748; font-weight: 500;'>%s</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #e2e8f0;'>" +
                        "<span style='color: %s; font-weight: 600;'>LPO Number:</span>" +
                        "<span style='color: #2d3748; font-weight: 500;'>%s</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #e2e8f0;'>" +
                        "<span style='color: %s; font-weight: 600;'>Supplier:</span>" +
                        "<span style='color: #2d3748; font-weight: 500;'>%s</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 10px 0;'>" +
                        "<span style='color: %s; font-weight: 600;'>Branch:</span>" +
                        "<span style='color: #2d3748; font-weight: 500;'>%s</span>" +
                        "</div>" +
                        "</div>" +
                        "</div>" +

                        // CTA Button - FIXED
                        "<div style='text-align: center; margin: 40px 0;'>" +
                        "<a href='%s' style='display: inline-block; padding: 18px 40px; color: #ffffff; background-color: " + TEAL_PRIMARY + "; background-image: linear-gradient(135deg, %s 0%%, %s 100%%); text-decoration: none; border-radius: 50px; font-weight: 600; font-size: 16px; letter-spacing: 0.5px; text-transform: uppercase; box-shadow: 0 10px 30px rgba(5,84,75,0.3);'>%s</a>" +
                        "</div>" +

                        "<div style='text-align: center; margin-top: 30px;'>" +
                        "<p style='color: #718096; font-size: 14px; margin: 5px 0;'>Regards,</p>" +
                        "<p style='color: %s; font-size: 16px; font-weight: 600; margin: 5px 0;'>Admin Systems Notification</p>" +
                        "</div>" +

                        // Disclaimer
                        "<div style='background: #fffbf0; border: 2px solid %s; border-radius: 12px; padding: 20px; margin-top: 40px;'>" +
                        "<p style='color: #744210; font-size: 13px; margin: 0; line-height: 1.5; text-align: center;'>" +
                        "<strong>📧 AUTOMATED NOTIFICATION:</strong> This is an automated message from the Asset Management System. Please do not reply to this email." +
                        "</p>" +
                        "</div>" +
                        "</div>" +

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                TEAL_PRIMARY, TEAL_SECONDARY,
                GOLD_PRIMARY,
                TEAL_PRIMARY, headerText,
                GOLD_PRIMARY,
                password != null ? password : "Please update the system with the newly received equipment.",
                TEAL_PRIMARY,
                TEAL_PRIMARY, dateReceived,
                TEAL_PRIMARY, stock.getLpoNumber(),
                TEAL_PRIMARY, stock.getSupplier().getName(),
                TEAL_PRIMARY, stock.getBranch().getName(),
                viewStockLink,
                TEAL_PRIMARY, TEAL_SECONDARY, buttonText,
                TEAL_PRIMARY,
                GOLD_PRIMARY
        );
    }

    /**
     * FIXED: Notification email visibility issues
     */
    private String getNotificationContent(String username, String assetName) {
        return format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "<meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "<title>Asset Assignment Notification</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f0f4f8;'>" +
                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f9f2e7 100%%); min-height: 100vh; padding: 40px 20px;'>" +
                        "<div style='max-width: 650px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,0.1);'>" +

                        // Header - FIXED
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 30px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 280px; height: auto; filter: brightness(0) invert(1); margin-bottom: 20px;' />" +
                        "<h1 style='color: #ffffff; margin: 0; font-size: 26px; font-weight: 400; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);'>Asset Assignment</h1>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content - FIXED
                        "<div style='padding: 50px 40px; text-align: center; background-color: #ffffff;'>" +
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); border-radius: 100px; width: 80px; height: 80px; margin: 0 auto 30px; display: flex; align-items: center; justify-content: center;'>" +
                        "<span style='color: #ffffff; font-size: 30px;'>🎯</span>" +
                        "</div>" +

                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 24px; font-weight: 600;'>You've been assigned a new Asset</h2>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin: 0 auto 30px;'></div>" +

                        "<p style='color: #4a5568; font-size: 18px; margin: 25px 0;'>Dear <strong style='color: %s;'>%s</strong>,</p>" +

                        "<div style='background: linear-gradient(135deg, #f7fafc 0%%, #edf2f7 100%%); border-radius: 16px; padding: 30px; margin: 30px 0; border-left: 6px solid %s;'>" +
                        "<p style='color: #2d3748; font-size: 17px; line-height: 1.7; margin: 0;'>" +
                        "This is to inform you that asset <strong style='color: %s; background: rgba(188,137,44,0.1); padding: 4px 12px; border-radius: 20px;'>%s</strong> has officially been assigned to you." +
                        "</p>" +
                        "</div>" +

                        "<p style='color: #4a5568; font-size: 16px; line-height: 1.6; margin: 30px 0;'>Thank you for your continued support and commitment.</p>" +

                        "<div style='margin-top: 40px;'>" +
                        "<p style='color: #718096; font-size: 14px; margin: 5px 0;'>Regards,</p>" +
                        "<p style='color: %s; font-size: 16px; font-weight: 600; margin: 5px 0;'>Admin Department</p>" +
                        "<p style='color: %s; font-size: 16px; font-weight: 600; margin: 5px 0;'>Pride Bank</p>" +
                        "</div>" +

                        // Disclaimer
                        "<div style='background: #fffbf0; border: 2px solid %s; border-radius: 12px; padding: 20px; margin-top: 40px;'>" +
                        "<p style='color: #744210; font-size: 13px; margin: 0; line-height: 1.5;'>" +
                        "<strong>📧 AUTOMATED NOTIFICATION:</strong> This is an automated message from the Pride Bank Asset Management System. Please do not reply to this email." +
                        "</p>" +
                        "</div>" +
                        "</div>" +

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                TEAL_PRIMARY, TEAL_SECONDARY,
                GOLD_PRIMARY,
                GOLD_PRIMARY, GOLD_SECONDARY,
                TEAL_PRIMARY,
                GOLD_PRIMARY,
                TEAL_PRIMARY, username,
                TEAL_PRIMARY,
                TEAL_PRIMARY, assetName,
                TEAL_PRIMARY,
                TEAL_PRIMARY,
                GOLD_PRIMARY
        );
    }

    /**
     * FIXED: Leave email visibility issues
     */
    private String getLeaveEmailContent(
            String actingUserName,
            String leaveUserName,
            String leaveType,
            String actingTitle,
            String startDate,
            String endDate,
            String leaveReason
    ) {
        return format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "<meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "<title>Acting Officer Assignment</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f0f4f8;'>" +
                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f9f2e7 100%%); min-height: 100vh; padding: 40px 20px;'>" +
                        "<div style='max-width: 700px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,0.1);'>" +

                        // Header - FIXED
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 30px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 280px; height: auto; filter: brightness(0) invert(1); margin-bottom: 20px;' />" +
                        "<h1 style='color: #ffffff; margin: 0; font-size: 26px; font-weight: 400; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);'>Acting Officer Assignment</h1>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content - FIXED
                        "<div style='padding: 50px 40px; background-color: #ffffff;'>" +
                        "<div style='text-align: center; margin-bottom: 30px;'>" +
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); border-radius: 100px; width: 80px; height: 80px; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center;'>" +
                        "<span style='color: #ffffff; font-size: 30px;'>👤</span>" +
                        "</div>" +
                        "</div>" +

                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 24px; font-weight: 600;'>You've been assigned as Acting Officer</h2>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin-bottom: 30px;'></div>" +

                        "<p style='color: #4a5568; font-size: 18px; margin: 25px 0;'>Dear <strong style='color: %s;'>%s</strong>,</p>" +

                        "<p style='color: #2d3748; font-size: 17px; line-height: 1.8; margin: 25px 0;'>" +
                        "This is to inform you that <strong style='color: %s;'>%s</strong> has officially gone on <strong style='color: %s; background: rgba(188,137,44,0.1); padding: 4px 12px; border-radius: 20px;'>%s</strong> leave from " +
                        "<strong style='color: %s;'>%s</strong> to <strong style='color: %s;'>%s</strong>." +
                        "</p>" +

                        "<div style='background: linear-gradient(135deg, #f7fafc 0%%, #edf2f7 100%%); border-radius: 16px; padding: 30px; margin: 30px 0; border-left: 6px solid %s;'>" +
                        "<p style='color: #2d3748; font-size: 17px; line-height: 1.7; margin: 0;'>" +
                        "You have been assigned as the acting officer for this period. Please take over the responsibilities of the role titled: " +
                        "<strong style='color: %s; background: rgba(5,84,75,0.1); padding: 4px 12px; border-radius: 20px;'>%s</strong>." +
                        "</p>" +
                        "</div>" +

                        "<div style='background: #fffbf0; border: 2px solid %s; border-radius: 16px; padding: 25px; margin: 30px 0;'>" +
                        "<h4 style='color: %s; margin: 0 0 15px 0; font-size: 16px;'>📝 Reason for Leave:</h4>" +
                        "<p style='color: #744210; margin: 0; font-size: 15px; line-height: 1.6; font-style: italic;'>%s</p>" +
                        "</div>" +

                        "<p style='color: #4a5568; font-size: 16px; line-height: 1.6; margin: 30px 0;'>Thank you for your continued support and commitment.</p>" +

                        "<div style='text-align: center; margin-top: 40px;'>" +
                        "<p style='color: #718096; font-size: 14px; margin: 5px 0;'>Regards,</p>" +
                        "<p style='color: %s; font-size: 16px; font-weight: 600; margin: 5px 0;'>HR Department</p>" +
                        "<p style='color: %s; font-size: 16px; font-weight: 600; margin: 5px 0;'>Pride Bank</p>" +
                        "</div>" +

                        // Disclaimer
                        "<div style='background: #fff5f5; border: 2px solid #fed7d7; border-radius: 12px; padding: 20px; margin-top: 40px;'>" +
                        "<p style='color: #742a2a; font-size: 13px; margin: 0; line-height: 1.5; text-align: center;'>" +
                        "<strong>📧 AUTOMATED NOTIFICATION:</strong> This is an automated message from the Pride Bank HR System. Please do not reply to this email." +
                        "</p>" +
                        "</div>" +
                        "</div>" +

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                TEAL_PRIMARY, TEAL_SECONDARY,
                GOLD_PRIMARY,
                GOLD_PRIMARY, GOLD_SECONDARY,
                TEAL_PRIMARY,
                GOLD_PRIMARY,
                TEAL_PRIMARY, actingUserName,
                TEAL_PRIMARY, leaveUserName,
                TEAL_PRIMARY, leaveType,
                TEAL_PRIMARY, startDate,
                TEAL_PRIMARY, endDate,
                TEAL_PRIMARY,
                TEAL_PRIMARY, actingTitle,
                GOLD_PRIMARY,
                GOLD_PRIMARY, leaveReason,
                TEAL_PRIMARY,
                TEAL_PRIMARY
        );
    }

    /**
     * Common footer for all emails - FIXED
     */
    private String getEmailFooter() {
        return format(
                "<div style='background: #f8fafc; padding: 40px 30px; border-top: 1px solid #e2e8f0;'>" +
                        "<div style='text-align: center;'>" +
                        "<div style='margin-bottom: 25px;'>" +
                        "<h3 style='color: %s; margin: 0; font-size: 22px; font-weight: 700;'>Pride Bank</h3>" +
                        "<p style='color: %s; font-size: 14px; margin: 8px 0 0 0; font-style: italic;'>Excellence in Banking</p>" +
                        "</div>" +

                        "<div style='background: #ffffff; border-radius: 12px; padding: 25px; margin: 25px 0; box-shadow: 0 2px 10px rgba(0,0,0,0.05);'>" +
                        "<div style='margin-bottom: 15px;'>" +
                        "<p style='color: #4a5568; font-size: 14px; margin: 0 0 8px 0; line-height: 1.6;'>" +
                        "<strong style='color: %s;'>📍 Address:</strong> Plot 6-9, Victoria Office Park<br>" +
                        "6-9 Ben Kiwanuka Okot Close, Bukoto, Kampala, Uganda" +
                        "</p>" +
                        "</div>" +
                        "<p style='color: #4a5568; font-size: 14px; margin: 0;'>" +
                        "<strong style='color: %s;'>📞 Contact:</strong> +256 754 346930 (Call or WhatsApp)" +
                        "</p>" +
                        "</div>" +

                        "<div style='border-top: 2px solid %s; padding-top: 20px;'>" +
                        "<p style='color: #a0aec0; font-size: 12px; margin: 0;'>" +
                        "&copy; 2025 Pride Bank Limited. All rights reserved." +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "</div>",
                TEAL_PRIMARY,
                GOLD_PRIMARY,
                TEAL_PRIMARY,
                TEAL_PRIMARY,
                GOLD_PRIMARY
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Workflow step email
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sends a professional workflow step notification email.
     *
     * @param email        Recipient email address
     * @param subject      Email subject
     * @param greeting     Recipient's first name or "Team"
     * @param stepTitle    The step name e.g. "Manager Approval Required"
     * @param instructions Step-specific instruction text
     * @param request      The request entity
     * @param step         The current approval step (may be null for rejection)
     * @param stepStatus   "PENDING", "REJECTED", "CC"
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
     * Both {@code sendEmailVerification} and {@code sendWorkflowEmail} route through
     * here, so callers pass the raw stored address and never clean it themselves.
     */
    private String cleanRecipient(String email) {
        String cleaned = new ExtractNumberFromEmailAddress(email).getCleanedEmail();
        if (!Objects.equals(email, cleaned)) {
            logger.debug("Recipient rewritten for SMTP: {} -> {}", email, cleaned);
        }
        return cleaned;
    }

    private String getWorkflowEmailContent(
            String greeting,
            String stepTitle,
            String instructions,
            Request request,
            String stepStatus
    ) {
        String statusColor;
        String statusLabel;
        String statusIcon;
        switch (stepStatus != null ? stepStatus.toUpperCase() : "PENDING") {
            case "REJECTED" -> { statusColor = "#C53030"; statusLabel = "REJECTED"; statusIcon = "✗"; }
            case "CC"       -> { statusColor = TEAL_PRIMARY; statusLabel = "FOR YOUR INFORMATION"; statusIcon = "ℹ"; }
            default         -> { statusColor = GOLD_PRIMARY; statusLabel = "ACTION REQUIRED"; statusIcon = "⚡"; }
        }

        String commodityRows = buildCommodityRows(request);
        String requesterName = (request.getRequester() != null)
                ? request.getRequester().getFirstName() + " " + request.getRequester().getLastName()
                : "N/A";
        String requesterBranch = (request.getRequester() != null && request.getRequester().getBranch() != null)
                ? request.getRequester().getBranch().getName() : "N/A";
        String viewLink = format("%s/assets-mgt/asset-request/view?id=%d", verificationUrl, request.getId());

        return format(
                "<!DOCTYPE html><html lang='en'><head>" +
                "<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>%s — Pride Bank</title></head>" +
                "<body style='margin:0; padding:0; font-family:-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color:#f0f4f8;'>" +
                "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f0f7ff 100%%); min-height:100vh; padding:40px 16px;'>" +
                "<div style='max-width:620px; margin:0 auto; background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 20px 60px rgba(0,0,0,0.10);'>" +

                // Header
                "<div style='background-color:%s; background-image:linear-gradient(135deg,%s 0%%,%s 100%%); padding:40px 32px; text-align:center;'>" +
                "<img src='cid:logoImage' alt='Pride Bank Limited' style='max-width:240px; height:auto; filter:brightness(0) invert(1); margin-bottom:20px;'/>" +
                "<div style='display:inline-block; background:rgba(255,255,255,0.18); border:1px solid rgba(255,255,255,0.4); border-radius:50px; padding:6px 20px; margin-top:8px;'>" +
                "<p style='margin:0; color:#ffffff; font-size:12px; font-weight:700; letter-spacing:2px; text-transform:uppercase;'>%s %s</p>" +
                "</div>" +
                "</div>" +

                // Body
                "<div style='padding:36px 32px;'>" +
                "<p style='color:#2d3748; font-size:16px; font-weight:600; margin:0 0 6px 0;'>Dear %s,</p>" +
                "<p style='color:#4a5568; font-size:15px; line-height:1.7; margin:0 0 24px 0;'>%s</p>" +

                // Request card
                "<div style='background:#f7fafc; border:1px solid #e2e8f0; border-radius:14px; overflow:hidden; margin:24px 0;'>" +
                "<div style='background:%s; padding:14px 20px;'><p style='margin:0; color:#ffffff; font-size:13px; font-weight:700; letter-spacing:1px; text-transform:uppercase;'>📋 Request Details</p></div>" +
                "<div style='padding:20px 24px;'>" +
                "<table style='width:100%%; border-collapse:collapse;'>" +
                "<tr><td style='padding:8px 0; color:#718096; font-size:13px; width:40%%;'>Request ID</td><td style='padding:8px 0; color:#2d3748; font-size:14px; font-weight:600;'>#%d</td></tr>" +
                "<tr style='border-top:1px solid #e2e8f0;'><td style='padding:8px 0; color:#718096; font-size:13px;'>Request Name</td><td style='padding:8px 0; color:#2d3748; font-size:14px; font-weight:600;'>%s</td></tr>" +
                "<tr style='border-top:1px solid #e2e8f0;'><td style='padding:8px 0; color:#718096; font-size:13px;'>Requested By</td><td style='padding:8px 0; color:#2d3748; font-size:14px;'>%s</td></tr>" +
                "<tr style='border-top:1px solid #e2e8f0;'><td style='padding:8px 0; color:#718096; font-size:13px;'>Branch</td><td style='padding:8px 0; color:#2d3748; font-size:14px;'>%s</td></tr>" +
                "<tr style='border-top:1px solid #e2e8f0;'><td style='padding:8px 0; color:#718096; font-size:13px;'>Priority</td><td style='padding:8px 0; color:#2d3748; font-size:14px;'>%s</td></tr>" +
                "</table></div></div>" +

                // Items
                "%s" +

                // CTA (hide for CC)
                "%s" +

                // Step title
                "<p style='color:#718096; font-size:12px; margin:24px 0 0 0;'>This is an automated notification from the Pride Bank ERP system. Please do not reply to this email.</p>" +
                "</div>" +

                "%s" + // footer
                "</div></div></body></html>",

                // title
                stepTitle,

                // header bg
                TEAL_PRIMARY, TEAL_PRIMARY, TEAL_SECONDARY,

                // badge
                statusIcon, statusLabel,

                // greeting
                greeting, instructions,

                // request card header
                TEAL_PRIMARY,

                // request details
                request.getId(),
                request.getName() != null ? request.getName() : "N/A",
                requesterName,
                requesterBranch,
                request.getPriority() != null ? request.getPriority() : "Normal",

                // commodity rows
                commodityRows,

                // CTA button
                ("CC".equalsIgnoreCase(stepStatus)) ? "" :
                        format("<div style='text-align:center; margin:28px 0;'>" +
                               "<a href='%s' style='display:inline-block; background:%s; color:#ffffff; text-decoration:none; " +
                               "padding:14px 36px; border-radius:50px; font-size:14px; font-weight:700; letter-spacing:0.5px;'>View Request</a>" +
                               "</div>", viewLink, TEAL_PRIMARY),

                // footer
                getEmailFooter()
        );
    }

    private String buildCommodityRows(Request request) {
        if (request.getCommodities() == null || request.getCommodities().isEmpty()) return "";

        StringBuilder rows = new StringBuilder();
        rows.append("<div style='background:#f7fafc; border:1px solid #e2e8f0; border-radius:14px; overflow:hidden; margin:16px 0;'>")
            .append("<div style='background:" + GOLD_PRIMARY + "; padding:14px 20px;'><p style='margin:0; color:#ffffff; font-size:13px; font-weight:700; letter-spacing:1px; text-transform:uppercase;'>📦 Requested Items</p></div>")
            .append("<div style='padding:0 24px;'><table style='width:100%%; border-collapse:collapse;'>")
            .append("<tr style='background:#edf2f7;'><th style='padding:10px 8px; text-align:left; color:#4a5568; font-size:12px; text-transform:uppercase;'>Item</th>" +
                    "<th style='padding:10px 8px; text-align:right; color:#4a5568; font-size:12px; text-transform:uppercase;'>Qty</th></tr>");

        for (var c : request.getCommodities()) {
            String itemName = (c.getCommodity() != null && c.getCommodity().getName() != null)
                    ? c.getCommodity().getName() : "Item";
            rows.append(format("<tr style='border-top:1px solid #e2e8f0;'>" +
                    "<td style='padding:10px 8px; color:#2d3748; font-size:14px;'>%s</td>" +
                    "<td style='padding:10px 8px; color:#2d3748; font-size:14px; text-align:right; font-weight:600;'>%d</td></tr>",
                    itemName, c.getQuantity()));
        }
        rows.append("</table></div></div>");
        return rows.toString();
    }
}