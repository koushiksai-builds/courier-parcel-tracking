package edu.vitap.common;
import java.time.Instant;
public record ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant expectedDelivery, Instant occurredAt) {}
