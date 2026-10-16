package greenhouse.a;

import greenhouse.storage.CsvStore;
import java.nio.file.Path;
import java.util.Optional;

/** A's own persistence gateway, independent of B and C. */
public final class ReservationRepository {
    private final CsvStore store;
    public ReservationRepository(Path root) { store = new CsvStore(root.resolve("A_reservations")); }
    public void save(WaterReservation r) {
        store.write(r.id(), r.id(), String.valueOf(r.soilMoisture()), String.valueOf(r.liters()), r.status().name());
    }
    public Optional<WaterReservation> find(String id) {
        return store.read(id).map(row -> WaterReservation.restore(row[0], Integer.parseInt(row[1]),
                Integer.parseInt(row[2]), WaterReservation.Status.valueOf(row[3])));
    }
    public static void main(String[] args) {
        Path data = Path.of("demo-a-data");
        ReservationRepository repo = new ReservationRepository(data);
        int soil = new SoilSensorArray().read(35);
        WaterReservation r = new WaterReservation("a-demo", soil, new IrrigationScheduler().reserveLiters(soil));
        r.reserve(); repo.save(r);
        System.out.println("A independently stored: " + repo.find("a-demo").orElseThrow().status());
    }
}
