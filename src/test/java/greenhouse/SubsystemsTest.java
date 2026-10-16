package greenhouse;

import greenhouse.a.*;
import greenhouse.b.*;
import greenhouse.c.*;
import greenhouse.facade.*;
import greenhouse.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class SubsystemsTest {
    @TempDir Path temp;

    @Test void soilArrayAndSchedulerMakeIndependentDecisions() {
        SoilSensorArray array = new SoilSensorArray();
        IrrigationScheduler planner = new IrrigationScheduler();
        assertEquals(0, array.read(0));
        assertEquals(100, array.read(100));
        assertEquals(3, planner.reserveLiters(array.read(35)));
        assertEquals(6, planner.reserveLiters(0));
        assertThrows(GreenhouseException.class, () -> array.read(-1));
        assertThrows(GreenhouseException.class, () -> planner.reserveLiters(-1));
        assertThrows(GreenhouseException.class, () -> planner.reserveLiters(60));
    }

    @Test void reservationRejectsIllegalTransitionsAndPersistsIndependently() {
        WaterReservation r = new WaterReservation("soil", 33, 3);
        assertEquals(WaterReservation.Status.NEW, r.status());
        assertThrows(GreenhouseException.class, r::cancel);
        r.reserve();
        assertThrows(GreenhouseException.class, r::reserve);
        ReservationRepository repo = new ReservationRepository(temp);
        repo.save(r);
        assertEquals(33, new ReservationRepository(temp).find("soil").orElseThrow().soilMoisture());
        r.cancel(); repo.save(r);
        assertEquals(WaterReservation.Status.CANCELLED, new ReservationRepository(temp).find("soil").orElseThrow().status());
        assertThrows(GreenhouseException.class, r::finish);
        assertThrows(GreenhouseException.class, () -> new WaterReservation("bad", 20, 0));
    }

    @Test void climateBusinessRulesAndStatusesAreIndependent() {
        IrrigationController pump = new IrrigationController();
        VentilationUnit fan = new VentilationUnit();
        pump.verify(3, true);
        assertThrows(GreenhouseException.class, () -> pump.verify(0, true));
        assertThrows(GreenhouseException.class, () -> pump.verify(3, false));
        assertEquals(3, fan.minutes(65, true));
        assertEquals(2, fan.minutes(30, true));
        assertThrows(GreenhouseException.class, () -> fan.minutes(-1, true));
        assertThrows(GreenhouseException.class, () -> fan.minutes(60, false));
        ClimateAction action = new ClimateAction("climate", 3, 4);
        assertThrows(GreenhouseException.class, action::stop);
        action.activate();
        assertThrows(GreenhouseException.class, action::activate);
        ClimateRepository repo = new ClimateRepository(temp);
        repo.save(action);
        assertEquals(ClimateAction.Status.ACTIVE, new ClimateRepository(temp).find("climate").orElseThrow().status());
        assertEquals(4, new ClimateRepository(temp).find("climate").orElseThrow().fanMinutes());
        action.stop(); repo.save(action);
        assertEquals(ClimateAction.Status.STOPPED, new ClimateRepository(temp).find("climate").orElseThrow().status());
        assertThrows(GreenhouseException.class, action::complete);
        assertThrows(GreenhouseException.class, () -> new ClimateAction("bad", 3, 0));
    }

    @Test void nutrientBusinessRulesAndStatusesAreIndependent() {
        NutrientDispenser dispenser = new NutrientDispenser();
        AlertService alerts = new AlertService();
        assertEquals(300, dispenser.doseMg(3, 1000));
        assertThrows(GreenhouseException.class, () -> dispenser.doseMg(3, 100));
        assertThrows(GreenhouseException.class, () -> dispenser.doseMg(-1, 100));
        NutritionRecord feeding = new NutritionRecord("feeding", 300);
        assertEquals(NutritionRecord.Status.NEW, feeding.status());
        assertThrows(GreenhouseException.class, () -> new NutritionRecord("bad", -2));
        feeding.apply(alerts.message("feeding", 300));
        new NutritionRepository(temp).save(feeding);
        assertEquals(NutritionRecord.Status.APPLIED, new NutritionRepository(temp).find("feeding").orElseThrow().status());
        assertTrue(new NutritionRepository(temp).find("feeding").orElseThrow().alert().contains("300"));
        assertThrows(GreenhouseException.class, feeding::cancel);
        NutritionRecord cancelled = new NutritionRecord("cancelled", 200);
        cancelled.cancel();
        assertEquals(NutritionRecord.Status.CANCELLED, cancelled.status());
        assertThrows(GreenhouseException.class, () -> cancelled.apply("late"));
    }

    @Test void stageTransitionsAreValidated() {
        CycleRecord progress = new CycleRecord("id", CycleRecord.Stage.PENDING_B);
        assertThrows(GreenhouseException.class, () -> progress.moveTo(CycleRecord.Stage.COMPLETED));
        progress.moveTo(CycleRecord.Stage.PENDING_C);
        progress.moveTo(CycleRecord.Stage.COMPLETED);
        assertThrows(GreenhouseException.class, () -> progress.moveTo(CycleRecord.Stage.COMPENSATING));
        CycleRecord rollback = new CycleRecord("other", CycleRecord.Stage.PENDING_B);
        rollback.moveTo(CycleRecord.Stage.COMPENSATING);
        rollback.moveTo(CycleRecord.Stage.COMPENSATED);
        assertThrows(GreenhouseException.class, () -> rollback.moveTo(CycleRecord.Stage.PENDING_B));
    }

    @Test void csvReallyPersistsAndValidatesIdsAndFields() {
        CsvStore csv = new CsvStore(temp.resolve("raw"));
        assertTrue(csv.read("x").isEmpty());
        csv.write("x", "x", "12");
        assertArrayEquals(new String[]{"x", "12"}, new CsvStore(temp.resolve("raw")).read("x").orElseThrow());
        assertEquals(1, csv.ids().size());
        assertThrows(GreenhouseException.class, () -> csv.write("../hijack", "bad"));
        assertThrows(GreenhouseException.class, () -> csv.write("x", "comma,bad"));
    }
}
