package com.vehiclerental.service;

public interface MailService {

    /** Sends a plain-text email. Never throws: a mail problem must not undo the action that sent it. */
    void send(String to, String subject, String body);

    /** True when a real SMTP server is configured; false means mail goes to the log. */
    boolean isDelivering();
}
