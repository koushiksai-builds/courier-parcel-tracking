package com.courier.parcelservice.repository;

import com.courier.parcelservice.model.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    Optional<Parcel> findByTrackingId(String trackingId);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<Parcel> findTopByTrackingIdStartingWithOrderByTrackingIdDesc(String prefix);
}