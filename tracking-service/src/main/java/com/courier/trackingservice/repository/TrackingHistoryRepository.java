package com.courier.trackingservice.repository;

import com.courier.trackingservice.model.TrackingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackingHistoryRepository extends JpaRepository<TrackingHistory, Long> {

    List<TrackingHistory> findByTrackingIdOrderByTimestampAscIdAsc(String trackingId);
}