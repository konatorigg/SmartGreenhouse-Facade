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

/** F.I.R.S.T.: each test uses a new @TempDir; no shared persistent state. */
public class GreenhouseFacadeTest {
    @TempDir Path temp;

    // Small domain-specific testing language (DSL) for an easy-to-read test.
    private Scenario givenDrySoil(String id) { return new Scenario(id); }
    private class Scenario {
        final String id;
        final GreenhouseFacade facade = new GreenhouseFacade(temp);
        Scenario(String id) { this.id = id; facade.startCycle(id, 30); }
        Scenario whenPumpFails() {
            assertThrows(GreenhouseException.class, () -> facade.executeB(id, 70, false, true));
            return this;
        }
        Scenario whenNutrientsRunOut() {
            facade.executeB(id, 70, true, true);
            assertThrows(GreenhouseException.class, () -> facade.finishC(id, 0));
            return this;
        }
        void thenOnlyAIsCancelled() {
            assertEquals(WaterReservation.Status.CANCELLED, a(id).status());
            assertTrue(new ClimateRepository(temp).find(id).isEmpty());
            assertTrue(new NutritionRepository(temp).find(id).isEmpty());
            assertEquals(CycleRecord.Stage.COMPENSATED, progress(id).stage());
        }
        void thenBStopsBeforeAIsCancelled() {
            assertEquals(ClimateAction.Status.STOPPED, b(id).status());
            assertEquals(WaterReservation.Status.CANCELLED, a(id).status());
            assertTrue(new NutritionRepository(temp).find(id).isEmpty());
            assertEquals(CycleRecord.Stage.COMPENSATED, progress(id).stage());
        }
    }

    private WaterReservation a(String id) { return new ReservationRepository(temp).find(id).orElseThrow(); }
    private ClimateAction b(String id) { return new ClimateRepository(temp).find(id).orElseThrow(); }
    private NutritionRecord c(String id) { return new NutritionRepository(temp).find(id).orElseThrow(); }
    private CycleRecord progress(String id) { return new CycleRepository(temp).find(id).orElseThrow(); }

    @Test void fullChainPersistsAcrossSeparateRepoInstances() {
        new GreenhouseFacade(temp).runCycle("success", 35, 70, true, true, 1000);
        assertEquals(WaterReservation.Status.FINISHED, a("success").status());
        assertEquals(3, a("success").liters());
        assertEquals(ClimateAction.Status.COMPLETED, b("success").status());
        assertEquals(NutritionRecord.Status.APPLIED, c("success").status());
        assertEquals(300, c("success").doseMg());
        assertTrue(c("success").alert().contains("success"));
        assertEquals(CycleRecord.Stage.COMPLETED, progress("success").stage());
        assertTrue(new AdministrationFacade(temp).inspect("success").contains("COMPLETED"));
        assertEquals(1, new AdministrationFacade(temp).listCycles().size());
    }

    @Test void bFailureCompensatesOnlyA() { givenDrySoil("no-pump").whenPumpFails().thenOnlyAIsCancelled(); }

    @Test void fanFailureCompensatesOnlyA() {
        givenDrySoil("no-fan");
        assertThrows(GreenhouseException.class,
                () -> new GreenhouseFacade(temp).executeB("no-fan", 65, true, false));
        assertEquals(WaterReservation.Status.CANCELLED, a("no-fan").status());
        assertEquals(CycleRecord.Stage.COMPENSATED, progress("no-fan").stage());
    }

    @Test void cFailureCompensatesBThenA() {
        givenDrySoil("no-fertilizer").whenNutrientsRunOut().thenBStopsBeforeAIsCancelled();
    }

    @Test void restartMidChainResumesFromPersistedPendingC() {
        GreenhouseFacade beforeRestart = new GreenhouseFacade(temp);
        beforeRestart.startCycle("restart", 30);
        beforeRestart.executeB("restart", 65, true, true);
        assertEquals(CycleRecord.Stage.PENDING_C, progress("restart").stage());
        // A new Facade instance has no memory of what the previous instance did.
        GreenhouseFacade afterRestart = new GreenhouseFacade(temp);
        afterRestart.resume("restart", 65, true, true, 1200);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("restart").stage());
        assertEquals(NutritionRecord.Status.APPLIED, c("restart").status());
    }

    @Test void restartAfterAResumesBothLaterStages() {
        new GreenhouseFacade(temp).startCycle("after-a", 10);
        assertEquals(CycleRecord.Stage.PENDING_B, progress("after-a").stage());
        new GreenhouseFacade(temp).resume("after-a", 60, true, true, 2000);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("after-a").stage());
    }

    @Test void restartAfterBPersistedButBeforeProgressSaveRecovers() {
        new GreenhouseFacade(temp).startCycle("b-save-crash", 25);
        ClimateAction active = new ClimateAction("b-save-crash", a("b-save-crash").liters(), 4);
        active.activate(); new ClimateRepository(temp).save(active);
        // Progress is still PENDING_B, despite the persisted ACTIVE B record.
        assertEquals(CycleRecord.Stage.PENDING_B, progress("b-save-crash").stage());
        new GreenhouseFacade(temp).resume("b-save-crash", 65, true, true, 1500);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("b-save-crash").stage());
    }

    @Test void restartAfterCPersistedCompletesWithoutDispensingTwice() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("c-save-crash", 25);
        facade.executeB("c-save-crash", 65, true, true);
        NutritionRecord alreadyApplied = new NutritionRecord("c-save-crash", 400);
        alreadyApplied.apply("Existing alert");
        new NutritionRepository(temp).save(alreadyApplied);
        new GreenhouseFacade(temp).finishC("c-save-crash", 0);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("c-save-crash").stage());
        assertEquals("Existing alert", c("c-save-crash").alert());
    }

    @Test void interruptedCompensationResumesAfterRestart() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("undo-restart", 20);
        facade.executeB("undo-restart", 60, true, true);
        CycleRecord record = progress("undo-restart");
        record.moveTo(CycleRecord.Stage.COMPENSATING);
        new CycleRepository(temp).save(record);
        new GreenhouseFacade(temp).resume("undo-restart", 50, true, true, 0);
        assertEquals(CycleRecord.Stage.COMPENSATED, progress("undo-restart").stage());
        assertEquals(ClimateAction.Status.STOPPED, b("undo-restart").status());
        assertEquals(WaterReservation.Status.CANCELLED, a("undo-restart").status());
    }

    @Test void adminCanCancelAndInspectPersistedState() {
        new GreenhouseFacade(temp).startCycle("manual", 40);
        AdministrationFacade admin = new AdministrationFacade(temp);
        assertTrue(admin.inspect("manual").contains("PENDING_B"));
        admin.manuallyCompensate("manual");
        admin.manuallyCompensate("manual"); // idempotent, safe to repeat
        assertEquals(CycleRecord.Stage.COMPENSATED, progress("manual").stage());
        assertTrue(admin.inspect("manual").contains("CANCELLED"));
        assertTrue(admin.inspect("notthere").contains("NOT_FOUND"));
    }

    @Test void completedCycleCannotBeManuallyCancelledButCanBeResumedSafely() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.runCycle("done", 30, 50, true, true, 1000);
        assertThrows(GreenhouseException.class, () -> new AdministrationFacade(temp).manuallyCompensate("done"));
        facade.resume("done", 50, true, true, 0);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("done").stage());
    }

    @Test void compensatingACompletedCycleIsNotAllowed() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.runCycle("finished", 30, 50, true, true, 1000);
        assertThrows(GreenhouseException.class, () -> facade.compensate("finished"));
    }

    @Test void previouslyCompensatedCycleCannotRestart() {
        givenDrySoil("undo").whenPumpFails();
        assertThrows(GreenhouseException.class, () -> new GreenhouseFacade(temp).resume("undo", 50, true, true, 1000));
    }

    @Test void invalidInputStopsBeforeCommit() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        assertThrows(GreenhouseException.class, () -> facade.startCycle("invalid", 200));
        assertThrows(GreenhouseException.class, () -> facade.startCycle("wet", 80));
        assertThrows(GreenhouseException.class, () -> facade.startCycle("bad/id", 20));
        assertTrue(new AdministrationFacade(temp).listCycles().isEmpty());
        assertTrue(new ReservationRepository(temp).find("invalid").isEmpty());
    }

    @Test void duplicateIdAndWrongStageAreRejected() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("duplicate", 30);
        assertThrows(GreenhouseException.class, () -> facade.startCycle("duplicate", 20));
        assertThrows(GreenhouseException.class, () -> facade.finishC("duplicate", 1000));
        facade.executeB("duplicate", 60, true, true);
        assertThrows(GreenhouseException.class, () -> facade.executeB("duplicate", 60, true, true));
        facade.finishC("duplicate", 1000);
        assertThrows(GreenhouseException.class, () -> facade.finishC("duplicate", 1000));
        assertThrows(GreenhouseException.class, () -> facade.executeB("missing", 60, true, true));
    }

    @Test void invalidHumidityTriggersCompensation() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("humid", 30);
        assertThrows(GreenhouseException.class, () -> facade.executeB("humid", 101, true, true));
        assertEquals(CycleRecord.Stage.COMPENSATED, progress("humid").stage());
    }

    @Test void invalidStockTriggersBothCompensations() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("negative", 30);
        facade.executeB("negative", 60, true, true);
        assertThrows(GreenhouseException.class, () -> facade.finishC("negative", -1));
        assertEquals(CycleRecord.Stage.COMPENSATED, progress("negative").stage());
        assertEquals(ClimateAction.Status.STOPPED, b("negative").status());
    }

    @Test void appliedNutrientsPreventManualRollbackWhileAwaitingFinalization() {
        GreenhouseFacade facade = new GreenhouseFacade(temp);
        facade.startCycle("applied", 30);
        facade.executeB("applied", 60, true, true);
        NutritionRecord applied = new NutritionRecord("applied", 300);
        applied.apply("Applied"); new NutritionRepository(temp).save(applied);
        assertThrows(GreenhouseException.class, () -> facade.compensate("applied"));
        facade.finishC("applied", 0);
        assertEquals(CycleRecord.Stage.COMPLETED, progress("applied").stage());
    }
}
