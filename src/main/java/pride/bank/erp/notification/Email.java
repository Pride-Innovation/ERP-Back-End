/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pride.bank.erp.request.CommodityWithQuantityDTO;
import pride.bank.erp.request.Request;
import pride.bank.erp.stock.Stock;

import java.io.File;
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
    private final JavaMailSender mailSender;

    @Value("${mail.sender.from}")
    private String fromEmail;

    @Value("${application.verification.url}")
    private String verificationUrl;

    @Value("${application.security.jwt.expiration}")
    private long tokenExpirationTime;

    // Brand colors
    private static final String TEAL_PRIMARY = "#05544B";
    private static final String TEAL_SECONDARY = "#0a6b5d";
    private static final String GOLD_PRIMARY = "#BC892C";
    private static final String GOLD_SECONDARY = "#d4a545";

    /**
     * Sends an email with an optional password.
     */
    @Async
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

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setFrom(fromEmail);
            helper.setText(htmlContent, true);

            String pathToAttachment = "src/main/resources/static/images/logo.png";
            FileSystemResource image = new FileSystemResource(new File(pathToAttachment));
            helper.addInline("logoImage", image);

            mailSender.send(mimeMessage);
            logger.info("Email sent to {}", email);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send email to {}", email, e);
            return false;
        }
    }

    /**
     * Generates the HTML content for authentication emails.
     * FIXED: White text visibility issues
     */
    private String getAuthenticationEmailContent(
            String token,
            String expirationTime,
            String headerText,
            String buttonText,
            String password,
            String reason
    ) {
        String verifyLink = Objects.equals(reason, "reset") ?
                format("%s/reset-password?token=%s", verificationUrl, token)
                : format("%s/verify?token=%s", verificationUrl, token);

        String passwordSection = password != null
                ? format(
                "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); border-radius: 12px; padding: 20px; margin: 25px 0; text-align: center;'>" +
                        "<h3 style='color: #ffffff; margin: 0 0 10px 0; font-size: 18px;'>Temporary Password</h3>" +
                        "<div style='background: rgba(255,255,255,0.2); border-radius: 8px; padding: 15px; font-family: monospace;'>" +
                        "<span style='color: #ffffff; font-size: 20px; font-weight: bold; letter-spacing: 2px;'>%s</span>" +
                        "</div>" +
                        "<p style='color: #ffffff; margin: 10px 0 0 0; font-size: 13px; opacity: 0.9;'>Please change this password after your first login</p>" +
                        "</div>",
                GOLD_PRIMARY, GOLD_SECONDARY, password
        )
                : "";

        return format(
                "<!DOCTYPE html>" +
                        "<html lang='en'>" +
                        "<head>" +
                        "<meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "<title>%s</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f0f4f8;'>" +
                        "<div style='background: linear-gradient(135deg, #e8f5f3 0%%, #f9f2e7 100%%); min-height: 100vh; padding: 40px 20px;'>" +
                        "<div style='max-width: 650px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,0.1);'>" +

                        // Header Section - FIXED
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 30px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 280px; height: auto; filter: brightness(0) invert(1); margin-bottom: 20px;' />" +
                        "<h1 style='color: #01607A; margin: 0; font-size: 24px; font-weight: 400; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);'>Account Verification</h1>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content Section - FIXED
                        "<div style='padding: 50px 40px; background-color: #ffffff;'>" +
                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 26px; font-weight: 600;'>%s</h2>" +
                        "<div style='width: 50px; height: 3px; background: %s; margin-bottom: 30px;'></div>" +

                        "<p style='color: #4a5568; font-size: 16px; line-height: 1.7; margin: 25px 0;'>Click the button below to <span style='text-transform: lowercase; color: %s; font-weight: 600;'>%s</span>:</p>" +

                        // CTA Button - FIXED
                        "<div style='text-align: center; margin: 40px 0;'>" +
                        "<a href='%s' style='display: inline-block; padding: 18px 40px; color: #01607A; background: linear-gradient(135deg, %s 0%%, %s 100%%); text-decoration: none; border-radius: 50px; font-weight: 600; font-size: 16px; letter-spacing: 0.5px; text-transform: uppercase; box-shadow: 0 10px 30px rgba(5,84,75,0.3); transition: all 0.3s ease;'>%s</a>" +
                        "</div>" +

                        // Token Expiration
                        "<div style='background: #f7fafc; border-left: 4px solid %s; border-radius: 0 8px 8px 0; padding: 20px; margin: 30px 0;'>" +
                        "<p style='margin: 0; color: #4a5568; font-size: 15px;'><strong style='color: %s;'>⏰ Valid Until:</strong> %s</p>" +
                        "</div>" +

                        "%s" +

                        // Disclaimer
                        "<div style='background: #fff5f5; border: 1px solid #fed7d7; border-radius: 12px; padding: 20px; margin-top: 40px;'>" +
                        "<p style='color: #742a2a; font-size: 14px; margin: 0; line-height: 1.5;'>" +
                        "<strong>🔒 Security Notice:</strong> If you did not request this verification, please ignore this email and contact our support team immediately." +
                        "</p>" +
                        "</div>" +
                        "</div>" +

                        // Footer
                        getEmailFooter() +

                        "</div>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                headerText,
                TEAL_PRIMARY, TEAL_SECONDARY,
                GOLD_PRIMARY,
                TEAL_PRIMARY, headerText,
                GOLD_PRIMARY,
                TEAL_PRIMARY, headerText,
                verifyLink,
                TEAL_PRIMARY, TEAL_SECONDARY, buttonText,
                GOLD_PRIMARY,
                TEAL_PRIMARY, expirationTime,
                passwordSection
        );
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
                "<tr style='background: linear-gradient(135deg, %s 0%%, %s 100%%);'>" +
                        "<td style='padding: 18px 24px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase; color: #01607A; border: none;' colspan='3'>Total Quantity</td>" +
                        "<td style='padding: 18px 24px; font-weight: 700; font-size: 20px; color: #01607A; border: none;'>%d</td>" +
                        "</tr>",
                TEAL_PRIMARY, TEAL_SECONDARY, totalQuantity
        );

        String viewRequestLink = String.format("%s/%s", verificationUrl, request.getId());
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

                        // Header Section - FIXED title visibility
                        "<div style='background: linear-gradient(135deg, %s 0%%, %s 100%%); padding: 50px 40px; text-align: center;'>" +
                        "<img src='cid:logoImage' alt='Pride Bank Logo' style='max-width: 300px; height: auto; filter: brightness(0) invert(1); margin-bottom: 25px;' />" +
                        "<h1 style='color: #01607A; margin: 0; font-size: 32px; font-weight: 400; letter-spacing: 1px; text-shadow: 0 2px 4px rgba(0,0,0,0.4);'>Store Request Notification</h1>" +
                        "<div style='width: 80px; height: 4px; background: %s; margin: 20px auto; border-radius: 2px;'></div>" +
                        "</div>" +

                        // Content Section - FIXED
                        "<div style='padding: 50px 40px; background-color: #ffffff;'>" +
                        "<h2 style='color: %s; margin: 0 0 15px 0; font-size: 28px; font-weight: 600;'>Dear %s,</h2>" +
                        "<div style='width: 60px; height: 3px; background: %s; margin-bottom: 35px;'></div>" +

                        "%s" +

                        // Modern Table - FIXED headers visibility
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

                        // Call to Action - FIXED button text visibility
                        "<div style='text-align: center; margin: 50px 0; background-color: #ffffff;'>" +
                        "<p style='color: #4a5568; font-size: 16px; margin-bottom: 30px; font-weight: 500;'>To review and take action on this request, click the button below:</p>" +
                        "<a href='%s' style='display: inline-block; padding: 20px 45px; color: #01607A; background: linear-gradient(135deg, %s 0%%, %s 100%%); text-decoration: none; border-radius: 50px; font-weight: 700; font-size: 16px; letter-spacing: 0.5px; text-transform: uppercase; box-shadow: 0 12px 35px rgba(5,84,75,0.4); transition: all 0.3s ease;'>%s</a>" +
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
                // Arguments in correct order (20 total):
                TEAL_PRIMARY, TEAL_SECONDARY,              // 1,2: Header background gradient
                GOLD_PRIMARY,                              // 3: Header underline
                TEAL_PRIMARY, approver,                    // 4,5: Dear section
                GOLD_PRIMARY,                              // 6: Dear underline
                text,                                      // 7: Content text
                TEAL_PRIMARY,                              // 8: Table header background
                TEAL_PRIMARY, TEAL_PRIMARY, TEAL_PRIMARY, TEAL_PRIMARY, // 9,10,11,12: Individual th backgrounds
                tableRows, totalRow,                       // 13,14: Table content
                viewRequestLink,                           // 15: Button link
                TEAL_PRIMARY, TEAL_SECONDARY, buttonText,  // 16,17,18: Button styling and text
                GOLD_PRIMARY,                              // 19: Disclaimer border
                GOLD_PRIMARY                               // 20: Disclaimer text color
        );
    }

    private static String getString(Request request, String comment) {
        String requester = format("%s %s", request.getRequester().getFirstName(), request.getRequester().getLastName());

        return comment != null
                ? format("<div style='background: linear-gradient(135deg, #f0fff4 0%%, #e6fffa 100%%); border-left: 6px solid %s; padding: 25px; margin: 30px 0; border-radius: 0 12px 12px 0; box-shadow: 0 4px 15px rgba(5,84,75,0.1);'><p style='margin: 0; color: #2d3748; font-size: 16px; line-height: 1.7; font-style: italic;'>%s</p></div>", TEAL_PRIMARY, comment)
                : format("<p style='color: #4a5568; font-size: 17px; line-height: 1.8; margin: 30px 0;'>A new store request has been initiated by <strong style='color: %s; font-weight: 600;'>%s</strong> for the following item(s):</p>", TEAL_PRIMARY, requester);
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
                        "<h1 style='color: #01607A; margin: 0; font-size: 28px; font-weight: 400; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);'>Stock Notification</h1>" +
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
                        "<a href='%s' style='display: inline-block; padding: 18px 40px; color: #01607A; background: linear-gradient(135deg, %s 0%%, %s 100%%); text-decoration: none; border-radius: 50px; font-weight: 600; font-size: 16px; letter-spacing: 0.5px; text-transform: uppercase; box-shadow: 0 10px 30px rgba(5,84,75,0.3);'>%s</a>" +
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
}