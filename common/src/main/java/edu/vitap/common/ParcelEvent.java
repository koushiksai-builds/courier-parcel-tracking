package edu.vitap.common;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

public record ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant expectedDelivery, Instant occurredAt, String eventId, BigDecimal shippingCost) {
	public ParcelEvent {
		if (eventId == null || eventId.isBlank()) eventId = UUID.randomUUID().toString();
	}

	public ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant expectedDelivery, Instant occurredAt) {
		this(trackingId, customerId, status, note, expectedDelivery, occurredAt, UUID.randomUUID().toString(), null);
	}

	public ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant expectedDelivery, Instant occurredAt, String eventId) {
		this(trackingId, customerId, status, note, expectedDelivery, occurredAt, eventId, null);
	}

	public ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant expectedDelivery, Instant occurredAt, BigDecimal shippingCost) {
		this(trackingId, customerId, status, note, expectedDelivery, occurredAt, UUID.randomUUID().toString(), shippingCost);
	}
}
