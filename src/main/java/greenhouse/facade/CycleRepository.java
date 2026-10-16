package greenhouse.facade;

import greenhouse.storage.CsvStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persists workflow progress independently of any JVM instance. */
public final class CycleRepository {
    private final CsvStore store;
    public CycleRepository(Path root) { store = new CsvStore(root.resolve("workflow")); }
    public void save(CycleRecord r) { store.write(r.id(), r.id(), r.stage().name()); }
    public Optional<CycleRecord> find(String id) {
        return store.read(id).map(row -> new CycleRecord(row[0], CycleRecord.Stage.valueOf(row[1])));
    }
    public List<String> ids() { return store.ids(); }
}
