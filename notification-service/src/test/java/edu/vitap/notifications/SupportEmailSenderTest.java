package edu.vitap.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SupportEmailSenderTest {
    @Test
    void sendsRequestToConfiguredSupportAddressWithTrackingId() throws Exception {
        Fixture fixture = new Fixture("help@example.test");
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", "CP123456", "Parcel has not moved.");

        fixture.sender.send(request, Instant.parse("2026-10-11T12:00:00Z"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(fixture.mailSender).send(sent.capture());
        assertEquals("help@example.test", sent.getValue().getAllRecipients()[0].toString());
        assertEquals("Customer Support Request - CP123456", sent.getValue().getSubject());
        assertEquals("customer@example.test", sent.getValue().getReplyTo()[0].toString());
    }

    @Test
    void sendsRequestWithoutOptionalTrackingId() throws Exception {
        Fixture fixture = new Fixture("help@example.test");
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", " ", "I need booking assistance.");

        fixture.sender.send(request, Instant.parse("2026-10-11T12:00:00Z"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(fixture.mailSender).send(sent.capture());
        assertEquals("Customer Support Request", sent.getValue().getSubject());
    }

    @Test
    void hidesSmtpFailureDetailsFromCaller() {
        Fixture fixture = new Fixture("help@example.test");
        doThrow(new MailSendException("private relay detail"))
                .when(fixture.mailSender).send(any(MimeMessage.class));

        SupportEmailException error = assertThrows(SupportEmailException.class,
                () -> fixture.sender.send(new SupportContactRequest(
                        "Customer", "customer@example.test", null, "Please help."),
                        Instant.parse("2026-10-11T12:00:00Z")));

        assertEquals("Support email delivery is unavailable", error.getMessage());
        assertTrue(error.getCause() == null);
    }

    @Test
    void missingSupportAddressDoesNotAttemptToSend() {
        Fixture fixture = new Fixture("");
        assertThrows(SupportEmailException.class,
                () -> fixture.sender.send(new SupportContactRequest(
                        "Customer", "customer@example.test", null, "Please help."),
                        Instant.parse("2026-10-11T12:00:00Z")));
        verify(fixture.mailSender, never()).send(any(MimeMessage.class));
    }

    private static class Fixture {
        final JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        final SupportEmailSender sender;

        @SuppressWarnings("unchecked")
        Fixture(String recipient) {
            ObjectProvider<JavaMailSender> provider = org.mockito.Mockito.mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(mailSender);
            when(mailSender.createMimeMessage())
                    .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
            sender = new SupportEmailSender(recipient, "noreply@example.test", provider);
        }
    }
}
