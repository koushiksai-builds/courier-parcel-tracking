package edu.vitap.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface NotificationRepository extends JpaRepository<ParcelNotification,Long>{List<ParcelNotification> findByCustomerIdAndArchivedOrderByCreatedAtDesc(Long id,boolean archived);}
interface FailedEventRepository extends JpaRepository<FailedEvent,Long>{}
