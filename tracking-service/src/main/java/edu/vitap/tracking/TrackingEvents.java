package edu.vitap.tracking;
import edu.vitap.common.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Configuration class TrackingQueueConfiguration {
 @Bean Queue trackingEventQueue(){return QueueBuilder.durable("tracking.events").withArgument("x-dead-letter-exchange",ParcelEventConfiguration.FAILED_EXCHANGE).build();}
 @Bean Binding trackingEventBinding(Queue trackingEventQueue,@Qualifier("parcelEventsExchange") TopicExchange parcelEventsExchange){return BindingBuilder.bind(trackingEventQueue).to(parcelEventsExchange).with("parcel.#");}
}
@Component class TrackingEventConsumer {
 private final TrackedParcelRepository parcels;private final TrackingEntryRepository history;
 TrackingEventConsumer(TrackedParcelRepository p,TrackingEntryRepository h){parcels=p;history=h;}
 @RabbitListener(queues="tracking.events") @Transactional
 public void consume(ParcelEvent event){TrackedParcel p=parcels.findById(event.trackingId()).orElseGet(TrackedParcel::new);p.trackingId=event.trackingId();p.customerId=event.customerId();p.status=event.status();p.expectedDelivery=event.expectedDelivery();p.updatedAt=event.occurredAt();parcels.save(p);history.save(new TrackingEntry(event.trackingId(),event.status(),event.note(),event.occurredAt()==null?Instant.now():event.occurredAt()));}
}
