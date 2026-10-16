package greenhouse.facade;

import greenhouse.a.*;
import greenhouse.b.*;
import greenhouse.c.*;
import greenhouse.storage.*;
import java.nio.file.Path;

/** Client-facing facade coordinating three independently persisted subsystems. */
public final class GreenhouseFacade {
    private final SoilSensorArray sensors = new SoilSensorArray();
    private final IrrigationScheduler scheduler = new IrrigationScheduler();
    private final IrrigationController irrigation = new IrrigationController();
    private final VentilationUnit ventilation = new VentilationUnit();
    private final NutrientDispenser dispenser = new NutrientDispenser();
    private final AlertService alerts = new AlertService();
    private final ReservationRepository a;
    private final ClimateRepository b;
    private final NutritionRepository c;
    private final CycleRepository cycles;

    public GreenhouseFacade(Path dataDirectory) {
        a = new ReservationRepository(dataDirectory);
        b = new ClimateRepository(dataDirectory);
        c = new NutritionRepository(dataDirectory);
        cycles = new CycleRepository(dataDirectory);
    }

    /** A: reserve the amount of water; commit this state and workflow progress to CSV. */
    public void startCycle(String id, int moisturePercent) {
        CsvStore.checkId(id);
        if (cycles.find(id).isPresent() || a.find(id).isPresent())
            throw new GreenhouseException("Cycle ID already exists: " + id);
        int soil = sensors.read(moisturePercent);
        int liters = scheduler.reserveLiters(soil);
        WaterReservation reservation = new WaterReservation(id, soil, liters);
        reservation.reserve();
        a.save(reservation);
        cycles.save(new CycleRecord(id, CycleRecord.Stage.PENDING_B));
    }

    /** B: verify equipment, record activation, advance the durable stage. */
    public void executeB(String id, int humidityPercent, boolean pumpOnline, boolean fanOnline) {
        CycleRecord progress = requireCycle(id);
        if (progress.stage() != CycleRecord.Stage.PENDING_B)
            throw new GreenhouseException("B cannot execute at stage " + progress.stage());
        // Handle crash after B's state was stored but before progress was updated.
        if (b.find(id).map(x -> x.status() == ClimateAction.Status.ACTIVE).orElse(false)) {
            progress.moveTo(CycleRecord.Stage.PENDING_C);
            cycles.save(progress);
            return;
        }
        try {
            WaterReservation reservation = a.find(id).orElseThrow(() -> new GreenhouseException("A record missing"));
            if (reservation.status() != WaterReservation.Status.RESERVED)
                throw new GreenhouseException("No active reservation");
            irrigation.verify(reservation.liters(), pumpOnline);
            int fanMinutes = ventilation.minutes(humidityPercent, fanOnline);
            ClimateAction action = new ClimateAction(id, reservation.liters(), fanMinutes);
            action.activate();
            b.save(action);
            progress.moveTo(CycleRecord.Stage.PENDING_C);
            cycles.save(progress);
        } catch (GreenhouseException e) {
            compensate(id); // persist compensation based on what is actually on disk
            throw e;
        }
    }

    /** C: apply nutrients, create the alert and mark the full operation completed. */
    public void finishC(String id, int nutrientStockMg) {
        CycleRecord progress = requireCycle(id);
        if (progress.stage() != CycleRecord.Stage.PENDING_C)
            throw new GreenhouseException("C cannot execute at stage " + progress.stage());
        try {
            if (c.find(id).isEmpty()) {
                ClimateAction active = b.find(id).orElseThrow(() -> new GreenhouseException("B record missing"));
                if (active.status() != ClimateAction.Status.ACTIVE && active.status() != ClimateAction.Status.COMPLETED)
                    throw new GreenhouseException("B is not active");
                int dose = dispenser.doseMg(active.liters(), nutrientStockMg);
                NutritionRecord feeding = new NutritionRecord(id, dose);
                feeding.apply(alerts.message(id, dose));
                c.save(feeding);
            }
        } catch (GreenhouseException e) {
            // Once C was saved as APPLIED, the correct recovery is to complete, not undo nutrients.
            if (c.find(id).isEmpty()) compensate(id);
            throw e;
        }
        completeCycle(id);
    }

    /** Resume from disk (even from a new JVM), without trusting old in-memory flags. */
    public void resume(String id, int humidityPercent, boolean pumpOnline, boolean fanOnline, int nutrientStockMg) {
        CycleRecord progress = requireCycle(id);
        if (progress.stage() == CycleRecord.Stage.COMPENSATING) { compensate(id); return; }
        if (progress.stage() == CycleRecord.Stage.COMPLETED) return;
        if (progress.stage() == CycleRecord.Stage.COMPENSATED)
            throw new GreenhouseException("Cycle already compensated: " + id);
        if (progress.stage() == CycleRecord.Stage.PENDING_B)
            executeB(id, humidityPercent, pumpOnline, fanOnline);
        finishC(id, nutrientStockMg);
    }

    /** The simple public operation a normal user needs. */
    public void runCycle(String id, int moisture, int humidity,
                         boolean pumpOnline, boolean fanOnline, int nutrientStockMg) {
        startCycle(id, moisture);
        resume(id, humidity, pumpOnline, fanOnline, nutrientStockMg);
    }

    /** Compensation is idempotent: B is stopped before A is cancelled. */
    public void compensate(String id) {
        CycleRecord progress = requireCycle(id);
        if (progress.stage() == CycleRecord.Stage.COMPLETED)
            throw new GreenhouseException("A completed cycle cannot be cancelled");
        if (c.find(id).map(x -> x.status() == NutritionRecord.Status.APPLIED).orElse(false))
            throw new GreenhouseException("Nutrients already applied; resume to complete instead");
        if (progress.stage() == CycleRecord.Stage.COMPENSATED) return;
        if (progress.stage() != CycleRecord.Stage.COMPENSATING) {
            progress.moveTo(CycleRecord.Stage.COMPENSATING);
            cycles.save(progress);
        }
        b.find(id).ifPresent(action -> {
            if (action.status() == ClimateAction.Status.ACTIVE) {
                action.stop(); b.save(action);
            }
        });
        a.find(id).ifPresent(reservation -> {
            if (reservation.status() == WaterReservation.Status.RESERVED) {
                reservation.cancel(); a.save(reservation);
            }
        });
        progress.moveTo(CycleRecord.Stage.COMPENSATED);
        cycles.save(progress);
    }

    private void completeCycle(String id) {
        ClimateAction action = b.find(id).orElseThrow();
        WaterReservation reservation = a.find(id).orElseThrow();
        if (action.status() == ClimateAction.Status.ACTIVE) { action.complete(); b.save(action); }
        if (reservation.status() == WaterReservation.Status.RESERVED) { reservation.finish(); a.save(reservation); }
        CycleRecord progress = requireCycle(id);
        progress.moveTo(CycleRecord.Stage.COMPLETED);
        cycles.save(progress);
    }

    private CycleRecord requireCycle(String id) {
        return cycles.find(id).orElseThrow(() -> new GreenhouseException("Unknown cycle: " + id));
    }
}
