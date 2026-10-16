package greenhouse.c;

import greenhouse.storage.GreenhouseException;

/** C's persistent state with independently enforced transitions. */
public final class NutritionRecord {
    public enum Status { NEW, APPLIED, CANCELLED }
    private final String id;
    private final int doseMg;
    private String alert;
    private Status status;

    public NutritionRecord(String id, int doseMg) {
        if (doseMg <= 0) throw new GreenhouseException("Nutrient dose must be positive");
        this.id = id; this.doseMg = doseMg; this.status = Status.NEW; this.alert = "";
    }
    public static NutritionRecord restore(String id, int dose, String alert, Status status) {
        NutritionRecord r = new NutritionRecord(id, dose);
        r.alert = alert; r.status = status;
        return r;
    }
    public void apply(String message) { require(Status.NEW); alert = message; status = Status.APPLIED; }
    public void cancel() { require(Status.NEW); status = Status.CANCELLED; }
    private void require(Status expected) {
        if (status != expected) throw new GreenhouseException("C: illegal transition from " + status);
    }
    public String id() { return id; }
    public int doseMg() { return doseMg; }
    public String alert() { return alert; }
    public Status status() { return status; }
}
