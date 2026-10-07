package com.courier.trackingservice.model;

public enum TrackingStatus {
    BOOKED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    DELAYED,
    EXCEPTION
}