package edu.vitap.tracking;
import edu.vitap.common.*;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
record TrackingView(String trackingId,ParcelStatus status,Instant expectedDelivery,List<TrackingEntry> history){}
@RestController @RequestMapping("/api/tracking")
class PublicTrackingApi {
 private final TrackedParcelRepository parcels;private final TrackingEntryRepository history;
 PublicTrackingApi(TrackedParcelRepository p,TrackingEntryRepository h){parcels=p;history=h;}
 @GetMapping("/{trackingId}") TrackingView track(@PathVariable String trackingId){TrackedParcel p=parcels.findById(trackingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tracking ID not found"));return new TrackingView(p.trackingId,p.status,p.expectedDelivery,history.findByTrackingIdOrderByOccurredAtAsc(trackingId));}
}
@RestController @RequestMapping("/api/parcels")
class CustomerTrackingApi {
 private final TrackedParcelRepository parcels;private final TrackingEntryRepository history;private final UserRepository users;
 CustomerTrackingApi(TrackedParcelRepository p,TrackingEntryRepository h,UserRepository u){parcels=p;history=h;users=u;}
 @GetMapping("/{trackingId}/history") @PreAuthorize("isAuthenticated()") List<TrackingEntry> history(@PathVariable String trackingId,Authentication auth){TrackedParcel p=parcels.findById(trackingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));AppUser u=CurrentUser.get(users,auth);if(u.getRole()!=Role.ADMIN&&!u.getId().equals(p.customerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return history.findByTrackingIdOrderByOccurredAtAsc(trackingId);}
}
@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
class TrackingAdminApi {private final TrackingEntryRepository history;TrackingAdminApi(TrackingEntryRepository h){history=h;}@GetMapping("/activities") List<TrackingEntry> activities(){return history.findTop200ByOrderByOccurredAtDesc();}}
