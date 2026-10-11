package edu.vitap.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface NotificationRepository extends JpaRepository<ParcelNotification,Long>{List<ParcelNotification> findByCustomerIdAndArchivedOrderByCreatedAtDesc(Long id,boolean archived);boolean existsByEventId(String eventId);}
interface FailedEventRepository extends JpaRepository<FailedEvent,Long>{}
interface EmailDeliveryRepository extends JpaRepository<EmailDelivery,Long>{
 boolean existsByEventId(String eventId);
 List<EmailDelivery> findByStatusAndAttemptsLessThanOrderByUpdatedAtAsc(String status,int attempts);
 List<EmailDelivery> findAllByOrderByCreatedAtDesc();
}
