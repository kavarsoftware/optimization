package optimizer.ui;

import static optimizer.brain.SelfTest.check;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** UI integration checks stay beside the UI to avoid exposing widget internals. */
public final class DashboardTest {
    public static void run() throws Exception {
        AtomicReference<AssessmentDashboard> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    AssessmentDashboard dashboard = new AssessmentDashboard();
                    dashboard.pace.setValue(0);
                    dashboard.budget.setValue(100);
                    view.set(dashboard);
                    dashboard.start(false);
                });
        long end = System.currentTimeMillis() + 10000;
        boolean[] busy = {true};
        while (busy[0] && System.currentTimeMillis() < end) {
            SwingUtilities.invokeAndWait(() -> busy[0] = view.get().busy);
            if (busy[0]) Thread.sleep(10);
        }
        check(!busy[0], "Assessment SwingWorker finishes");
        SwingUtilities.invokeAndWait(
                () -> {
                    AssessmentDashboard dashboard = view.get();
                    check(
                            dashboard.result != null
                                    && dashboard.result.evaluation().feasible()
                                    && dashboard.networkModel.getRowCount() == 12,
                            "Assessment GUI displays feasible result and 12 network rows");
                    check(
                            dashboard.errorMessage == null
                                    && Files.exists(dashboard.saved.resolve("performance.png")),
                            "Assessment GUI saves performance/timeline evidence");
                    dashboard.motion.stop();
                });
        String assignmentCsv = Files.readString(view.get().saved.resolve("assignment.csv"));
        check(
                assignmentCsv.startsWith("service,server,cpu_cores,ram_gb,disk_gb,gpu_slots")
                        && !assignmentCsv.contains("NaN"),
                "Assessment assignment export uses real network/work columns without old weighted"
                    + " fields");
    }
}
