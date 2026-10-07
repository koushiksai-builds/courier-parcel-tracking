package edu.vitap.parcels;

import edu.vitap.common.ParcelEvent;
import edu.vitap.common.ParcelEventConfiguration;
import edu.vitap.common.ParcelStatus;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Configuration
class ParcelStatusQueueConfiguration {
    @Bean
    Queue parcelStatusQueue() {
        return QueueBuilder.durable("parcel.status-updates")
                .withArgument("x-dead-letter-exchange", ParcelEventConfiguration.FAILED_EXCHANGE)
                .build();
    }

    @Bean
    Declarables parcelStatusBindings(Queue parcelStatusQueue,
                                     @Qualifier("parcelEventsExchange") TopicExchange exchange) {
        List<String> keys = List.of("booked", "picked_up", "in_transit", "out_for_delivery",
                "delivered", "delivery_failed", "exception");
        return new Declarables(keys.stream()
                .map(key -> BindingBuilder.bind(parcelStatusQueue).to(exchange).with("parcel." + key))
                .toList());
    }
}

@Component
class ParcelStatusEventConsumer {
    private final ParcelRepository parcels;

    ParcelStatusEventConsumer(ParcelRepository parcels) {
        this.parcels = parcels;
    }

    @RabbitListener(queues = "parcel.status-updates")
    @Transactional
    public void update(ParcelEvent event) {
        parcels.findByTrackingId(event.trackingId()).ifPresent(parcel -> {
            parcel.status = event.status();
            parcel.expectedDelivery = event.expectedDelivery();
            parcels.save(parcel);
        });
    }
}
