package com.vehiclerental.service.impl;

import com.vehiclerental.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Outgoing email.
 *
 * With spring.mail.host set, messages go out over SMTP. Without it - a
 * laptop, a demo - every message is written to the application log in full,
 * links included, so password reset and email verification can still be
 * exercised end to end. Nothing is ever silently dropped.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender sender;
    private final String from;

    public MailServiceImpl(ObjectProvider<JavaMailSender> sender,
                           @Value("${rental.mail.from:no-reply@axle.lk}") String from) {
        this.sender = sender.getIfAvailable();
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (sender == null) {
            log.info("""

                ===== EMAIL (no SMTP server configured - shown here instead) =====
                To:      {}
                Subject: {}

                {}
                ==================================================================""", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            sender.send(m);
        } catch (RuntimeException e) {
            log.error("Could not send \"{}\" to {}", subject, to, e);
        }
    }

    @Override
    public boolean isDelivering() {
        return sender != null;
    }
}
