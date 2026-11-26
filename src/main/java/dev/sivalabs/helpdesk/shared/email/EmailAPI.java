package dev.sivalabs.helpdesk.shared.email;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends email, plain text or HTML: the shared facade any module may use. */
@Component
public class EmailAPI {
    private final JavaMailSender mailSender;

    public EmailAPI(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String from, String to, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    /**
     * Sends an HTML message to several recipients, with copies to {@code cc} (may be empty).
     *
     * @throws MailPreparationException when the message cannot be built (a malformed address)
     */
    public void sendHtml(String from, List<String> to, List<String> cc, String subject, String html) {
        mailSender.send(mimeMessage -> {
            var helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to.toArray(String[]::new));
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(String[]::new));
            }
            helper.setSubject(subject);
            helper.setText(html, true);
        });
    }
}
