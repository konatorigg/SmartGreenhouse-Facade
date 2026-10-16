package greenhouse.b;

import greenhouse.storage.CsvStore;
import java.nio.file.Path;
import java.util.Optional;

/** B's own persistence gateway. */
public final class ClimateRepository {
    private final CsvStore store;
    public ClimateRepository(Path root) { store = new CsvStore(root.resolve("B_climate")); }
    public void save(ClimateAction a) {
        store.write(a.id(), a.id(), String.valueOf(a.liters()), String.valueOf(a.fanMinutes()), a.status().name());
    }
    public Optional<ClimateAction> find(String id) {
        return store.read(id).map(row -> ClimateAction.restore(row[0], Integer.parseInt(row[1]),
                Integer.parseInt(row[2]), ClimateAction.Status.valueOf(row[3])));
    }
    public static void main(String[] args) {
        Path root = Path.of("demo-b-data");
        new IrrigationController().verify(3, true);
        int minutes = new VentilationUnit().minutes(70, true);
        ClimateAction action = new ClimateAction("b-demo", 3, minutes);
        action.activate(); new ClimateRepository(root).save(action);
        System.out.println("B independently stored: " + new ClimateRepository(root).find("b-demo").orElseThrow().status());
    }
}
