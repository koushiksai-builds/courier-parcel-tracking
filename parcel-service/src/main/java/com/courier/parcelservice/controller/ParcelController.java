package com.courier.parcelservice.controller;

import com.courier.parcelservice.dto.CreateParcelRequest;
import com.courier.parcelservice.dto.ParcelResponse;
import com.courier.parcelservice.dto.UpdateParcelStatusRequest;
import com.courier.parcelservice.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/parcels")
public class ParcelController {

    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    public ResponseEntity<ParcelResponse> createParcel(
            @Valid @RequestBody CreateParcelRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(parcelService.createParcel(request));
    }

    @GetMapping
    public ResponseEntity<List<ParcelResponse>> getAllParcels() {

        return ResponseEntity.ok(
                parcelService.getAllParcels()
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ParcelResponse>> getParcelsByUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                parcelService.getParcelsByUser(userId)
        );
    }

    @GetMapping("/{trackingId}")
    public ResponseEntity<ParcelResponse> getParcelByTrackingId(
            @PathVariable String trackingId) {

        return ResponseEntity.ok(
                parcelService.getParcelByTrackingId(trackingId)
        );
    }

    @PutMapping("/{trackingId}")
    public ResponseEntity<ParcelResponse> updateStatus(
            @PathVariable String trackingId,
            @Valid @RequestBody UpdateParcelStatusRequest request) {

        return ResponseEntity.ok(
                parcelService.updateStatus(trackingId, request)
        );
    }
}