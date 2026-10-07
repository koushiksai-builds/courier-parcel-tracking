package com.courier.parcelservice.dto;

import com.courier.parcelservice.model.ParcelStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateParcelStatusRequest {

    @NotNull
    private ParcelStatus status;

    public ParcelStatus getStatus() {
        return status;
    }

    public void setStatus(ParcelStatus status) {
        this.status = status;
    }
}