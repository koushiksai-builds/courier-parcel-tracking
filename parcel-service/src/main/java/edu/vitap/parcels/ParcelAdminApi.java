package edu.vitap.parcels;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class ParcelAdminApi {
    private final ParcelRepository parcels;

    ParcelAdminApi(ParcelRepository parcels) {
        this.parcels = parcels;
    }

    @GetMapping("/parcels")
    List<Parcel> all() {
        return parcels.findAll();
    }
}
