package com.courier.trackingservice.repository;

import com.courier.trackingservice.model.TrackingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackingRecordRepository extends JpaRepository<TrackingRecord, Long> {

    Optional<TrackingRecord> findByTrackingId(String trackingId);

    boolean existsByTrackingId(String trackingId);
}