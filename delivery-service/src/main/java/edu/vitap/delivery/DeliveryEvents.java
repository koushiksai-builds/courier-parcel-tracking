package edu.vitap.delivery;
import edu.vitap.common.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Configuration class DeliveryQueueConfiguration {
 @Bean Queue parcelCreatedQueue(){return QueueBuilder.durable("delivery.parcel-created").withArgument("x-dead-letter-exchange",ParcelEventConfiguration.FAILED_EXCHANGE).build();}
 @Bean Binding deliveryCreatedBinding(Queue parcelCreatedQueue,@Qualifier("parcelEventsExchange") TopicExchange exchange){return BindingBuilder.bind(parcelCreatedQueue).to(exchange).with("parcel.created");}
}
@Component class DeliveryEventConsumer {
 private final DeliveryRepository deliveries;DeliveryEventConsumer(DeliveryRepository d){deliveries=d;}
 @RabbitListener(queues="delivery.parcel-created") @Transactional
 public void create(ParcelEvent event){DeliveryRecord d=new DeliveryRecord();d.trackingId=event.trackingId();d.customerId=event.customerId();d.status=ParcelStatus.BOOKED;d.expectedDelivery=event.expectedDelivery();d.lastActivity=event.occurredAt()==null?Instant.now():event.occurredAt();deliveries.save(d);}
}
@Component class DeliveryEventPublisher {
 private final RabbitTemplate rabbit;DeliveryEventPublisher(RabbitTemplate r){rabbit=r;}
 void publish(DeliveryRecord d,String note){rabbit.convertAndSend(ParcelEventConfiguration.EXCHANGE,"parcel."+d.status.name().toLowerCase(),new ParcelEvent(d.trackingId,d.customerId,d.status,note,d.expectedDelivery,Instant.now()));}
}
