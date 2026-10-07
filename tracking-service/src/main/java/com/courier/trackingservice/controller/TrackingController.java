package com.courier.trackingservice.controller;

import com.courier.trackingservice.dto.HistoryResponse;
import com.courier.trackingservice.dto.RecordEventRequest;
import com.courier.trackingservice.dto.TrackingResponse;
import com.courier.trackingservice.service.TrackingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{trackingId}")
    public TrackingResponse getCurrentStatus(
            @PathVariable String trackingId) {

        return trackingService.getCurrentStatus(trackingId);
    }

    @GetMapping("/{trackingId}/history")
    public List<HistoryResponse> getHistory(
            @PathVariable String trackingId) {

        return trackingService.getHistory(trackingId);
    }

    // Temporary endpoint for Phase 5 testing.
    // Will be removed when RabbitMQ is connected in Phase 7.
    @PostMapping("/dev/events")
    public TrackingResponse recordEventForTesting(
            @Valid @RequestBody RecordEventRequest request) {

        return trackingService.recordEvent(request);
    }
}