package edu.vitap.parcels;
import edu.vitap.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record BookingRequest(@NotBlank String senderName,@NotBlank String senderAddress,@NotBlank String senderPhone,@NotBlank String receiverName,@NotBlank String receiverAddress,@NotBlank String receiverPhone,@NotBlank String description,Instant expectedDelivery){}
@RestController @RequestMapping("/api/parcels")
class ParcelApi {
 private final ParcelRepository parcels;private final UserRepository users;private final RabbitTemplate events;
 ParcelApi(ParcelRepository p,UserRepository u,RabbitTemplate e){parcels=p;users=u;events=e;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CUSTOMER')")
 Parcel book(@Valid @RequestBody BookingRequest r,Authentication auth){AppUser user=CurrentUser.get(users,auth);Parcel p=new Parcel();p.trackingId="CP"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);p.customerId=user.getId();p.senderName=r.senderName();p.senderAddress=r.senderAddress();p.senderPhone=r.senderPhone();p.receiverName=r.receiverName();p.receiverAddress=r.receiverAddress();p.receiverPhone=r.receiverPhone();p.description=r.description();p.expectedDelivery=r.expectedDelivery();p.status=ParcelStatus.BOOKED;p.createdAt=Instant.now();p=parcels.save(p);events.convertAndSend(ParcelEventConfiguration.EXCHANGE,"parcel.created",new ParcelEvent(p.trackingId,p.customerId,p.status,"Parcel booked",p.expectedDelivery,Instant.now()));return p;}
 @GetMapping @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')") List<Parcel> mine(Authentication auth){AppUser u=CurrentUser.get(users,auth);return u.getRole()==Role.ADMIN?parcels.findAll():parcels.findByCustomerId(u.getId());}
 @GetMapping("/{trackingId}") @PreAuthorize("isAuthenticated()") Parcel get(@PathVariable String trackingId,Authentication auth){Parcel p=find(trackingId);AppUser u=CurrentUser.get(users,auth);if(u.getRole()!=Role.ADMIN&&!Objects.equals(u.getId(),p.customerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p;}
 @GetMapping("/admin/all") @PreAuthorize("hasRole('ADMIN')") List<Parcel> all(){return parcels.findAll();}
 private Parcel find(String id){return parcels.findByTrackingId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tracking ID not found"));}
}
