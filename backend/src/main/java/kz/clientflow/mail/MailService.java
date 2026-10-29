package kz.clientflow.mail;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kz.clientflow.config.AppProperties;

/**
 * Sends authentication emails in the user's language.
 * Links are built from the configured public URL, never from the request Host header.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final AppProperties properties;

    public MailService(JavaMailSender mailSender, MessageSource messages, AppProperties properties) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuthEmail(AuthEmail email) {
        Locale locale = Locale.forLanguageTag(email.language());
        String baseUrl = properties.publicUrl().replaceAll("/+$", "");

        String key;
        Object[] args;
        switch (email.type()) {
            case VERIFY_EMAIL -> {
                key = "mail.verify";
                args = new Object[] {email.fullName(), baseUrl + "/verify-email?token=" + email.rawToken(),
                        properties.tokens().verificationTtl().toHours()};
            }
            case PASSWORD_RESET -> {
                key = "mail.reset";
                args = new Object[] {email.fullName(), baseUrl + "/reset-password?token=" + email.rawToken(),
                        properties.tokens().passwordResetTtl().toMinutes()};
            }
            case ACCOUNT_EXISTS -> {
                key = "mail.exists";
                args = new Object[] {email.fullName(), baseUrl + "/login", baseUrl + "/forgot-password"};
            }
            default -> throw new IllegalStateException("Unknown email type");
        }

        send(email.to(), key, args, locale);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvitation(InvitationEmail email) {
        String baseUrl = properties.publicUrl().replaceAll("/+$", "");
        send(email.to(), "mail.invite", new Object[] {email.inviterName(), email.workspaceName(),
                baseUrl + "/login", baseUrl + "/register"}, Locale.forLanguageTag(email.language()));
    }

    private void send(String to, String key, Object[] args, Locale locale) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(messages.getMessage(key + ".subject", null, locale));
        message.setText(messages.getMessage(key + ".body", args, locale));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // The exception may contain the message text (with a token), so it is not logged.
            // The user can request the email again.
            log.warn("Failed to send {} email: {}", key, ex.getClass().getSimpleName());
        }
    }
}
