package com.courier.trackingservice.dto;

import com.courier.trackingservice.model.TrackingRecord;
import com.courier.trackingservice.model.TrackingStatus;

import java.time.LocalDateTime;

public class TrackingResponse {

    private String trackingId;
    private Long customerId;
    private TrackingStatus status;
    private String location;
    private LocalDateTime lastUpdatedAt;
    private LocalDateTime expectedDeliveryDate;
    private boolean delayed;

    public static TrackingResponse from(TrackingRecord record) {
        TrackingResponse r = new TrackingResponse();

        r.trackingId = record.getTrackingId();
        r.customerId = record.getCustomerId();
        r.status = record.getStatus();
        r.location = record.getLocation();
        r.lastUpdatedAt = record.getLastUpdatedAt();
        r.expectedDeliveryDate = record.getExpectedDeliveryDate();

        boolean overdue = record.getStatus() != TrackingStatus.DELIVERED
                && record.getExpectedDeliveryDate() != null
                && LocalDateTime.now().isAfter(record.getExpectedDeliveryDate());

        r.delayed = record.getStatus() == TrackingStatus.DELAYED || overdue;

        return r;
    }

    public String getTrackingId() { return trackingId; }
    public Long getCustomerId() { return customerId; }
    public TrackingStatus getStatus() { return status; }
    public String getLocation() { return location; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public LocalDateTime getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public boolean isDelayed() { return delayed; }
}