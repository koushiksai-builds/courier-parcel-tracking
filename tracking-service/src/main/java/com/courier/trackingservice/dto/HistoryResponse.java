package com.courier.trackingservice.dto;

import com.courier.trackingservice.model.TrackingHistory;
import com.courier.trackingservice.model.TrackingStatus;

import java.time.LocalDateTime;

public class HistoryResponse {

    private Long id;
    private String trackingId;
    private TrackingStatus status;
    private String location;
    private String remarks;
    private LocalDateTime timestamp;

    public static HistoryResponse from(TrackingHistory h) {
        HistoryResponse r = new HistoryResponse();

        r.id = h.getId();
        r.trackingId = h.getTrackingId();
        r.status = h.getStatus();
        r.location = h.getLocation();
        r.remarks = h.getRemarks();
        r.timestamp = h.getTimestamp();

        return r;
    }

    public Long getId() { return id; }
    public String getTrackingId() { return trackingId; }
    public TrackingStatus getStatus() { return status; }
    public String getLocation() { return location; }
    public String getRemarks() { return remarks; }
    public LocalDateTime getTimestamp() { return timestamp; }
}