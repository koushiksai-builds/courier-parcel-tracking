package edu.vitap.parcels;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ParcelRepository extends JpaRepository<Parcel,Long>{Optional<Parcel> findByTrackingId(String trackingId);List<Parcel> findByCustomerId(Long customerId);}
