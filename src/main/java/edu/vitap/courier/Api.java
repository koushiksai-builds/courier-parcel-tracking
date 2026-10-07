package edu.vitap.courier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.*;
import java.util.*;

record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank @Size(min=8) String password) {}
record UserView(Long id,String name,String email,Role role) { static UserView from(AppUser u){return new UserView(u.id,u.name,u.email,u.role);} }
record BookingRequest(@NotBlank String senderName,@NotBlank String senderAddress,@NotBlank String senderPhone,
                      @NotBlank String receiverName,@NotBlank String receiverAddress,@NotBlank String receiverPhone,
                      @NotBlank String description, Instant expectedDelivery) {}
record StatusRequest(@NotNull ParcelStatus status,@NotBlank String note) {}
record AssignmentRequest(@NotNull Long courierId) {}
record TrackingView(String trackingId,ParcelStatus status,Instant expectedDelivery,List<TrackingEntry> history) {}

@RestController @RequestMapping("/api/auth")
class AuthController {
    private final UserRepository users; private final PasswordEncoder encoder;
    AuthController(UserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    UserView register(@Valid @RequestBody RegisterRequest r){
        String email=r.email().toLowerCase(Locale.ROOT);
        if(users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        return UserView.from(users.save(new AppUser(email,encoder.encode(r.password()),r.name(),Role.CUSTOMER)));
    }
    @GetMapping("/login") @PreAuthorize("isAuthenticated()")
    UserView login(Authentication auth,UserRepository repo){return UserView.from(current(repo,auth));}
    static AppUser current(UserRepository repo,Authentication auth){return repo.findByEmail(auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
}

@RestController @RequestMapping("/api/parcels")
class ParcelController {
    private final ParcelRepository parcels; private final UserRepository users; private final ParcelEvents events;
    private final TrackingRepository tracking; private final NotificationRepository notifications;
    ParcelController(ParcelRepository p,UserRepository u,ParcelEvents e,TrackingRepository t,NotificationRepository n){parcels=p;users=u;events=e;tracking=t;notifications=n;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CUSTOMER')")
    Parcel book(@Valid @RequestBody BookingRequest r,Authentication auth){
        AppUser customer=AuthController.current(users,auth); Parcel p=new Parcel();
        p.trackingId="CP"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        p.customerId=customer.id;p.senderName=r.senderName();p.senderAddress=r.senderAddress();p.senderPhone=r.senderPhone();
        p.receiverName=r.receiverName();p.receiverAddress=r.receiverAddress();p.receiverPhone=r.receiverPhone();p.description=r.description();
        p.expectedDelivery=r.expectedDelivery();p.status=ParcelStatus.BOOKED;p.createdAt=Instant.now();p.updatedAt=p.createdAt;
        p=parcels.save(p);events.publish(p,"Parcel booked");return p;
    }
    @GetMapping @PreAuthorize("hasAnyRole('CUSTOMER','COURIER','ADMIN')")
    List<Parcel> mine(Authentication auth){AppUser u=AuthController.current(users,auth);return switch(u.role){case CUSTOMER->parcels.findByCustomerId(u.id);case COURIER->parcels.findByCourierId(u.id);case ADMIN->parcels.findAll();};}
    @GetMapping("/{trackingId}") @PreAuthorize("hasAnyRole('CUSTOMER','COURIER','ADMIN')")
    Parcel get(@PathVariable String trackingId,Authentication auth){Parcel p=find(trackingId);authorize(p,auth);return p;}
    @PutMapping("/{trackingId}/assignment") @PreAuthorize("hasRole('ADMIN')")
    Parcel assign(@PathVariable String trackingId,@Valid @RequestBody AssignmentRequest request){
        Parcel p=find(trackingId);AppUser courier=users.findById(request.courierId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Courier not found"));
        if(courier.role!=Role.COURIER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Assigned user must be courier staff");
        p.courierId=courier.id;p.updatedAt=Instant.now();return parcels.save(p);
    }
    @PutMapping("/{trackingId}/status") @PreAuthorize("hasAnyRole('COURIER','ADMIN')")
    Parcel update(@PathVariable String trackingId,@Valid @RequestBody StatusRequest request,Authentication auth){
        Parcel p=find(trackingId);AppUser actor=AuthController.current(users,auth);
        if(actor.role==Role.COURIER&&!Objects.equals(actor.id,p.courierId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(p.status==ParcelStatus.DELIVERED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivered parcels cannot be updated");
        p.status=request.status();p.updatedAt=Instant.now();p=parcels.save(p);events.publish(p,request.note());
        if(request.status()==ParcelStatus.DELIVERY_FAILED){
            p.status=ParcelStatus.EXCEPTION;p.updatedAt=Instant.now();p=parcels.save(p);events.publish(p,"Failed delivery attempt: "+request.note());
        }
        return p;
    }
    @GetMapping("/{trackingId}/history") @PreAuthorize("hasAnyRole('CUSTOMER','COURIER','ADMIN')")
    List<TrackingEntry> history(@PathVariable String trackingId,Authentication auth){Parcel p=find(trackingId);authorize(p,auth);return tracking.findByTrackingIdOrderByOccurredAtAsc(trackingId);}
    @GetMapping("/notifications/mine") @PreAuthorize("isAuthenticated()")
    List<ParcelNotification> notifications(Authentication auth){AppUser u=AuthController.current(users,auth);return notifications.findByCustomerIdOrderByCreatedAtDesc(u.id);}
    private Parcel find(String id){return parcels.findByTrackingId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tracking ID not found"));}
    private void authorize(Parcel p,Authentication auth){AppUser u=AuthController.current(users,auth);if(u.role==Role.ADMIN)return;if(u.role==Role.CUSTOMER&&Objects.equals(u.id,p.customerId))return;if(u.role==Role.COURIER&&Objects.equals(u.id,p.courierId))return;throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
}

@RestController @RequestMapping("/api/tracking")
class TrackingController {
    private final ParcelRepository parcels;private final TrackingRepository history;
    TrackingController(ParcelRepository p,TrackingRepository h){parcels=p;history=h;}
    @GetMapping("/{trackingId}") TrackingView track(@PathVariable String trackingId){
        Parcel p=parcels.findByTrackingId(trackingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tracking ID not found"));
        return new TrackingView(p.trackingId,p.status,p.expectedDelivery,history.findByTrackingIdOrderByOccurredAtAsc(trackingId));
    }
}

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
class AdminController {
    private final UserRepository users;private final ParcelRepository parcels;private final FailedEventRepository failures;private final RabbitTemplate rabbit;private final TrackingRepository tracking;
    AdminController(UserRepository u,ParcelRepository p,FailedEventRepository f,RabbitTemplate r,TrackingRepository t){users=u;parcels=p;failures=f;rabbit=r;tracking=t;}
    @GetMapping("/users") List<UserView> users(){return users.findAll().stream().map(UserView::from).toList();}
    @PostMapping("/couriers") @ResponseStatus(HttpStatus.CREATED)
    UserView createCourier(@Valid @RequestBody RegisterRequest r,PasswordEncoder encoder){
        String email=r.email().toLowerCase(Locale.ROOT);
        if(users.findByEmail(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        return UserView.from(users.save(new AppUser(email,encoder.encode(r.password()),r.name(),Role.COURIER)));
    }
    @GetMapping("/parcels") List<Parcel> parcels(){return parcels.findAll();}
    @GetMapping("/delayed") List<Parcel> delayed(){return parcels.findAll().stream().filter(p->p.expectedDelivery!=null&&p.status!=ParcelStatus.DELIVERED&&p.expectedDelivery.isBefore(Instant.now().minus(Duration.ofHours(1)))).toList();}
    @GetMapping("/exceptions") List<Parcel> exceptions(){return parcels.findByStatus(ParcelStatus.EXCEPTION);}
    @GetMapping("/activities") List<TrackingEntry> activities(){return tracking.findTop200ByOrderByOccurredAtDesc();}
    @GetMapping("/events/failed") List<FailedEvent> failedEvents(){return failures.findAll();}
    @PostMapping("/events/failed/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    void retryFailedEvent(@PathVariable Long id){
        FailedEvent f=failures.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Failed event not found"));
        rabbit.send(EventConfiguration.EXCHANGE,"parcel.retry",org.springframework.amqp.core.MessageBuilder.withBody(f.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).setContentType(org.springframework.amqp.core.MessageProperties.CONTENT_TYPE_JSON).build());
        f.attempts++;failures.save(f);
    }
}

@Component
class ParcelMonitoring {
    private final ParcelRepository parcels;private final ParcelEvents events;
    ParcelMonitoring(ParcelRepository p,ParcelEvents e){parcels=p;events=e;}
    @Scheduled(fixedDelayString="${DELAY_SCAN_MS:60000}") @Transactional
    void scanForDelays(){
        Instant cutoff=Instant.now().minus(Duration.ofHours(1));
        for(Parcel p:parcels.findAll()) if(p.expectedDelivery!=null&&p.expectedDelivery.isBefore(cutoff)&&p.status!=ParcelStatus.DELIVERED&&p.status!=ParcelStatus.EXCEPTION){
            p.status=ParcelStatus.EXCEPTION;p.updatedAt=Instant.now();parcels.save(p);events.publish(p,"Delivery delay detected; parcel requires attention");
        }
        Instant inactivity=Instant.now().minus(Duration.ofHours(24));
        for(Parcel p:parcels.findAll()) if(p.updatedAt!=null&&p.updatedAt.isBefore(inactivity)&&p.status!=ParcelStatus.DELIVERED&&p.status!=ParcelStatus.EXCEPTION){
            p.status=ParcelStatus.EXCEPTION;p.updatedAt=Instant.now();parcels.save(p);events.publish(p,"Parcel exception detected after prolonged inactivity");
        }
    }
}
