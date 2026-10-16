package greenhouse.c;

/** Subsystem C: notification business logic; message is persisted in NutritionRecord. */
public final class AlertService {
    public String message(String cycleId, int doseMg) {
        return "Cycle " + cycleId + " finished: " + doseMg + " mg nutrients applied";
    }
}
