# Assignment 4 — Smart Greenhouse (Variant 9)

Java 17+, Facade Design Pattern, real CSV persistence, 2 facades, 3 independent subsystems, CLI, JUnit 5, JaCoCo.

## Running

From the project root:

```bash
mvn clean package
java -jar target/smart-greenhouse-facade-1.0-SNAPSHOT.jar
```

You may alternatively compile and run WITHOUT Maven (Java 17+):

```bash
# macOS/Linux
mkdir -p out
find src/main/java -name '*.java' > sources.txt
javac -d out @sources.txt
java -cp out greenhouse.app.Main
```

In IntelliJ IDEA: open this folder as a Maven project and run `greenhouse.app.Main.main()`.
Data is stored in `./greenhouse-data/`, automatically created on first launch.
To reset the application, **close it and delete the `greenhouse-data` directory**. For temporary automated tests a fresh `@TempDir` is used.

## How to demonstrate (exact CLI steps)

### Happy path: A -> B -> C
- Menu `4` (full cycle), ID `ok1`, soil `35`, humidity `70`, pump `yes`, fan `yes`, nutrient stock `1000`.
- Menu `6`, ID `ok1`: A = FINISHED, B = COMPLETED, C = APPLIED, progress = COMPLETED.

### B failure after A persisted: compensate A alone
- Menu `4`, ID `failB`, soil `35`, humidity `70`, pump `no`, fan `yes`, stock `1000`.
- Menu `6`, ID `failB`: A = CANCELLED, B = NOT_STARTED, C = NOT_STARTED, progress = COMPENSATED.
- The pump being offline is a realistic B-stage failure before equipment activation.

### C failure after A and B persisted: compensate B, then A
- Menu `4`, ID `failC`, soil `35`, humidity `70`, pump `yes`, fan `yes`, stock `0`.
- Menu `6`, ID `failC`: B = STOPPED, A = CANCELLED, C = NOT_STARTED, progress = COMPENSATED.
- B is a *persisted control-command activation*; compensation turns that command off, rather than trying to undo physical water already delivered.

### Real restart / persistence demo
- Menu `1`, ID `restart`, soil `30`: A = RESERVED, progress = PENDING_B.
- Menu `2`, ID `restart`, humidity `60`, pump `yes`, fan `yes`: B = ACTIVE, progress = PENDING_C.
- Menu `6` to inspect, then `0` to EXIT the program completely.
- Re-run `java -jar target/smart-greenhouse-facade-1.0-SNAPSHOT.jar`.
- Menu `5`, ID `restart`, humidity `60`, pump `yes`, fan `yes`, stock `1000`.
- Menu `6`: progress = COMPLETED. This uses CSV records read from disk after restarting Java.

### Independent subsystems (not called through the facade)
- Run `greenhouse.a.ReservationRepository.main()` for standalone A.
- Run `greenhouse.b.ClimateRepository.main()` for standalone B.
- Run `greenhouse.c.NutritionRepository.main()` for standalone C.

## Domain architecture (3-5 classes per subsystem)

| A: reservation | B: execution | C: finalization |
|---|---|---|
| SoilSensorArray (validation) | IrrigationController (hardware validation) | NutrientDispenser (stock decision) |
| IrrigationScheduler (business logic) | VentilationUnit (humidity policy) | AlertService (business logic) |
| WaterReservation (entity + legal status transitions) | ClimateAction (entity + legal status transitions) | NutritionRecord (entity + legal status transitions) |
| ReservationRepository (CSV gateway) | ClimateRepository (CSV gateway) | NutritionRepository (CSV gateway) |

Subsystem packages `a`, `b`, `c` have **no imports of each other**. Each one stores its records on disk using only the shared `CsvStore` infrastructure. The facade is the single coordinator.

A statuses: NEW -> RESERVED -> CANCELLED or FINISHED.
B statuses: NEW -> ACTIVE -> STOPPED or COMPLETED.
C statuses: NEW -> APPLIED or CANCELLED.

Progress is persisted as PENDING_B, PENDING_C, COMPENSATING, COMPENSATED, COMPLETED.
The facade checks **actual A, B, C repository records** when compensating, and does not rely on a JVM boolean saying whether B happened.

## Recovery logic and limitations

- If a restarted instance sees PENDING_C, it continues with C, reading B from CSV.
- If B was already saved ACTIVE but the process stopped before saving PENDING_C, `executeB` notices B's persisted ACTIVE record and proceeds without executing B twice.
- If C was already saved APPLIED but the process stopped before completing A/B and the progress marker, `finishC` completes the remaining bookkeeping without applying nutrients twice.
- If the process stops while COMPENSATING, a later resume retries compensation. STOPPED and CANCELLED stages are idempotently skipped.
- The CLI models hardware by entering pump/fan health and nutrient stock. It **does not connect to real greenhouse hardware**. A production system requires actuator acknowledgements, idempotency keys, concurrency control, and recovery of an orphan A record in case of a crash specifically between A.save and progress.save. CSV is suitable for this assignment's single-user embedded demo, not industrial control.

## Principle of Least Knowledge: before / after

**Without Facade, the UI needs to know every subsystem:**

```java
int soil = new SoilSensorArray().read(35);
int liters = new IrrigationScheduler().reserveLiters(soil);
WaterReservation r = new WaterReservation("x", soil, liters);
r.reserve(); new ReservationRepository(root).save(r);
new IrrigationController().verify(liters, true);
int minutes = new VentilationUnit().minutes(70, true);
ClimateAction b = new ClimateAction("x", liters, minutes);
b.activate(); new ClimateRepository(root).save(b);
int dose = new NutrientDispenser().doseMg(liters, 1000);
NutritionRecord c = new NutritionRecord("x", dose);
c.apply(new AlertService().message("x", dose));
new NutritionRepository(root).save(c);
// Still missing consistent failure-handling and progress persistence!
```

**With Facade, the UI calls one immediate collaborator:**

```java
GreenhouseFacade facade = new GreenhouseFacade(root);
facade.runCycle("x", 35, 70, true, true, 1000);
```

The client only knows **what** to do; the facade knows **how** to sequence and compensate.

## Facade vs. Adapter reflection

Facade is appropriate because the client needs **one coherent greenhouse operation** made from several independent services: soil assessment/reservation, irrigation/ventilation, and nutrient confirmation. These subsystems already have valid interfaces. The challenge is coordinating them, saving progress, and undoing committed earlier steps when a later step fails. An Adapter would instead translate an incompatible interface, e.g. convert a legacy fan controller's `startMotor(int rpm)` API into a new `setVentilation(int minutes)` API. That conversion would not provide cross-subsystem workflow orchestration or compensation. So a Facade solves the interaction-complexity problem; Adapter solves interface-compatibility problems.

## Tests and coverage

```bash
mvn clean verify
```

JUnit 5 tests in `src/test/java/` use `@TempDir` for **Fast, Independent, Repeatable** cases, `assert...` for Self-Validating results, plus a domain test DSL (`givenDrySoil(...).whenPumpFails().thenOnlyAIsCancelled()`). Tests include **B failure**, **C failure**, **restart**, interrupted persisted states, illegal status transitions, manual compensation, and invalid input. For the **Timely** criterion, commit your tests with the code that they verify. JaCoCo report appears at `target/site/jacoco/index.html`; check that coverage is **at least 80%** for subsystems and facade(s). Actual coverage must be measured on your own machine; it is not assumed by the presence of tests.

## UML

`diagram.puml` contains a PlantUML class diagram covering the client, both facades, persistence and all subsystems. Export it as PNG/SVG using PlantUML or an IDE plugin and include the exported diagram in the submission. Do not submit the `.puml` alone if the examiner explicitly requires an image/vector file.

## What to submit

1. GitHub repository link with all code, README, exported UML and tests.
2. `mvn clean verify` test output and JaCoCo report (coverage >=80%).
3. Real CLI demo and the ability to reproduce all 3 paths and a restart.
4. Commit history where unit tests were added alongside their implementations (F.I.R.S.T. Timely).
5. Moodle link before the instructor's announced deadline.

## Oral defense quick notes

- **Facade:** `GreenhouseFacade`, `AdministrationFacade`.
- **Business rules:** `IrrigationScheduler`, `VentilationUnit`, `NutrientDispenser`.
- **Entities:** `WaterReservation`, `ClimateAction`, `NutritionRecord`.
- **Persistence:** `ReservationRepository`, `ClimateRepository`, `NutritionRepository`, and `CycleRepository`; CSV files on disk.
- **One-step rollback:** B fails -> cancel A.
- **Two-step rollback:** C fails -> STOP B, then CANCEL A.
- **Why CSV:** lightweight real durable state, no DB server required.
- **Why not Adapter:** nothing needs an incompatible interface translation; we simplify workflow coordination.
