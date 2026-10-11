package edu.vitap.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import edu.vitap.common.ParcelEvent;
import edu.vitap.common.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParcelBookingPaymentConsumerTest {
    @Test void bookingEventAmountSetsPaymentAmount() {
        PaymentRepository repository=mock(PaymentRepository.class);
        when(repository.findByTrackingId("CP123")).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class))).thenAnswer(invocation->invocation.getArgument(0));
        ParcelBookingPaymentConsumer consumer=new ParcelBookingPaymentConsumer(repository,new BigDecimal("100.00"));
        ParcelEvent event=new ParcelEvent("CP123",7L,ParcelStatus.BOOKED,"Parcel booked",null,Instant.now(),new BigDecimal("160.00"));
        consumer.onBooking(event);
        var captor=org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        assertEquals(new BigDecimal("160.00"),captor.getValue().amount);
    }
}
