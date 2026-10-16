package greenhouse.b;

import greenhouse.storage.GreenhouseException;

/** B's reversible control-command state (not removal of water already used). */
public final class ClimateAction {
    public enum Status { NEW, ACTIVE, STOPPED, COMPLETED }
    private final String id;
    private final int liters;
    private final int fanMinutes;
    private Status status;

    public ClimateAction(String id, int liters, int fanMinutes) {
        if (liters <= 0 || fanMinutes <= 0) throw new GreenhouseException("Invalid B action");
        this.id = id; this.liters = liters; this.fanMinutes = fanMinutes; this.status = Status.NEW;
    }
    public static ClimateAction restore(String id, int liters, int fanMinutes, Status status) {
        ClimateAction action = new ClimateAction(id, liters, fanMinutes);
        action.status = status;
        return action;
    }
    public void activate() { require(Status.NEW); status = Status.ACTIVE; }
    public void stop() { require(Status.ACTIVE); status = Status.STOPPED; }
    public void complete() { require(Status.ACTIVE); status = Status.COMPLETED; }
    private void require(Status expected) {
        if (status != expected) throw new GreenhouseException("B: illegal transition from " + status);
    }
    public String id() { return id; }
    public int liters() { return liters; }
    public int fanMinutes() { return fanMinutes; }
    public Status status() { return status; }
}
