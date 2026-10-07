package edu.vitap.courier;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

record ParcelEvent(String trackingId, Long customerId, ParcelStatus status, String note, Instant occurredAt) {}

@Configuration
class EventConfiguration {
    static final String EXCHANGE="parcel.events", QUEUE="tracking.events", NOTIFICATION_QUEUE="notification.events";
    static final String FAILED_EXCHANGE="parcel.failed", FAILED_QUEUE="parcel.failed.events";
    @Bean TopicExchange parcelExchange(){ return new TopicExchange(EXCHANGE); }
    @Bean Queue trackingQueue(){ return QueueBuilder.durable(QUEUE).withArgument("x-dead-letter-exchange",FAILED_EXCHANGE).build(); }
    @Bean Queue notificationQueue(){ return QueueBuilder.durable(NOTIFICATION_QUEUE).withArgument("x-dead-letter-exchange",FAILED_EXCHANGE).build(); }
    @Bean Binding trackingBinding(){ return BindingBuilder.bind(trackingQueue()).to(parcelExchange()).with("parcel.#"); }
    @Bean Binding notificationBinding(){ return BindingBuilder.bind(notificationQueue()).to(parcelExchange()).with("parcel.#"); }
    @Bean TopicExchange failedExchange(){return new TopicExchange(FAILED_EXCHANGE);}
    @Bean Queue failedQueue(){return QueueBuilder.durable(FAILED_QUEUE).build();}
    @Bean Binding failedBinding(){return BindingBuilder.bind(failedQueue()).to(failedExchange()).with("#");}
    @Bean Jackson2JsonMessageConverter eventJsonConverter(){return new Jackson2JsonMessageConverter();}
}

@Component
class ParcelEvents {
    private final RabbitTemplate rabbit;
    ParcelEvents(RabbitTemplate rabbit){ this.rabbit=rabbit; }
    void publish(Parcel p, String note){ rabbit.convertAndSend(EventConfiguration.EXCHANGE,"parcel."+p.status.name().toLowerCase(),new ParcelEvent(p.trackingId,p.customerId,p.status,note,Instant.now())); }
}

@Component
class EventConsumers {
    private final TrackingRepository tracking;
    private final NotificationRepository notifications;
    EventConsumers(TrackingRepository tracking, NotificationRepository notifications){this.tracking=tracking;this.notifications=notifications;}
    @RabbitListener(queues=EventConfiguration.QUEUE)
    @Transactional
    public void track(ParcelEvent e){ tracking.save(new TrackingEntry(e.trackingId(),e.status(),e.note())); }
    @RabbitListener(queues=EventConfiguration.NOTIFICATION_QUEUE)
    @Transactional
    public void notifyCustomer(ParcelEvent e){ notifications.save(new ParcelNotification(e.customerId(),e.trackingId(),e.note()+" ("+e.status()+")")); }
}

@Component
class FailedEventConsumer {
    private final FailedEventRepository failures;
    FailedEventConsumer(FailedEventRepository failures){this.failures=failures;}
    @RabbitListener(queues=EventConfiguration.FAILED_QUEUE)
    public void record(Message message){failures.save(new FailedEvent(new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8)));}
}
