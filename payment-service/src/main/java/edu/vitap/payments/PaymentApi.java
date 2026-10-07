package edu.vitap.payments;

import edu.vitap.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
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
        payments.findByTrackingId(event.trackingId()).orElseGet(()->payments.save(new Payment(event.trackingId(),event.customerId(),amount)));
    }
}

@RestController
@RequestMapping("/api/payments")
class PaymentApi {
    private final PaymentRepository payments;
    private final UserRepository users;
    PaymentApi(PaymentRepository payments,UserRepository users){this.payments=payments;this.users=users;}

    @GetMapping("/{trackingId}") @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    Payment view(@PathVariable String trackingId,Authentication auth){return owned(trackingId,auth);}

    @PostMapping("/{trackingId}/pay") @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    Payment pay(@PathVariable String trackingId,@Valid @RequestBody PayRequest request,Authentication auth){
        Payment payment=owned(trackingId,auth);
        String method=request.method().trim().toUpperCase(Locale.ROOT);
        if(!List.of("UPI","CARD","CASH_ON_DELIVERY","WALLET").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose UPI, card, wallet, or cash on delivery");
        if("PENDING".equals(payment.status)){
            payment.method=method;
            payment.status="CASH_ON_DELIVERY".equals(method)?"PAY_ON_DELIVERY":"SUCCESS";
            payment.paidAt="SUCCESS".equals(payment.status)?Instant.now():null;
            payments.save(payment);
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
}
