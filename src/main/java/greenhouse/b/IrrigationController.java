package greenhouse.b;

import greenhouse.storage.GreenhouseException;

/** Subsystem B: verifies hardware conditions before activating irrigation. */
public final class IrrigationController {
    public void verify(int liters, boolean pumpOnline) {
        if (liters <= 0) throw new GreenhouseException("Water amount must be positive");
        if (!pumpOnline) throw new GreenhouseException("B failure: irrigation pump is OFFLINE");
    }
}
