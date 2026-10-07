package edu.vitap.notifications;
import edu.vitap.common.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Configuration class NotificationQueueConfiguration {
 @Bean Queue notificationEventQueue(){return QueueBuilder.durable("notification.events").withArgument("x-dead-letter-exchange",ParcelEventConfiguration.FAILED_EXCHANGE).build();}
 @Bean Binding notificationEventBinding(@Qualifier("notificationEventQueue") Queue notificationEventQueue,@Qualifier("parcelEventsExchange") TopicExchange exchange){return BindingBuilder.bind(notificationEventQueue).to(exchange).with("parcel.#");}
 @Bean Queue failedEventQueue(){return QueueBuilder.durable("parcel.failed.events").build();}
 @Bean Binding failedEventBinding(@Qualifier("failedEventQueue") Queue failedEventQueue,@Qualifier("failedEventsExchange") TopicExchange exchange){return BindingBuilder.bind(failedEventQueue).to(exchange).with("#");}
}
@Component class NotificationEventConsumer {
 private final NotificationRepository notifications;private final FailedEventRepository failures;private final CustomerEmailNotifier emailNotifier;
 NotificationEventConsumer(NotificationRepository n,FailedEventRepository f,CustomerEmailNotifier emailNotifier){notifications=n;failures=f;this.emailNotifier=emailNotifier;}
 @RabbitListener(queues="notification.events") @Transactional public void notify(ParcelEvent e){notifications.save(new ParcelNotification(e.customerId(),e.trackingId(),e.note()+" ("+e.status()+")"));emailNotifier.send(e);}
 @RabbitListener(queues="parcel.failed.events") public void capture(org.springframework.amqp.core.Message m){failures.save(new FailedEvent(new String(m.getBody(),StandardCharsets.UTF_8)));}
}
@RestController @RequestMapping("/api/notifications")
class NotificationApi {
 private final NotificationRepository notifications;private final UserRepository users;
 NotificationApi(NotificationRepository n,UserRepository u){notifications=n;users=u;}
 @GetMapping("/mine") @PreAuthorize("hasRole('CUSTOMER')") List<ParcelNotification> mine(org.springframework.security.core.Authentication a){AppUser u=CurrentUser.get(users,a);return notifications.findByCustomerIdAndArchivedOrderByCreatedAtDesc(u.getId(),false);}
 @GetMapping("/history") @PreAuthorize("hasRole('CUSTOMER')") List<ParcelNotification> history(org.springframework.security.core.Authentication a){AppUser u=CurrentUser.get(users,a);return notifications.findByCustomerIdAndArchivedOrderByCreatedAtDesc(u.getId(),true);}
 @PostMapping("/mine/clear") @PreAuthorize("hasRole('CUSTOMER')") @Transactional ClearNotificationsResult clear(org.springframework.security.core.Authentication a){AppUser u=CurrentUser.get(users,a);List<ParcelNotification> current=notifications.findByCustomerIdAndArchivedOrderByCreatedAtDesc(u.getId(),false);current.forEach(n->n.archived=true);notifications.saveAll(current);return new ClearNotificationsResult(current.size());}
}
record ClearNotificationsResult(int movedToHistory){}
@RestController @RequestMapping("/api/admin/events/failed") @PreAuthorize("hasRole('ADMIN')")
class FailedEventAdminApi {
 private final FailedEventRepository failures;private final RabbitTemplate rabbit;
 FailedEventAdminApi(FailedEventRepository f,RabbitTemplate r){failures=f;rabbit=r;}
 @GetMapping List<FailedEvent> list(){return failures.findAll();}
 @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED) void retry(@PathVariable Long id){FailedEvent f=failures.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));rabbit.send(ParcelEventConfiguration.EXCHANGE,"parcel.retry",MessageBuilder.withBody(f.payload.getBytes(StandardCharsets.UTF_8)).setContentType(MessageProperties.CONTENT_TYPE_JSON).build());f.attempts++;failures.save(f);}
}
