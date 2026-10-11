package edu.vitap.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentNotificationEvent(
        String eventId,
        String trackingId,
        Long customerId,
        String status,
        String method,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
    public PaymentNotificationEvent {
        if (eventId == null || eventId.isBlank()) eventId = UUID.randomUUID().toString();
    }
}