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

import java.io.File;

/**
 * Sends already-rendered mail off the request thread.
 *
 * <p>{@link Email} used to be {@code @Async} as a whole, which meant the HTML was *built* on the
 * async thread too — from JPA entities handed across the boundary. Off the request thread there is
 * no open session and the entity is detached, so any association reached while rendering (and the
 * templates reach several: {@code request.getRequester().getBranch().getName()},
 * {@code stock.getSupplier().getName()}) could only work while every one of those mappings stayed
 * EAGER. The failure mode was silent: the exception was caught and logged, so mail simply stopped
 * arriving with nothing surfacing to the user.
 *
 * <p>Splitting it this way puts the async boundary after rendering — entities are read on the
 * caller's thread, where the session is open, and only text crosses over. It is a separate bean on
 * purpose: {@code @Async} is proxy-based, so calling it from within {@link Email} itself would be a
 * self-invocation that bypasses the proxy and quietly runs synchronously.
 */
@Service
@RequiredArgsConstructor
public class MailDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MailDispatcher.class);
    private static final String LOGO_PATH = "src/main/resources/static/images/logo.png";

    private final JavaMailSender mailSender;

    @Value("${mail.sender.from}")
    private String fromEmail;

    /**
     * @param recipient   already cleaned address
     * @param subject     mail subject
     * @param htmlContent fully rendered body — no entities, nothing lazy
     * @param context     short label used only for logging which mail failed
     */
    @Async
    public void send(String recipient, String subject, String htmlContent, String context) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setFrom(fromEmail);
            helper.setText(htmlContent, true);
            helper.addInline("logoImage", new FileSystemResource(new File(LOGO_PATH)));

            mailSender.send(mimeMessage);
            logger.info("{} email sent to {}", context, recipient);
        } catch (Exception e) {
            logger.error("Failed to send {} email to {}", context, recipient, e);
        }
    }
}
