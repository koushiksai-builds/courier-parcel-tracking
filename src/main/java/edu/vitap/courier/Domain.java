package edu.vitap.courier;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import java.time.Instant;

enum Role { CUSTOMER, COURIER, ADMIN }
enum ParcelStatus { BOOKED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, EXCEPTION }

@Entity @Table(name="app_users")
class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) String email;
    @Column(nullable=false) String password;
    @Column(nullable=false) String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Role role;
    protected AppUser() {}
    AppUser(String email, String password, String name, Role role) { this.email=email; this.password=password; this.name=name; this.role=role; }
}

@Entity @JsonAutoDetect(fieldVisibility=Visibility.ANY) @Table(name="parcels", indexes=@Index(columnList="trackingId", unique=true))
class Parcel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) String trackingId;
    @Column(nullable=false) Long customerId;
    @Column(nullable=false) String senderName, senderAddress, senderPhone;
    @Column(nullable=false) String receiverName, receiverAddress, receiverPhone;
    @Column(nullable=false) String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status;
    Instant expectedDelivery, createdAt, updatedAt;
    Long courierId;
    protected Parcel() {}
}

@Entity @JsonAutoDetect(fieldVisibility=Visibility.ANY) @Table(name="tracking_history")
class TrackingEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) String trackingId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status;
    @Column(nullable=false) String note;
    @Column(nullable=false) Instant occurredAt;
    protected TrackingEntry() {}
    TrackingEntry(String trackingId, ParcelStatus status, String note) { this.trackingId=trackingId; this.status=status; this.note=note; this.occurredAt=Instant.now(); }
}

@Entity @JsonAutoDetect(fieldVisibility=Visibility.ANY) @Table(name="notifications")
class ParcelNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) Long customerId;
    @Column(nullable=false) String trackingId, message;
    @Column(nullable=false) Instant createdAt;
    protected ParcelNotification() {}
    ParcelNotification(Long customerId, String trackingId, String message) { this.customerId=customerId; this.trackingId=trackingId; this.message=message; this.createdAt=Instant.now(); }
}

@Entity @JsonAutoDetect(fieldVisibility=Visibility.ANY) @Table(name="failed_events")
class FailedEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Lob @Column(nullable=false) String payload;
    @Column(nullable=false) Instant failedAt;
    @Column(nullable=false) int attempts;
    protected FailedEvent() {}
    FailedEvent(String payload){this.payload=payload;this.failedAt=Instant.now();this.attempts=0;}
}
