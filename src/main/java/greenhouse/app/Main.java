package greenhouse.app;

import greenhouse.facade.*;
import java.nio.file.Path;
import java.util.Scanner;

/** Working end-to-end CLI. All records are saved under ./greenhouse-data/. */
public final class Main {
    private final Scanner input = new Scanner(System.in);
    private final GreenhouseFacade workflow;
    private final AdministrationFacade admin;

    public Main(Path data) {
        workflow = new GreenhouseFacade(data);
        admin = new AdministrationFacade(data);
    }

    private String ask(String prompt) { System.out.print(prompt); return input.nextLine().trim(); }
    private int number(String prompt) { return Integer.parseInt(ask(prompt)); }
    private boolean online(String prompt) { return ask(prompt + " (yes/no): ").equalsIgnoreCase("yes"); }

    private void run() {
        while (true) {
            System.out.println("\nSMART GREENHOUSE\n1 Start A only\n2 Execute B only\n3 Finish C only\n4 Run A -> B -> C\n5 Resume saved cycle\n6 Inspect saved cycle\n7 List cycles\n8 Manually compensate\n0 Exit");
            String choice = ask("Choose: ");
            if (choice.equals("0")) break;
            try {
                String id;
                switch (choice) {
                    case "1" -> {
                        id = ask("Cycle ID: "); workflow.startCycle(id, number("Soil moisture 0-100: "));
                        System.out.println("A saved. Exit and restart to verify persistence.");
                    }
                    case "2" -> {
                        id = ask("Cycle ID: "); workflow.executeB(id, number("Humidity 0-100: "),
                                online("Pump online?"), online("Fan online?"));
                        System.out.println("B saved.");
                    }
                    case "3" -> {
                        id = ask("Cycle ID: "); workflow.finishC(id, number("Nutrient stock in mg: "));
                        System.out.println("C saved; cycle completed.");
                    }
                    case "4" -> {
                        id = ask("Cycle ID: "); int soil = number("Soil moisture 0-100: ");
                        int humidity = number("Humidity 0-100: ");
                        boolean pump = online("Pump online?"); boolean fan = online("Fan online?");
                        int stock = number("Nutrient stock in mg: ");
                        workflow.runCycle(id, soil, humidity, pump, fan, stock);
                        System.out.println("Entire cycle completed.");
                    }
                    case "5" -> {
                        id = ask("Cycle ID: "); int humidity = number("Humidity 0-100: ");
                        boolean pump = online("Pump online?"); boolean fan = online("Fan online?");
                        int stock = number("Nutrient stock in mg: ");
                        workflow.resume(id, humidity, pump, fan, stock);
                        System.out.println("Resume completed.");
                    }
                    case "6" -> System.out.println(admin.inspect(ask("Cycle ID: ")));
                    case "7" -> System.out.println("Saved IDs: " + admin.listCycles());
                    case "8" -> {
                        id = ask("Cycle ID: "); admin.manuallyCompensate(id);
                        System.out.println("Compensated.\n" + admin.inspect(id));
                    }
                    default -> System.out.println("Unknown menu choice.");
                }
            } catch (RuntimeException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Path root = Path.of(args.length > 0 ? args[0] : "greenhouse-data");
        System.out.println("Data folder: " + root.toAbsolutePath());
        new Main(root).run();
    }
}
