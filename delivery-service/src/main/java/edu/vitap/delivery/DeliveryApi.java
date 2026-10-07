package edu.vitap.delivery;
import edu.vitap.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record AssignmentRequest(@NotNull Long courierId){}
record StatusRequest(@NotNull ParcelStatus status,@NotBlank String note){}
@RestController @RequestMapping("/api/delivery")
class DeliveryApi {
 private final DeliveryRepository deliveries;private final UserRepository users;private final DeliveryEventPublisher events;
 DeliveryApi(DeliveryRepository d,UserRepository u,DeliveryEventPublisher e){deliveries=d;users=u;events=e;}
 @GetMapping("/mine") @PreAuthorize("hasRole('COURIER')") List<DeliveryRecord> mine(Authentication auth){return deliveries.findByCourierId(CurrentUser.get(users,auth).getId());}
 @PutMapping("/{trackingId}/assignment") @PreAuthorize("hasRole('ADMIN')") DeliveryRecord assign(@PathVariable String trackingId,@Valid @RequestBody AssignmentRequest request){DeliveryRecord d=find(trackingId);AppUser courier=users.findById(request.courierId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Courier not found"));if(courier.getRole()!=Role.COURIER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Assigned user must be courier staff");d.courierId=courier.getId();d.lastActivity=Instant.now();d=deliveries.save(d);events.publish(d,"Courier assigned");return d;}
 @PutMapping("/{trackingId}/status") @PreAuthorize("hasAnyRole('COURIER','ADMIN')") DeliveryRecord update(@PathVariable String trackingId,@Valid @RequestBody StatusRequest r,Authentication auth){DeliveryRecord d=find(trackingId);AppUser actor=CurrentUser.get(users,auth);if(actor.getRole()==Role.COURIER&&!Objects.equals(actor.getId(),d.courierId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This parcel is not assigned to your courier account");if(d.status==ParcelStatus.DELIVERED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivered parcels cannot be updated");d.status=r.status();d.lastActivity=Instant.now();d=deliveries.save(d);events.publish(d,r.note());if(r.status()==ParcelStatus.DELIVERY_FAILED){d.status=ParcelStatus.EXCEPTION;d.lastActivity=Instant.now();d=deliveries.save(d);events.publish(d,"Failed delivery attempt: "+r.note());}return d;}
 private DeliveryRecord find(String id){return deliveries.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found"));}
}
@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
class DeliveryAdminApi {private final DeliveryRepository deliveries;DeliveryAdminApi(DeliveryRepository d){deliveries=d;}@GetMapping("/deliveries") List<DeliveryRecord> all(){return deliveries.findAll();}@GetMapping("/delayed") List<DeliveryRecord> delayed(){Instant t=Instant.now().minus(Duration.ofHours(1));return deliveries.findAll().stream().filter(d->d.expectedDelivery!=null&&d.expectedDelivery.isBefore(t)&&d.status!=ParcelStatus.DELIVERED).toList();}@GetMapping("/exceptions") List<DeliveryRecord> exceptions(){return deliveries.findByStatus(ParcelStatus.EXCEPTION);}}
@Component class DeliveryMonitor {
 private final DeliveryRepository deliveries;private final DeliveryEventPublisher events;
 DeliveryMonitor(DeliveryRepository d,DeliveryEventPublisher e){deliveries=d;events=e;}
 @Scheduled(fixedDelayString="${DELAY_SCAN_MS:60000}") @Transactional void scan(){Instant delay=Instant.now().minus(Duration.ofHours(1)),inactive=Instant.now().minus(Duration.ofHours(24));for(DeliveryRecord d:deliveries.findAll()){if(d.status==ParcelStatus.DELIVERED||d.status==ParcelStatus.EXCEPTION)continue;String note=null;if(d.expectedDelivery!=null&&d.expectedDelivery.isBefore(delay))note="Delivery delay detected; parcel requires attention";else if(d.lastActivity!=null&&d.lastActivity.isBefore(inactive))note="Parcel exception detected after prolonged inactivity";if(note!=null){d.status=ParcelStatus.EXCEPTION;d.lastActivity=Instant.now();deliveries.save(d);events.publish(d,note);}}}
}
