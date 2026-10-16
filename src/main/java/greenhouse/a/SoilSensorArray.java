package greenhouse.a;

import greenhouse.storage.GreenhouseException;

/** Subsystem A: validates a soil-sensor reading. */
public final class SoilSensorArray {
    public int read(int moisturePercent) {
        if (moisturePercent < 0 || moisturePercent > 100)
            throw new GreenhouseException("Soil moisture must be 0..100");
        return moisturePercent;
    }
}
