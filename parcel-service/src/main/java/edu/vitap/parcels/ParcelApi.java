package edu.vitap.parcels;

import edu.vitap.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record BookingRequest(@NotBlank String senderName,@NotBlank String senderAddress,@NotBlank String senderPhone,
 @NotBlank String receiverName,@NotBlank String receiverAddress,@NotBlank String receiverPhone,
 @NotBlank String description,Instant expectedDelivery,
 @NotNull @DecimalMin(value="0", inclusive=false, message="Weight must be greater than 0 kg") @Digits(integer=4,fraction=6) BigDecimal weightKg,
 @NotNull @DecimalMin(value="0", message="Estimated distance cannot be negative") @Digits(integer=6,fraction=4) BigDecimal distanceKm,
 String deliverySpeed) {
}

record ShippingCostRequest(@NotBlank String source,@NotBlank String destination,
 @NotNull @DecimalMin(value="0", inclusive=false, message="Weight must be greater than 0 kg") @Digits(integer=4,fraction=6) BigDecimal weightKg,
 @NotNull @DecimalMin(value="0", message="Estimated distance cannot be negative") @Digits(integer=6,fraction=4) BigDecimal distanceKm,
 String deliverySpeed) {}

@RestController @RequestMapping("/api/parcels")
class ParcelApi {
 private final ParcelRepository parcels;private final UserRepository users;private final RabbitTemplate events;
 ParcelApi(ParcelRepository p,UserRepository u,RabbitTemplate e){parcels=p;users=u;events=e;}

 @PostMapping("/calculate-cost") @PreAuthorize("hasRole('CUSTOMER')")
 ShippingCostCalculator.Quote calculate(@Valid @RequestBody ShippingCostRequest r){
   return quote(r.weightKg(),r.distanceKm(),r.deliverySpeed());
 }

 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CUSTOMER')")
 Parcel book(@Valid @RequestBody BookingRequest r,Authentication auth){
  AppUser user=CurrentUser.get(users,auth);DeliverySpeed speed=parseSpeed(r.deliverySpeed());
  ShippingCostCalculator.Quote quote=ShippingCostCalculator.calculate(r.weightKg(),r.distanceKm(),speed);
  Parcel p=new Parcel();p.trackingId="CP"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);p.customerId=user.getId();
  p.senderName=r.senderName();p.senderAddress=r.senderAddress();p.senderPhone=r.senderPhone();p.receiverName=r.receiverName();p.receiverAddress=r.receiverAddress();p.receiverPhone=r.receiverPhone();p.description=r.description();p.expectedDelivery=r.expectedDelivery();
  p.weightKg=quote.weightKg();p.distanceKm=quote.distanceKm();p.deliverySpeed=speed;p.shippingCost=quote.totalShippingCost();p.status=ParcelStatus.BOOKED;p.createdAt=Instant.now();p=parcels.save(p);
  events.convertAndSend(ParcelEventConfiguration.EXCHANGE,"parcel.created",new ParcelEvent(p.trackingId,p.customerId,p.status,"Parcel booked",p.expectedDelivery,Instant.now(),p.shippingCost));return p;
 }
 @GetMapping @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')") List<Parcel> mine(Authentication auth){AppUser u=CurrentUser.get(users,auth);return u.getRole()==Role.ADMIN?parcels.findAll():parcels.findByCustomerId(u.getId());}
 @GetMapping("/{trackingId}") @PreAuthorize("isAuthenticated()") Parcel get(@PathVariable String trackingId,Authentication auth){Parcel p=find(trackingId);AppUser u=CurrentUser.get(users,auth);if(u.getRole()!=Role.ADMIN&&!Objects.equals(u.getId(),p.customerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p;}
 @GetMapping("/admin/all") @PreAuthorize("hasRole('ADMIN')") List<Parcel> all(){return parcels.findAll();}
 private Parcel find(String id){return parcels.findByTrackingId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tracking ID not found"));}
 private ShippingCostCalculator.Quote quote(BigDecimal weight,BigDecimal distance,String speed){return ShippingCostCalculator.calculate(weight,distance,parseSpeed(speed));}
 private static DeliverySpeed parseSpeed(String value){
  if(value==null||value.isBlank())return DeliverySpeed.STANDARD;
  try{return DeliverySpeed.valueOf(value.trim().toUpperCase(Locale.ROOT));}
  catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Delivery speed must be STANDARD or SAME_DAY");}
 }
}
