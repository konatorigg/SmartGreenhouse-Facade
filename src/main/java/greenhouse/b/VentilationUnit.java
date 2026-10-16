package greenhouse.b;

import greenhouse.storage.GreenhouseException;

/** Subsystem B: humidity-based ventilation rule. */
public final class VentilationUnit {
    public int minutes(int humidity, boolean fanOnline) {
        if (humidity < 0 || humidity > 100) throw new GreenhouseException("Humidity must be 0..100");
        if (!fanOnline) throw new GreenhouseException("B failure: ventilation fan is OFFLINE");
        return Math.max(2, (humidity - 40 + 9) / 10);
    }
}
