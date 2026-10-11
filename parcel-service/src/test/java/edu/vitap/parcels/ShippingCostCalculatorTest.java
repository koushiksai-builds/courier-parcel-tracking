package edu.vitap.parcels;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ShippingCostCalculatorTest {
    private ShippingCostCalculator.Quote quote(String weight, String distance, DeliverySpeed speed) {
        return ShippingCostCalculator.calculate(new BigDecimal(weight), new BigDecimal(distance), speed);
    }
    @Test void standardAndSameDayExamples() {
        assertEquals(new BigDecimal("70.00"), quote("1", "30", DeliverySpeed.STANDARD).totalShippingCost());
        assertEquals(new BigDecimal("110.00"), quote("2", "75", DeliverySpeed.STANDARD).totalShippingCost());
        assertEquals(new BigDecimal("160.00"), quote("2", "75", DeliverySpeed.SAME_DAY).totalShippingCost());
    }
    @Test void weightRoundsAdditionalPartUpAndDistanceBandsAreApplied() {
        assertEquals(new BigDecimal("160.00"), quote("3", "150", DeliverySpeed.STANDARD).totalShippingCost());
        assertEquals(new BigDecimal("150.00"), quote("1", "250", DeliverySpeed.STANDARD).totalShippingCost());
        assertEquals(new BigDecimal("110.00"), quote("1.2", "75", DeliverySpeed.STANDARD).totalShippingCost());
    }
    @Test void rejectsInvalidWeightDistanceAndMissingSpeed() {
        assertThrows(IllegalArgumentException.class, () -> quote("0", "10", DeliverySpeed.STANDARD));
        assertThrows(IllegalArgumentException.class, () -> quote("-2", "10", DeliverySpeed.STANDARD));
        assertThrows(IllegalArgumentException.class, () -> quote("1", "-1", DeliverySpeed.STANDARD));
        assertThrows(IllegalArgumentException.class, () -> quote("1", "10", null));
    }
}
