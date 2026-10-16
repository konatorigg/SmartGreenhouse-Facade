package greenhouse.facade;

import greenhouse.storage.GreenhouseException;

/** Durable progress of the overall A -> B -> C operation. */
public final class CycleRecord {
    public enum Stage { PENDING_B, PENDING_C, COMPENSATING, COMPENSATED, COMPLETED }
    private final String id;
    private Stage stage;

    public CycleRecord(String id, Stage stage) { this.id = id; this.stage = stage; }
    public String id() { return id; }
    public Stage stage() { return stage; }
    public void moveTo(Stage next) {
        boolean legal = switch (stage) {
            case PENDING_B -> next == Stage.PENDING_C || next == Stage.COMPENSATING;
            case PENDING_C -> next == Stage.COMPLETED || next == Stage.COMPENSATING;
            case COMPENSATING -> next == Stage.COMPENSATED;
            case COMPENSATED, COMPLETED -> false;
        };
        if (!legal) throw new GreenhouseException("Invalid workflow change: " + stage + " -> " + next);
        stage = next;
    }
}
