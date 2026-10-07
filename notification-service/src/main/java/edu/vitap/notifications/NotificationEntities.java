package edu.vitap.notifications;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="notifications",schema="courier_notifications")
class ParcelNotification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;@Column(nullable=false) Long customerId;@Column(nullable=false) String trackingId,message;@Column(nullable=false) Instant createdAt;@Column(nullable=false,columnDefinition="boolean not null default false") boolean archived=false;
 protected ParcelNotification(){}
 ParcelNotification(Long c,String t,String m){customerId=c;trackingId=t;message=m;createdAt=Instant.now();}
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="failed_events",schema="courier_notifications")
class FailedEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;@Lob @Column(nullable=false) String payload;@Column(nullable=false) Instant failedAt;@Column(nullable=false) int attempts;
 protected FailedEvent(){}
 FailedEvent(String p){payload=p;failedAt=Instant.now();attempts=1;}
}
