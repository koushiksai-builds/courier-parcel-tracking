package edu.vitap.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.vitap.common.AppUser;
import edu.vitap.common.ParcelEvent;
import edu.vitap.common.ParcelStatus;
import edu.vitap.common.PaymentNotificationEvent;
import edu.vitap.common.Role;
import edu.vitap.common.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerEmailNotifierTest {
    @Test
    void sendsEscapedHtmlAndPersistsSuccess() {
        Fixture fixture = new Fixture(false);
        fixture.notifier.send(new ParcelEvent("CP123", 7L, ParcelStatus.BOOKED, "Parcel booked <script>", null,
                Instant.parse("2026-10-10T12:00:00Z"), "event-1"));

        ArgumentCaptor<EmailDelivery> saved = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(fixture.deliveries).save(saved.capture());
        assertEquals("SENT", saved.getValue().status);
        assertEquals(1, saved.getValue().attempts);
        assertEquals("customer@example.test", saved.getValue().recipient);
        assertTrue(saved.getValue().htmlBody.contains("&lt;script&gt;"));
        verify(fixture.sendGrid).send(any(OutboundEmail.class));
    }

    @Test
    void failureIsRecordedAndCanRetryWithoutCreatingAnotherDelivery() {
        Fixture fixture = new Fixture(false);
        doThrow(new SendGridException("temporary provider failure")).doNothing()
                .when(fixture.sendGrid).send(any(OutboundEmail.class));
        ParcelEvent event = new ParcelEvent("CP456", 7L, ParcelStatus.BOOKED, "Parcel booked", null,
                Instant.now(), "event-2");

        fixture.notifier.send(event);
        ArgumentCaptor<EmailDelivery> saved = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(fixture.deliveries).save(saved.capture());
        EmailDelivery failed = saved.getValue();
        assertEquals("FAILED", failed.status);
        assertEquals(1, failed.attempts);
        assertTrue(failed.errorDetails.contains("temporary provider failure"));

        when(fixture.deliveries.findByStatusAndAttemptsLessThanOrderByUpdatedAtAsc("FAILED", 8))
                .thenReturn(List.of(failed));
        fixture.notifier.retryFailed();

        assertEquals("SENT", failed.status);
        assertEquals(2, failed.attempts);
        verify(fixture.deliveries, org.mockito.Mockito.times(2)).save(any(EmailDelivery.class));
    }

    @Test
    void duplicateEventIdDoesNotSendAgain() {
        Fixture fixture = new Fixture(true);
        fixture.notifier.send(new ParcelEvent("CP789", 7L, ParcelStatus.BOOKED, "Parcel booked", null,
                Instant.now(), "event-3"));

        verify(fixture.deliveries, never()).saveAndFlush(any(EmailDelivery.class));
        verify(fixture.sendGrid, never()).send(any(OutboundEmail.class));
    }

    @Test
    void paymentOutcomesUseDistinctEmailTypes() {
        Fixture fixture = new Fixture(false);
        fixture.notifier.send(new PaymentNotificationEvent("payment-success", "CP100", 7L, "SUCCESS", "UPI",
                new BigDecimal("100.00"), "INR", Instant.now()));
        fixture.notifier.send(new PaymentNotificationEvent("payment-declined", "CP101", 7L, "DECLINED",
                "SIMULATED_DECLINE", new BigDecimal("100.00"), "INR", Instant.now()));

        ArgumentCaptor<EmailDelivery> saved = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(fixture.deliveries, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of("PAYMENT_SUCCESS", "PAYMENT_DECLINED"),
                saved.getAllValues().stream().map(delivery -> delivery.eventType).toList());
        assertTrue(saved.getAllValues().get(0).htmlBody.contains("100.00 INR"));
        assertTrue(saved.getAllValues().get(1).htmlBody.contains("No payment was collected"));
    }

    private static class Fixture {
        final EmailDeliveryRepository deliveries = org.mockito.Mockito.mock(EmailDeliveryRepository.class);
        final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        final SendGridEmailClient sendGrid = org.mockito.Mockito.mock(SendGridEmailClient.class);
        final CustomerEmailNotifier notifier;

        Fixture(boolean duplicate) {
            when(sendGrid.isConfigured()).thenReturn(true);
            when(deliveries.existsByEventId(any(String.class))).thenReturn(duplicate);
            when(users.findById(7L)).thenReturn(Optional.of(
                    new AppUser("customer@example.test", "ignored", "Customer", Role.CUSTOMER)));
            when(deliveries.saveAndFlush(any(EmailDelivery.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(deliveries.save(any(EmailDelivery.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            notifier = new CustomerEmailNotifier(true, "noreply@example.test", "http://localhost:8081",
                    users, deliveries, sendGrid, 8, "sg-secret");
        }
    }
}
