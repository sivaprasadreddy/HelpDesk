package dev.sivalabs.helpdesk.shared.email;

import java.util.List;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends plain-text email: the shared facade any module may use. */
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

    /** Sends one message to several recipients, with copies to {@code cc} (either may be empty, not both). */
    public void send(String from, List<String> to, List<String> cc, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.toArray(String[]::new));
        if (!cc.isEmpty()) {
            message.setCc(cc.toArray(String[]::new));
        }
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
