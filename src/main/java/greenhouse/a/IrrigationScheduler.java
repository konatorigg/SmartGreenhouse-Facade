package greenhouse.a;

import greenhouse.storage.GreenhouseException;

/** Subsystem A: independent business rule for amount of water to reserve. */
public final class IrrigationScheduler {
    public int reserveLiters(int soilMoisture) {
        if (soilMoisture < 0 || soilMoisture > 100)
            throw new GreenhouseException("Invalid moisture");
        if (soilMoisture >= 60)
            throw new GreenhouseException("Soil is already moist: watering is not needed");
        return (60 - soilMoisture + 9) / 10; // 1..6 liters
    }
}
