package edu.vitap.parcels;
import edu.vitap.common.ParcelStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="parcels",catalog="courier_parcels",indexes=@Index(columnList="trackingId",unique=true))
class Parcel {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false,unique=true) String trackingId;
 @Column(nullable=false) Long customerId;
 @Column(nullable=false) String senderName,senderAddress,senderPhone,receiverName,receiverAddress,receiverPhone,description;
 @Enumerated(EnumType.STRING) @Column(nullable=false) ParcelStatus status;
 Instant expectedDelivery,createdAt;
 protected Parcel(){}
}
