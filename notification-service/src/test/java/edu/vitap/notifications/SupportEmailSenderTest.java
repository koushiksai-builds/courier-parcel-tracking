package edu.vitap.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupportEmailSenderTest {
    @Test
    void sendsRequestToConfiguredSupportAddressWithTrackingId() {
        Fixture fixture = new Fixture("help@example.test");
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", "CP123456", "Parcel has not moved.");

        fixture.sender.send(request, Instant.parse("2026-10-11T12:00:00Z"));

        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(fixture.sendGrid).send(sent.capture());
        assertEquals("help@example.test", sent.getValue().to());
        assertEquals("Customer Support Request - CP123456", sent.getValue().subject());
        assertEquals("customer@example.test", sent.getValue().replyTo());
    }

    @Test
    void sendsRequestWithoutOptionalTrackingId() {
        Fixture fixture = new Fixture("help@example.test");
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", " ", "I need booking assistance.");

        fixture.sender.send(request, Instant.parse("2026-10-11T12:00:00Z"));

        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(fixture.sendGrid).send(sent.capture());
        assertEquals("Customer Support Request", sent.getValue().subject());
    }

    @Test
    void hidesProviderFailureDetailsFromCaller() {
        Fixture fixture = new Fixture("help@example.test");
        doThrow(new SendGridException("private provider detail"))
                .when(fixture.sendGrid).send(any(OutboundEmail.class));

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
        verify(fixture.sendGrid, never()).send(any(OutboundEmail.class));
    }

    private static class Fixture {
        final SendGridEmailClient sendGrid = org.mockito.Mockito.mock(SendGridEmailClient.class);
        final SupportEmailSender sender;

        Fixture(String recipient) {
            org.mockito.Mockito.when(sendGrid.isConfigured()).thenReturn(true);
            sender = new SupportEmailSender(recipient, sendGrid);
        }
    }
}
