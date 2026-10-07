package edu.vitap.delivery;
import edu.vitap.common.ParcelStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="delivery_records",catalog="courier_delivery")
class DeliveryRecord {
 @Id String trackingId;@Column(nullable=false) Long customerId;Long courierId;@Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status;Instant expectedDelivery,lastActivity;
 protected DeliveryRecord(){}
}
