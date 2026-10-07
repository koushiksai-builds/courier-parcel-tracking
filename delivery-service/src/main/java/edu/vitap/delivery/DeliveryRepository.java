package edu.vitap.delivery;
import edu.vitap.common.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface DeliveryRepository extends JpaRepository<DeliveryRecord,String>{List<DeliveryRecord> findByCourierId(Long courierId);List<DeliveryRecord> findByStatus(ParcelStatus status);}
