package edu.vitap.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.vitap.common.AppUser;
import edu.vitap.common.PaymentNotificationEvent;
import edu.vitap.common.Role;
import edu.vitap.common.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PaymentApiTest {
    @Test
    void simulatedDeclinePersistsAndPublishesEvenIfRabbitIsUnavailable() {
        PaymentRepository payments = mock(PaymentRepository.class);
        UserRepository users = mock(UserRepository.class);
        RabbitTemplate events = mock(RabbitTemplate.class);
        AppUser customer = mock(AppUser.class);
        Payment payment = new Payment("CP123", 7L, new BigDecimal("100.00"));
        when(customer.getId()).thenReturn(7L);
        when(customer.getRole()).thenReturn(Role.CUSTOMER);
        when(users.findByEmail("customer@example.test")).thenReturn(Optional.of(customer));
        when(payments.findByTrackingId("CP123")).thenReturn(Optional.of(payment));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new AmqpException("broker unavailable")).when(events)
                .convertAndSend(eq("parcel.events"), eq("payment.declined"), any(PaymentNotificationEvent.class));
        var authentication = new UsernamePasswordAuthenticationToken("customer@example.test", "", List.of());

        Payment result = new PaymentApi(payments, users, events).pay("CP123", new PayRequest("DECLINE"), authentication);

        assertEquals("DECLINED", result.status);
        assertEquals("SIMULATED_DECLINE", result.method);
        verify(events).convertAndSend(eq("parcel.events"), eq("payment.declined"), any(PaymentNotificationEvent.class));
    }
}