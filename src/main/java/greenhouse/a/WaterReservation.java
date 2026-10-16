package greenhouse.a;

import greenhouse.storage.GreenhouseException;

/** A's state, independently enforcing legal transitions. */
public final class WaterReservation {
    public enum Status { NEW, RESERVED, CANCELLED, FINISHED }
    private final String id;
    private final int soilMoisture;
    private final int liters;
    private Status status;

    public WaterReservation(String id, int soilMoisture, int liters) {
        if (liters <= 0 || soilMoisture < 0 || soilMoisture > 100)
            throw new GreenhouseException("Invalid reservation data");
        this.id = id;
        this.soilMoisture = soilMoisture;
        this.liters = liters;
        this.status = Status.NEW;
    }
    public static WaterReservation restore(String id, int soil, int liters, Status status) {
        WaterReservation r = new WaterReservation(id, soil, liters);
        r.status = status;
        return r;
    }
    public void reserve() { require(Status.NEW); status = Status.RESERVED; }
    public void cancel() { require(Status.RESERVED); status = Status.CANCELLED; }
    public void finish() { require(Status.RESERVED); status = Status.FINISHED; }
    private void require(Status expected) {
        if (status != expected) throw new GreenhouseException("A: illegal transition from " + status);
    }
    public String id() { return id; }
    public int soilMoisture() { return soilMoisture; }
    public int liters() { return liters; }
    public Status status() { return status; }
}
