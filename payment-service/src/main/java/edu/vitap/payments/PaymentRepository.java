package edu.vitap.payments;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment,Long> {
    Optional<Payment> findByTrackingId(String trackingId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
