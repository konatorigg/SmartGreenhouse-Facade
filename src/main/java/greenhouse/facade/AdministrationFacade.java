package greenhouse.facade;

import greenhouse.a.*;
import greenhouse.b.*;
import greenhouse.c.*;
import java.nio.file.Path;
import java.util.List;

/** Narrow facade for inspection and manual compensation. */
public final class AdministrationFacade {
    private final ReservationRepository reservations;
    private final ClimateRepository climates;
    private final NutritionRepository nutrition;
    private final CycleRepository cycles;
    private final GreenhouseFacade workflow;

    public AdministrationFacade(Path root) {
        reservations = new ReservationRepository(root);
        climates = new ClimateRepository(root);
        nutrition = new NutritionRepository(root);
        cycles = new CycleRepository(root);
        workflow = new GreenhouseFacade(root);
    }

    public String inspect(String id) {
        String cycle = cycles.find(id).map(x -> x.stage().toString()).orElse("NOT_FOUND");
        String a = reservations.find(id).map(x -> x.status() + " (" + x.liters() + " L reserved)").orElse("NOT_STARTED");
        String b = climates.find(id).map(x -> x.status() + " (ventilate " + x.fanMinutes() + " min)").orElse("NOT_STARTED");
        String c = nutrition.find(id).map(x -> x.status() + " (" + x.doseMg() + " mg); " + x.alert()).orElse("NOT_STARTED");
        return "Cycle " + id + "\nProgress: " + cycle + "\nA: " + a + "\nB: " + b + "\nC: " + c;
    }
    public List<String> listCycles() { return cycles.ids(); }
    public void manuallyCompensate(String id) { workflow.compensate(id); }
}
