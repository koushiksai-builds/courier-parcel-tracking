package edu.vitap.courier;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface UserRepository extends JpaRepository<AppUser,Long> { Optional<AppUser> findByEmail(String email); }
interface ParcelRepository extends JpaRepository<Parcel,Long> {
    Optional<Parcel> findByTrackingId(String trackingId);
    List<Parcel> findByCustomerId(Long customerId);
    List<Parcel> findByCourierId(Long courierId);
    List<Parcel> findByStatus(ParcelStatus status);
}
interface TrackingRepository extends JpaRepository<TrackingEntry,Long> {
    List<TrackingEntry> findByTrackingIdOrderByOccurredAtAsc(String trackingId);
    List<TrackingEntry> findTop200ByOrderByOccurredAtDesc();
}
interface NotificationRepository extends JpaRepository<ParcelNotification,Long> { List<ParcelNotification> findByCustomerIdOrderByCreatedAtDesc(Long customerId); }
interface FailedEventRepository extends JpaRepository<FailedEvent,Long> {}
