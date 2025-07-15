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
import pride.bank.erp.stock.Stock;

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
            Request request,
            Stock stock
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
                htmlContent = getRequestEmailContent(request, headerText, buttonText, password);
            } else if ("stocks".equalsIgnoreCase(type)) {
                htmlContent = getStockEmailContent(stock, headerText, buttonText, password);
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
                        + "<a href='%s' style='display: inline-block; padding: 10px 20px; color: white; background-color: #05544B; text-decoration: none; border-radius: 5px;'>%s</a>"
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
    private String getRequestEmailContent(Request request, String approver, String buttonText, String comment) {
        StringBuilder tableRows = getStringBuilder(request);
        int totalQuantity = getTotalQuantity(request);

        String totalRow = String.format(
                "<tr style='background-color: #835F1E; color: white;'>"
                        + "<td style='padding: 10px; font-weight: bold;' colspan='3'>Total Quantity</td>"
                        + "<td style='padding: 10px; font-weight: bold;'>%d</td>"
                        + "</tr>",
                totalQuantity
        );


        String viewRequestLink = String.format("%s/%s", verificationUrl, request.getId());
        String requester = String.format("%s %s", request.getRequester().getFirstName(), request.getRequester().getLastName());

        String text = comment != null
                ? String.format("<p>%s</p>", comment) :
                String.format("<p>A new store request has been initiated by <strong>%s</strong> for the following item(s):</p>", requester);

        return String.format(
                "<div style='font-family: Arial, sans-serif; line-height: 1.5; color: #333;'>"
                        + "<p><strong>Dear %s</strong></p>"
                        + "%s"
                        + "<table style='width: 60%%; border-collapse: collapse; font-size: 14px;'>"
                        + "<thead>"
                        + "<tr style='background-color: #05544B; color: #FFFFFF;'>"
                        + "<th style='padding: 10px; text-align: left;'>Request ID#</th>"
                        + "<th style='padding: 10px; text-align: left;'>Item Name</th>"
                        + "<th style='padding: 10px; text-align: left;'>Unit of Measure</th>"
                        + "<th style='padding: 10px; text-align: left;'>Quantity</th>"
                        + "</tr>"
                        + "</thead>"
                        + "<tbody>%s</tbody>"
                        + "<tfoot>%s</tfoot>"
                        + "</table>"

                        + "<p style='margin-top: 20px;'>"
                        + "To view the request, click the link below:"
                        + "</p>"
                        + "<a href='%s' style='display: inline-block; padding: 10px 20px; color: white; background-color: #05544B; text-decoration: none; border-radius: 5px;'>"
                        + "%s"
                        + "</a>"

                        + "<p style='color: #777; margin-top: 30px;'>"
                        + "DO NOT REPLY TO THIS EMAIL. THIS IS AN AUTOMATED NOTIFICATION FROM THE ASSET MANAGEMENT SYSTEM."
                        + "</p>"
                        + "</div>",
                approver,
                text,
                tableRows,
                totalRow,
                viewRequestLink,
                buttonText
        );
    }


    private static StringBuilder getStringBuilder(Request request) {
        StringBuilder tableRows = new StringBuilder();
        List<CommodityWithQuantityDTO> commodities = request.getCommodities();

        int count = 1;
        for (CommodityWithQuantityDTO commodity : commodities) {
            boolean isEven = count % 2 == 0;
            String rowStyle = isEven ? "background-color: #f9f9f9;" : "background-color: #FFFFFF;";

            tableRows.append(String.format(
                    "<tr style='%s'>"
                            + "<td style='padding: 10px;'>%d</td>"
                            + "<td style='padding: 10px;'>%s</td>"
                            + "<td style='padding: 10px;'>%s</td>"
                            + "<td style='padding: 10px;'>%d</td>"
                            + "</tr>",
                    rowStyle,
                    count++,
                    commodity.getCommodity().getName(),
                    commodity.getCommodity().getGroupName(),
                    commodity.getQuantity()
            ));
        }
        return tableRows;
    }


    private int getTotalQuantity(Request request) {
        return request.getCommodities().stream()
                .mapToInt(CommodityWithQuantityDTO::getQuantity)
                .sum();
    }

    private String getStockEmailContent(
            Stock stock,
            String headerText,
            String buttonText,
            String password
    ) {
        String viewStockLink = String.format("%s/stocks/%s", verificationUrl, stock.getId());
        String dateReceived = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(stock.getCreateDate());

        return String.format(
                "<div style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>"
                        + "<h2 style='color: #05544B;'>%s</h2>"
                        + "<p><strong>Date Received:</strong> %s</p>"
                        + "<p><strong>LPO Number:</strong> %s</p>"
                        + "<p><strong>Supplier:</strong> %s</p>"
                        + "<p><strong>Branch:</strong> %s</p>"

                        + "<p style='margin-top: 20px;'>%s</p>"

                        + "<a href='%s' style='display: inline-block; padding: 10px 20px; color: white; "
                        + "background-color: #05544B; text-decoration: none; border-radius: 5px;'>%s</a>"

                        + "<p style='color: #999; font-size: 13px; margin-top: 30px;'>"
                        + "DO NOT REPLY TO THIS EMAIL. THIS IS AN AUTOMATED NOTIFICATION FROM THE ASSET MANAGEMENT SYSTEM."
                        + "</p>"
                        + "</div>",
                headerText,
                dateReceived,
                stock.getLpoNumber(),
                stock.getSupplier().getName(),
                stock.getBranch().getName(),
                password != null ? password : "Please update the system with the newly received equipment.",
                viewStockLink,
                buttonText
        );
    }

}
