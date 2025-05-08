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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pride.bank.erp.request.CommodityWithQuantityDTO;
import pride.bank.erp.request.Request;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    /**
     * Sends an email with an optional password.
     *
     * @param email      Recipient email address
     * @param subject    Subject of the email
     * @param token      Verification token
     * @param headerText Header text for the email
     * @param buttonText Text to display on the button
     * @param password   Optional password (can be null)
     * @param type       Type of email ('authentication' or 'requests')
     * @param request    request (only used when type is 'requests')
     * @return boolean indicating success or failure of email sending
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
            Request request
    ) {
        try {

            String htmlContent;

            if ("authentication".equalsIgnoreCase(type)) {
                Instant expirationInstant = Instant.now().plusMillis(tokenExpirationTime);
                String expirationTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(expirationInstant);
                htmlContent = getAuthenticationEmailContent(token, expirationTime, headerText, buttonText, password);
            } else if ("requests".equalsIgnoreCase(type)) {
                htmlContent = getRequestEmailContent(request, headerText, buttonText);
            } else {
                logger.error("Invalid email type: {}", type);
                return false;
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setFrom(fromEmail);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            logger.info("Email sent to {}", email);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send email to {}", email, e);
            return false;
        }
    }

    /**
     * Generates the HTML content for the email.
     *
     * @param token          Verification token
     * @param expirationTime Expiration time of the token
     * @param headerText     Header text of the email
     * @param buttonText     Button text to be displayed in the email
     * @param password       Optional password (can be null)
     * @return String containing the email's HTML content
     */
    private String getAuthenticationEmailContent(
            String token,
            String expirationTime,
            String headerText,
            String buttonText,
            String password
    ) {
        String verifyLink = String.format("%s/verify?token=%s", verificationUrl, token);

        String passwordSection = password != null
                ? String.format(
                "<p style='margin-top: 20px;'>Here is your temporary password for login: <strong>%s</strong></p>",
                password
        )
                : "";

        return String.format(
                "<div style='font-family: Arial, sans-serif; line-height: 1.5; color: #333;'>"
                        + "<h2 style='color: #4CAF50;'>%s</h2>"
                        + "<p>Click the button below to <span style='text-transform: lowercase;'>%s</span>:</p>"
                        + "<a href='%s' style='display: inline-block; padding: 10px 20px; color: white; background-color: #4CAF50; text-decoration: none; border-radius: 5px;'>%s</a>"
                        + "<p style='margin-top: 20px;'>This token is valid until: <strong>%s</strong></p>"
                        + "%s"
                        + "<p style='font-size: 14px; color: #777;'>If you did not request this email, please ignore it.</p>"
                        + "</div>",
                headerText, headerText, verifyLink, buttonText, expirationTime, passwordSection
        );
    }

    /**
     * Generates the HTML content for request emails (with table of commodities and quantities).
     *
     * @param request Request object containing commodity details
     * @return String containing the email's HTML content
     */
    private String getRequestEmailContent(Request request, String approver, String buttonText) {
        StringBuilder tableRows = getStringBuilder(request);
//        StringBuilder tableRows = new StringBuilder();

        String viewRequestLink = String.format("%s/%s", verificationUrl, request.getId());
        String requester = String.format("%s %s", request.getRequester().getFirstName(), request.getRequester().getLastName());

        return String.format(
                "<div style='font-family: Arial, sans-serif; line-height: 1.5; color: #333;'>"
                        + "<p><strong>Dear %s</strong></p>"
                        + "<p>A new store request has been initiated by <strong>%s</strong> for the following item(s):</p>"
                        + "<table border='1' cellpadding='10' cellspacing='0' style='border-collapse: collapse;'>"
                        + "<thead><tr><th>Request ID#</th><th>Item Name</th><th>Quantity</th></tr></thead>"
                        + "<tbody>%s</tbody>"
                        + "</table>"
                        + "<p style='margin-top: 20px;'>"
                        + "<p>To view the request, click the link below</p>"
                        + "<a href='%s' style='display: inline-block; padding: 10px 20px; color: white; background-color: #4CAF50; text-decoration: none; border-radius: 5px;'>"
                        + "%s"
                        + "</a>"
                        + "</p>"
                        + "<p style='color: #777'>DO NOT REPLY TO THIS MAIL. THIS IS AN AUTOMATED MAIL DELIVERY FROM ASSET MANAGEMENT SYSTEM</p>"
                        + "</div>",
                approver,
                requester,
                tableRows,
                viewRequestLink,
                buttonText
        );
    }

    private static StringBuilder getStringBuilder(Request request) {
        StringBuilder tableRows = new StringBuilder();

        int count = 1;
        List<CommodityWithQuantityDTO> commodities = request.getCommodities();
        for (CommodityWithQuantityDTO commodity : commodities) {
            tableRows.append(String.format(
                    "<tr>"
                            + "<td>%d</td>"
                            + "<td>%s (%s)</td>"
                            + "<td>%d</td>"
                            + "</tr>",
                    count++,
                    commodity.getCommodity().getName(),
                    commodity.getCommodity().getGroupName(),
                    commodity.getQuantity()
            ));
        }
        return tableRows;
    }
}
