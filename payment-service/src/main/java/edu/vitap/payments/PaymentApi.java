package edu.vitap.payments;

import edu.vitap.common.*;
import edu.vitap.common.PaymentNotificationEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record PayRequest(@NotBlank String method) {}

@Component
class ParcelBookingPaymentConsumer {
    private final PaymentRepository payments;
    private final BigDecimal amount;
    ParcelBookingPaymentConsumer(PaymentRepository payments,@Value("${payments.demo-amount:100.00}") BigDecimal amount){this.payments=payments;this.amount=amount;}
    @RabbitListener(queues="payment.booking.events")
    @Transactional
    public void onBooking(ParcelEvent event) {
        BigDecimal bookingAmount = event.shippingCost() == null ? amount : event.shippingCost();
        payments.findByTrackingId(event.trackingId()).orElseGet(()->payments.save(new Payment(event.trackingId(),event.customerId(),bookingAmount)));
    }
}

@RestController
@RequestMapping("/api/payments")
class PaymentApi {
    private static final Logger log = LoggerFactory.getLogger(PaymentApi.class);
    private final PaymentRepository payments;
    private final UserRepository users;
    private final RabbitTemplate events;
    PaymentApi(PaymentRepository payments,UserRepository users,RabbitTemplate events){this.payments=payments;this.users=users;this.events=events;}

    @GetMapping("/{trackingId}") @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    Payment view(@PathVariable String trackingId,Authentication auth){return owned(trackingId,auth);}

    @PostMapping("/{trackingId}/pay") @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    Payment pay(@PathVariable String trackingId,@Valid @RequestBody PayRequest request,Authentication auth){
        Payment payment=owned(trackingId,auth);
        String method=request.method().trim().toUpperCase(Locale.ROOT);
        if(!List.of("UPI","CARD","CASH_ON_DELIVERY","WALLET","DECLINE").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose UPI, card, wallet, cash on delivery, or DECLINE for a simulated decline");
        if("PENDING".equals(payment.status)){
            boolean declined="DECLINE".equals(method);
            payment.method=declined?"SIMULATED_DECLINE":method;
            payment.status=declined?"DECLINED":"CASH_ON_DELIVERY".equals(method)?"PAY_ON_DELIVERY":"SUCCESS";
            payment.paidAt="SUCCESS".equals(payment.status)?Instant.now():null;
            payment=payments.save(payment);
            if("SUCCESS".equals(payment.status)||"DECLINED".equals(payment.status))publishPaymentNotification(payment);
        }
        return payment;
    }

    @GetMapping("/admin/all") @PreAuthorize("hasRole('ADMIN')")
    List<Payment> all(){return payments.findAll();}

    private Payment owned(String trackingId,Authentication auth){
        AppUser user=CurrentUser.get(users,auth);
        Payment payment=payments.findByTrackingId(trackingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment order not ready yet; retry in a moment"));
        if(user.getRole()!=Role.ADMIN&&!user.getId().equals(payment.customerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This payment belongs to another customer");
        return payment;
    }

    private void publishPaymentNotification(Payment payment){
        PaymentNotificationEvent event=new PaymentNotificationEvent(null,payment.trackingId,payment.customerId,payment.status,payment.method,payment.amount,payment.currency,Instant.now());
        try {
            String routingKey="payment."+payment.status.toLowerCase(Locale.ROOT);
            events.convertAndSend(ParcelEventConfiguration.EXCHANGE,routingKey,event);
        } catch(AmqpException ex) {
            log.warn("Could not publish payment notification event {} for tracking ID {}",event.eventId(),payment.trackingId,ex);
        }
    }
}
