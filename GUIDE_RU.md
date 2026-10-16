# Как сделать и защитить тему №9 — Smart Greenhouse

## Идея одной фразой

Пользователь запускает один цикл управления теплицей. **Facade** последовательно обращается к трём независимым подсистемам: сначала резервирует полив (A), затем активирует оборудование (B), потом проверяет запас удобрений и фиксирует подтверждение (C). Если поздний этап не удался, фасад отменяет уже сохранённые действия в обратном порядке.

## Как открыть и запустить

1. Распакуй проект в папку `SmartGreenhouse`.
2. IntelliJ IDEA → `File` → `Open` → выбери папку проекта. Подожди импорта Maven.
3. Открой `src/main/java/greenhouse/app/Main.java` и нажми зелёный треугольник возле `main`.
4. Внизу откроется консоль с меню. Вводи значения по подсказкам.
5. Альтернатива на Windows без Maven: открой командную строку в папке проекта и запусти `run.bat` (нужна JDK 17+).
6. Maven-тесты и отчёт покрытия: `mvn clean verify`. Отчёт: `target/site/jacoco/index.html`.

## Какая роль у каждого класса

### Подсистема A — Soil / Reservation

- `SoilSensorArray`: проверяет значение влажности (0–100%).
- `IrrigationScheduler`: рассчитывает количество воды. Если влажность 60% или больше, полив не требуется.
- `WaterReservation`: хранит `id`, влажность, литры и статус NEW / RESERVED / CANCELLED / FINISHED. Сам проверяет допустимые переходы.
- `ReservationRepository`: записывает и читает объект A из CSV.

### Подсистема B — Climate / Execution

- `IrrigationController`: проверяет, работает ли насос и корректно ли количество воды.
- `VentilationUnit`: проверяет вентилятор и рассчитывает длительность вентиляции по влажности воздуха.
- `ClimateAction`: хранит параметры команды и статус NEW / ACTIVE / STOPPED / COMPLETED.
- `ClimateRepository`: записывает и читает объект B из CSV.

### Подсистема C — Fertilizer / Confirmation

- `NutrientDispenser`: рассчитывает дозу удобрений (100 мг на литр воды) и проверяет запас.
- `AlertService`: создаёт текст уведомления об успешном цикле.
- `NutritionRecord`: хранит дозу, уведомление и статус NEW / APPLIED / CANCELLED.
- `NutritionRepository`: сохраняет и читает объект C из CSV.

### Объединение

- `GreenhouseFacade`: один главный класс, управляющий A → B → C и компенсацией.
- `AdministrationFacade`: второй, более узкий фасад для просмотра состояния и ручной отмены.
- `CycleRecord`: статус всего процесса PENDING_B → PENDING_C → COMPLETED или COMPENSATING → COMPENSATED.
- `CycleRepository`: записывает прогресс в папку `workflow`.
- `CsvStore`: общий простой механизм записи/чтения файлов. Это инфраструктура, а не четвёртая бизнес-подсистема.
- `Main`: консольное приложение.

## Примеры ввода на демонстрации

### Сценарий №1: успех

Выбери меню **4** и введи:

```
Cycle ID: ok1
Soil moisture: 35
Humidity: 70
Pump online? yes
Fan online? yes
Nutrient stock in mg: 1000
```

Меню **6**, `ok1`. Результат: `Progress: COMPLETED`, `A: FINISHED`, `B: COMPLETED`, `C: APPLIED`.

### Сценарий №2: ошибка B → отмена A

Снова меню **4**, но с ID `failB` и `Pump online? no`; другие данные как раньше. Результат: `A: CANCELLED`, `B: NOT_STARTED`, `Progress: COMPENSATED`.

**Как объяснить:** A успела записать резерв полива в файл. На этапе B насос оказался недоступен. Фасад прочитал сохранённый резерв и отменил его.

### Сценарий №3: ошибка C → отмена B, потом A

Снова меню **4**, но с ID `failC`, насос `yes`, вентилятор `yes`, `Nutrient stock in mg: 0`. Результат: `B: STOPPED`, `A: CANCELLED`, `Progress: COMPENSATED`.

**Как объяснить:** A и B уже были записаны как успешные этапы. C обнаружила нехватку удобрений. Фасад прежде всего деактивировал команду оборудования B, а потом отменил резерв A.

### Сценарий №4: настоящий restart

1. Меню **1** → ID `restart`, влажность `30`. Создаётся A, прогресс `PENDING_B`.
2. Меню **2** → ID `restart`, влажность воздуха `60`, насос `yes`, вентилятор `yes`. Создаётся B, прогресс `PENDING_C`.
3. Закрой приложение (меню **0**). Не удаляй папку `greenhouse-data`.
4. Запусти `Main` заново → меню **5**, ID `restart`, влажность воздуха `60`, насос `yes`, вентилятор `yes`, запас удобрений `1000`.
5. Меню **6** → цикл `COMPLETED`. Состояние сохранено между перезапусками.

Важно: не используй один и тот же ID для двух новых запусков сценария, иначе программа специально выдаст `Cycle ID already exists`. Сброс: удали папку `greenhouse-data` при закрытом приложении.

## Самый главный метод, который нужно понимать

В `GreenhouseFacade`:

```java
public void runCycle(String id, int moisture, int humidity,
                     boolean pumpOnline, boolean fanOnline, int nutrientStockMg) {
    startCycle(id, moisture);
    resume(id, humidity, pumpOnline, fanOnline, nutrientStockMg);
}
```

`startCycle()` сохраняет A и состояние `PENDING_B`. `resume()` продолжает B и C, основываясь на состоянии **на диске**. В `executeB()` и `finishC()` ошибки вызывают метод `compensate()`, который сначала проверяет наличие ACTIVE B в CSV и переводит его в STOPPED, а потом при наличии RESERVED A переводит его в CANCELLED.

## Частые вопросы на защите

**Q: Where is the Facade?** — `GreenhouseFacade` is the main facade. `AdministrationFacade` is the second facade for inspection and manual cancellation.

**Q: Why Facade, not Adapter?** — Facade simplifies the use of three subsystems in one workflow. Adapter converts one incompatible interface to another. We need coordination rather than conversion.

**Q: Are the subsystems independent?** — Yes. Packages A/B/C do not import each other. Each subsystem has its own business logic, entity and CSV repository. Each repository also has a standalone `main()` for demonstration.

**Q: How is data persisted?** — Each subsystem uses real CSV files through its own repository: `A_reservations/`, `B_climate/`, `C_nutrition/`. The progress is stored separately in `workflow/`.

**Q: What happens when B fails?** — The system cancels the previously saved A reservation.

**Q: What happens when C fails?** — The system stops persisted B activation first and cancels A second.

**Q: Is it a real hardware controller?** — No. This is a working software simulation with persistent equipment-command state. It does not control physical pumps. Reversing B means stopping the active control command, not retrieving previously used water.

**Q: What about F.I.R.S.T.?** — Tests use separate temporary storage, make assertions, and cover both compensation depths and restart. Tests should be committed together with implementation. Use JaCoCo to measure at least 80% line coverage.

**Q: What can I change in live coding?** — The easiest change is a new business rule: adjust the 60% soil threshold in `IrrigationScheduler`, the 100 mg/L fertilizer calculation in `NutrientDispenser`, or the fan formula in `VentilationUnit`; then add or update a matching test.

## Что ещё самому проверить перед сдачей

- Выполнить `mvn clean verify` в среде с Maven и получить фактическое покрытие **не ниже 80%**.
- Добавить Git-коммиты с кодом **и тестами**, чтобы было видно требование *Timely*.
- Убедиться, что `diagram.svg` отражает текущие классы, если будешь менять структуру.
- Залить папку проекта на GitHub, но не папку `target` и не персональные CSV-сохранения из `greenhouse-data`.
