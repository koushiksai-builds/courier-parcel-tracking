package com.courier.parcelservice.service;

import com.courier.parcelservice.repository.ParcelRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class TrackingIdGenerator {

    private final ParcelRepository parcelRepository;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    public TrackingIdGenerator(ParcelRepository parcelRepository) {
        this.parcelRepository = parcelRepository;
    }

    public synchronized String generate() {

        String date = LocalDate.now().format(DATE_FORMAT);
        String prefix = "TRK" + date;

        int nextSequence = 1;

        var latest = parcelRepository
                .findTopByTrackingIdStartingWithOrderByTrackingIdDesc(prefix);

        if (latest.isPresent()) {
            String trackingId = latest.get().getTrackingId();
            String sequence = trackingId.substring(trackingId.length() - 4);
            nextSequence = Integer.parseInt(sequence) + 1;
        }

        return prefix + String.format("%04d", nextSequence);
    }
}