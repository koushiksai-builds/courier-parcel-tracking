package edu.vitap.notifications;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="notifications",catalog="courier_notifications")
class ParcelNotification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;@Column(nullable=false) Long customerId;@Column(nullable=false) String trackingId,message;@Column(unique=true) String eventId;@Column(nullable=false) Instant createdAt;@Column(nullable=false,columnDefinition="boolean not null default false") boolean archived=false;
 protected ParcelNotification(){}
 ParcelNotification(Long c,String t,String m,String e){customerId=c;trackingId=t;message=m;eventId=e;createdAt=Instant.now();}
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="failed_events",catalog="courier_notifications")
class FailedEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;@Lob @Column(nullable=false) String payload;@Column(nullable=false) Instant failedAt;@Column(nullable=false) int attempts;
 protected FailedEvent(){}
 FailedEvent(String p){payload=p;failedAt=Instant.now();attempts=1;}
}
@Entity @Table(name="email_delivery_history",catalog="courier_notifications")
class EmailDelivery {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false,unique=true) String eventId;
 @Column(nullable=false) String eventType;
 @Column(nullable=false) Long customerId;
 @Column(nullable=false) String recipient;
 @Column(nullable=false) String trackingId;
 @Column(nullable=false) String subject;
 @Column(nullable=false, columnDefinition="LONGTEXT") String htmlBody;
 @Column(nullable=false) String status;
 @Column(nullable=false) int attempts;
 @Lob String errorDetails;
 @Column(nullable=false) Instant createdAt;
 @Column(nullable=false) Instant updatedAt;
 Instant sentAt;
 protected EmailDelivery(){}
 EmailDelivery(String eventId,String eventType,Long customerId,String recipient,String trackingId,String subject,String htmlBody){this.eventId=eventId;this.eventType=eventType;this.customerId=customerId;this.recipient=recipient;this.trackingId=trackingId;this.subject=subject;this.htmlBody=htmlBody;this.status="PENDING";this.attempts=0;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
}
