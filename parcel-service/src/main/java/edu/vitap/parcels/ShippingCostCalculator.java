package edu.vitap.parcels;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Central source of truth for the demo shipping price. */
final class ShippingCostCalculator {
    private static final BigDecimal BASE = new BigDecimal("50.00");
    private ShippingCostCalculator() {}

    record Quote(BigDecimal weightKg, BigDecimal distanceKm, BigDecimal basePrice,
                 BigDecimal weightCharge, BigDecimal distanceCharge, DeliverySpeed deliverySpeed,
                 BigDecimal deliverySpeedCharge, BigDecimal totalShippingCost) {}

    static Quote calculate(BigDecimal weightKg, BigDecimal distanceKm, DeliverySpeed speed) {
        if (weightKg == null || weightKg.signum() <= 0) throw new IllegalArgumentException("Weight must be greater than 0 kg");
        if (distanceKm == null || distanceKm.signum() < 0) throw new IllegalArgumentException("Estimated distance must be 0 km or greater");
        if (speed == null) throw new IllegalArgumentException("Delivery speed must be STANDARD or SAME_DAY");

        BigDecimal weightCharge = weightKg.compareTo(BigDecimal.ONE) <= 0
                ? BigDecimal.ZERO.setScale(2)
                : weightKg.subtract(BigDecimal.ONE).setScale(0, RoundingMode.CEILING).multiply(new BigDecimal("20.00"));
        BigDecimal distanceCharge = distanceKm.compareTo(new BigDecimal("50")) <= 0 ? new BigDecimal("20.00")
                : distanceKm.compareTo(new BigDecimal("100")) <= 0 ? new BigDecimal("40.00")
                : distanceKm.compareTo(new BigDecimal("200")) <= 0 ? new BigDecimal("70.00") : new BigDecimal("100.00");
        BigDecimal speedCharge = speed == DeliverySpeed.SAME_DAY ? new BigDecimal("50.00") : BigDecimal.ZERO.setScale(2);
        BigDecimal total = BASE.add(weightCharge).add(distanceCharge).add(speedCharge).setScale(2);
        return new Quote(weightKg, distanceKm, BASE, weightCharge, distanceCharge, speed, speedCharge, total);
    }
}
