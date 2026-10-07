package edu.vitap.payments;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Table(name="payments", schema="courier_payments", uniqueConstraints=@UniqueConstraint(columnNames="trackingId"))
class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,unique=true) String trackingId;
    @Column(nullable=false) Long customerId;
    @Column(nullable=false,precision=10,scale=2) BigDecimal amount;
    @Column(nullable=false) String currency="INR";
    @Column(nullable=false) String method="";
    @Column(nullable=false) String status="PENDING";
    @Column(nullable=false,unique=true) String transactionReference;
    @Column(nullable=false) Instant createdAt;
    Instant paidAt;
    protected Payment() {}
    Payment(String trackingId,Long customerId,BigDecimal amount) {
        this.trackingId=trackingId;this.customerId=customerId;this.amount=amount;
        this.transactionReference="PAY-"+trackingId;this.createdAt=Instant.now();
    }
}
