package com.courier.trackingservice.service;

import com.courier.trackingservice.dto.HistoryResponse;
import com.courier.trackingservice.dto.RecordEventRequest;
import com.courier.trackingservice.dto.TrackingResponse;
import com.courier.trackingservice.exception.ApiException;
import com.courier.trackingservice.model.TrackingHistory;
import com.courier.trackingservice.model.TrackingRecord;
import com.courier.trackingservice.model.TrackingStatus;
import com.courier.trackingservice.repository.TrackingHistoryRepository;
import com.courier.trackingservice.repository.TrackingRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TrackingService {

    private static final Pattern TRACKING_ID_PATTERN =
            Pattern.compile("^TRK\\d{12}$");

    private final TrackingRecordRepository recordRepository;
    private final TrackingHistoryRepository historyRepository;

    public TrackingService(
            TrackingRecordRepository recordRepository,
            TrackingHistoryRepository historyRepository) {

        this.recordRepository = recordRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public TrackingResponse recordEvent(RecordEventRequest request) {

        String trackingId = normalizeAndValidate(request.getTrackingId());
        LocalDateTime now = LocalDateTime.now();

        TrackingRecord record;

        if (request.getStatus() == TrackingStatus.BOOKED) {

            if (recordRepository.existsByTrackingId(trackingId)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Tracking record already exists for " + trackingId);
            }

            if (request.getCustomerId() == null ||
                    request.getExpectedDeliveryDate() == null) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "customerId and expectedDeliveryDate are required for a BOOKED event");
            }

            record = new TrackingRecord();
            record.setTrackingId(trackingId);
            record.setCustomerId(request.getCustomerId());
            record.setExpectedDeliveryDate(
                    request.getExpectedDeliveryDate());

        } else {
            record = findRecordOrThrow(trackingId);
        }

        String location = isBlank(request.getLocation())
                ? record.getLocation()
                : request.getLocation().trim();

        if (location == null) {
            location = "Not specified";
        }

        String remarks = isBlank(request.getRemarks())
                ? defaultRemarks(request.getStatus())
                : request.getRemarks().trim();

        // Update current tracking status
        record.setStatus(request.getStatus());
        record.setLocation(location);
        record.setLastUpdatedAt(now);

        TrackingRecord saved = recordRepository.save(record);

        // Save status change in history
        TrackingHistory history = new TrackingHistory();
        history.setTrackingId(trackingId);
        history.setStatus(request.getStatus());
        history.setLocation(location);
        history.setRemarks(remarks);
        history.setTimestamp(now);

        historyRepository.save(history);

        return TrackingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TrackingResponse getCurrentStatus(String trackingId) {

        String id = normalizeAndValidate(trackingId);

        return TrackingResponse.from(
                findRecordOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> getHistory(String trackingId) {

        String id = normalizeAndValidate(trackingId);

        findRecordOrThrow(id);

        return historyRepository
                .findByTrackingIdOrderByTimestampAscIdAsc(id)
                .stream()
                .map(HistoryResponse::from)
                .collect(Collectors.toList());
    }

    private TrackingRecord findRecordOrThrow(String trackingId) {

        return recordRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "No tracking information found for " + trackingId));
    }

    private String normalizeAndValidate(String rawTrackingId) {

        String trackingId = rawTrackingId == null
                ? ""
                : rawTrackingId.trim().toUpperCase();

        if (!TRACKING_ID_PATTERN.matcher(trackingId).matches()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid tracking ID format. Expected something like TRK202610050001");
        }

        return trackingId;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String defaultRemarks(TrackingStatus status) {

        return switch (status) {

            case BOOKED -> "Parcel booked";

            case ASSIGNED -> "Parcel assigned to courier";

            case PICKED_UP -> "Parcel picked up";

            case IN_TRANSIT -> "Parcel in transit";

            case OUT_FOR_DELIVERY -> "Parcel out for delivery";

            case DELIVERED -> "Parcel delivered";

            case DELIVERY_FAILED -> "Delivery attempt failed";

            case DELAYED -> "Parcel delayed";

            case EXCEPTION -> "Parcel exception raised";
        };
    }
}