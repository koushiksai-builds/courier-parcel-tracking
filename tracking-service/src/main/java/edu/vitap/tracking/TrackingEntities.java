package edu.vitap.tracking;
import edu.vitap.common.ParcelStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="tracking_parcels",catalog="courier_tracking")
class TrackedParcel {
 @Id String trackingId; @Column(nullable=false) Long customerId; @Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status; Instant expectedDelivery,updatedAt;
 protected TrackedParcel(){}
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="tracking_history",catalog="courier_tracking")
class TrackingEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false) String trackingId; @Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status; @Column(nullable=false) String note; @Column(nullable=false) Instant occurredAt;
 protected TrackingEntry(){}
 TrackingEntry(String id,ParcelStatus s,String n,Instant at){trackingId=id;status=s;note=n;occurredAt=at;}
}
