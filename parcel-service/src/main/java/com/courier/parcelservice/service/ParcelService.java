package com.courier.parcelservice.service;

import com.courier.parcelservice.dto.CreateParcelRequest;
import com.courier.parcelservice.dto.ParcelResponse;
import com.courier.parcelservice.dto.UpdateParcelStatusRequest;
import com.courier.parcelservice.exception.ApiException;
import com.courier.parcelservice.model.Parcel;
import com.courier.parcelservice.model.ParcelStatus;
import com.courier.parcelservice.repository.ParcelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final TrackingIdGenerator trackingIdGenerator;

    @Value("${parcel.expected-delivery-days:3}")
    private int expectedDeliveryDays;

    public ParcelService(
            ParcelRepository parcelRepository,
            TrackingIdGenerator trackingIdGenerator) {
        this.parcelRepository = parcelRepository;
        this.trackingIdGenerator = trackingIdGenerator;
    }

    public ParcelResponse createParcel(CreateParcelRequest request) {

        LocalDate expectedDate = request.getExpectedDeliveryDate();

        if (expectedDate == null) {
            expectedDate = LocalDate.now().plusDays(expectedDeliveryDays);
        }

        if (expectedDate.isBefore(LocalDate.now())) {
            throw new ApiException(
                    400,
                    "Expected delivery date cannot be in the past"
            );
        }

        Parcel parcel = new Parcel();

        parcel.setTrackingId(trackingIdGenerator.generate());
        parcel.setCustomerId(request.getCustomerId());
        parcel.setParcelType(request.getParcelType());
        parcel.setSenderAddress(request.getSenderAddress());
        parcel.setReceiverAddress(request.getReceiverAddress());
        parcel.setStatus(ParcelStatus.BOOKED);
        parcel.setExpectedDeliveryDate(expectedDate);

        LocalDateTime now = LocalDateTime.now();
        parcel.setCreatedAt(now);
        parcel.setUpdatedAt(now);

        return toResponse(parcelRepository.save(parcel));
    }

    public List<ParcelResponse> getAllParcels() {
        return parcelRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ParcelResponse> getParcelsByUser(Long userId) {
        return parcelRepository
                .findByCustomerIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ParcelResponse getParcelByTrackingId(String trackingId) {

        validateTrackingId(trackingId);

        Parcel parcel = parcelRepository
                .findByTrackingId(trackingId)
                .orElseThrow(() ->
                        new ApiException(
                                404,
                                "Parcel not found: " + trackingId
                        ));

        return toResponse(parcel);
    }

    public ParcelResponse updateStatus(
            String trackingId,
            UpdateParcelStatusRequest request) {

        validateTrackingId(trackingId);

        Parcel parcel = parcelRepository
                .findByTrackingId(trackingId)
                .orElseThrow(() ->
                        new ApiException(
                                404,
                                "Parcel not found: " + trackingId
                        ));

        ParcelStatus currentStatus = parcel.getStatus();
        ParcelStatus newStatus = request.getStatus();

        if (currentStatus == newStatus) {
            throw new ApiException(
                    409,
                    "Parcel is already in status " + currentStatus
            );
        }

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new ApiException(
                    409,
                    "Invalid status transition: "
                            + currentStatus
                            + " -> "
                            + newStatus
            );
        }

        parcel.setStatus(newStatus);
        parcel.setUpdatedAt(LocalDateTime.now());

        return toResponse(parcelRepository.save(parcel));
    }

    private boolean isValidTransition(
            ParcelStatus current,
            ParcelStatus next) {

        Map<ParcelStatus, Set<ParcelStatus>> transitions =
                new EnumMap<>(ParcelStatus.class);

        transitions.put(
                ParcelStatus.BOOKED,
                EnumSet.of(
                        ParcelStatus.ASSIGNED,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.ASSIGNED,
                EnumSet.of(
                        ParcelStatus.PICKED_UP,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.PICKED_UP,
                EnumSet.of(
                        ParcelStatus.IN_TRANSIT,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.IN_TRANSIT,
                EnumSet.of(
                        ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.OUT_FOR_DELIVERY,
                EnumSet.of(
                        ParcelStatus.DELIVERED,
                        ParcelStatus.DELIVERY_FAILED,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.DELIVERY_FAILED,
                EnumSet.of(
                        ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.DELAYED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.DELAYED,
                EnumSet.of(
                        ParcelStatus.ASSIGNED,
                        ParcelStatus.PICKED_UP,
                        ParcelStatus.IN_TRANSIT,
                        ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.DELIVERED,
                        ParcelStatus.DELIVERY_FAILED,
                        ParcelStatus.EXCEPTION
                )
        );

        transitions.put(
                ParcelStatus.EXCEPTION,
                EnumSet.of(
                        ParcelStatus.ASSIGNED,
                        ParcelStatus.PICKED_UP,
                        ParcelStatus.IN_TRANSIT,
                        ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.DELIVERY_FAILED
                )
        );

        transitions.put(
                ParcelStatus.DELIVERED,
                EnumSet.noneOf(ParcelStatus.class)
        );

        return transitions
                .getOrDefault(current, Collections.emptySet())
                .contains(next);
    }

    private void validateTrackingId(String trackingId) {

        if (trackingId == null ||
                !trackingId.matches("^TRK\\d{12}$")) {

            throw new ApiException(
                    400,
                    "Invalid tracking ID format"
            );
        }
    }

    private ParcelResponse toResponse(Parcel parcel) {

        ParcelResponse response = new ParcelResponse();

        response.setId(parcel.getId());
        response.setTrackingId(parcel.getTrackingId());
        response.setCustomerId(parcel.getCustomerId());
        response.setParcelType(parcel.getParcelType());
        response.setSenderAddress(parcel.getSenderAddress());
        response.setReceiverAddress(parcel.getReceiverAddress());
        response.setStatus(parcel.getStatus());
        response.setExpectedDeliveryDate(
                parcel.getExpectedDeliveryDate()
        );
        response.setCreatedAt(parcel.getCreatedAt());
        response.setUpdatedAt(parcel.getUpdatedAt());

        return response;
    }
}