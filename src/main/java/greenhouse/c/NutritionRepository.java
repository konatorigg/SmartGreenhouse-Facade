package greenhouse.c;

import greenhouse.storage.CsvStore;
import java.nio.file.Path;
import java.util.Optional;

/** C's own persistence gateway. */
public final class NutritionRepository {
    private final CsvStore store;
    public NutritionRepository(Path root) { store = new CsvStore(root.resolve("C_nutrition")); }
    public void save(NutritionRecord r) {
        store.write(r.id(), r.id(), String.valueOf(r.doseMg()), r.alert(), r.status().name());
    }
    public Optional<NutritionRecord> find(String id) {
        return store.read(id).map(row -> NutritionRecord.restore(row[0], Integer.parseInt(row[1]),
                row[2], NutritionRecord.Status.valueOf(row[3])));
    }
    public static void main(String[] args) {
        Path root = Path.of("demo-c-data");
        int dose = new NutrientDispenser().doseMg(3, 800);
        NutritionRecord record = new NutritionRecord("c-demo", dose);
        record.apply(new AlertService().message("c-demo", dose));
        new NutritionRepository(root).save(record);
        System.out.println("C independently stored: " + new NutritionRepository(root).find("c-demo").orElseThrow().status());
    }
}
