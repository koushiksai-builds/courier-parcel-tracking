package edu.vitap.payments;

import edu.vitap.common.ParcelEventConfiguration;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PaymentQueueConfiguration {
    @Bean Queue paymentBookingQueue(){return QueueBuilder.durable("payment.booking.events").build();}
    @Bean Binding paymentBookingBinding(@Qualifier("paymentBookingQueue") Queue queue,@Qualifier("parcelEventsExchange") TopicExchange exchange){return BindingBuilder.bind(queue).to(exchange).with("parcel.created");}
}
