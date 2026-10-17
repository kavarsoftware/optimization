package optimizer;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;

/** Launch the assessment application or reproduce its experiments from the terminal. */
public final class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        Set<String> switches = Set.of("help", "self-test", "cli");
        Set<String> options = Set.of("method", "seed", "budget", "batch", "patience", "greediness", "study", "out", "input", "ui-scale");
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected --option");
            String key = args[i].substring(2);
            if (switches.contains(key)) flags.put(key, "true");
            else if (options.contains(key) && i + 1 < args.length && !args[i+1].startsWith("--")) flags.put(key, args[++i]);
            else throw new IllegalArgumentException("Unknown option or missing value: --" + key);
        }
        if (flags.containsKey("help")) {
            System.out.println("""
Service Placement (Java 17+)
  ./run.sh                         Open application
  ./run.sh --self-test              Run assessment tests T1-T12
  ./run.sh --cli --method LOCAL_SEARCH    Restart local search
  ./run.sh --cli --method RANDOM    Random baseline
  ./run.sh --cli --method EXACT     Full enumeration (ignores budget)
  ./run.sh --study 10 --out results/study
Options: --seed 2026 --budget 20000 --batch 10000 --patience 1
         --greediness 0.35 --input data/assessment --ui-scale 2
Study uses equal budgets, patience off, and includes budget/network sensitivity.
""");
            return;
        }
        if (flags.containsKey("self-test") || flags.containsKey("cli") || flags.containsKey("study"))
            System.setProperty("java.awt.headless", "true");
        if (flags.containsKey("self-test")) { SelfTest.run(); return; }
        if (flags.containsKey("cli") || flags.containsKey("study")) {
            ProblemData data = flags.containsKey("input") ? CsvFiles.read(Path.of(flags.get("input"))) : ProblemData.assessmentSupplied();
            Evaluator evaluator = new Evaluator(data);
            SearchConfig config = new SearchConfig(
                    Long.parseLong(flags.getOrDefault("seed", "2026")),
                    Integer.parseInt(flags.getOrDefault("budget", "20000")),
                    Integer.parseInt(flags.getOrDefault("batch", "10000")),
                    Integer.parseInt(flags.getOrDefault("patience", "1")),
                    Double.parseDouble(flags.getOrDefault("greediness", ".35")));
            Path output = Path.of(flags.getOrDefault("out", "results/run-" + System.currentTimeMillis()));
            if (flags.containsKey("study")) {
                int seeds = Integer.parseInt(flags.get("study"));
                if (seeds < 1) throw new IllegalArgumentException("Seed count must be positive");
                var study = ExperimentRunner.study(evaluator, config, seeds, config.budget(), System.out::println, () -> false);
                ExperimentRunner.write(output, study);
                AssessmentExperiments.additional(output, evaluator, config, seeds, System.out::println, () -> false);
                System.out.println(AssessmentExperiments.summary(study));
            } else {
                SearchMethod method = SearchMethod.valueOf(flags.getOrDefault("method", "LOCAL_SEARCH"));
                var events = new ArrayList<SearchEvent>();
                var result = new Optimizer().run(evaluator, method, config, null, () -> false, events::add);
                CsvFiles.export(output, evaluator, result);
                CsvFiles.writeEvents(output, events);
                StudyCharts.exportRun(output, evaluator, result);
                var points = new ArrayList<AssessmentPlots.Point>();
                for (var event : events) if (event.bestEvaluation() != null) points.add(new AssessmentPlots.Point(event.evaluations(), event.bestEvaluation()));
                AssessmentPlots.export(output, data, result, points);
                System.out.println(result.stopReason() + " / evaluations=" + result.evaluations());
                if (result.evaluation() != null) System.out.println("Best J=" + result.evaluation().score());
            }
            System.out.println("Saved: " + output.toAbsolutePath());
            return;
        }
        DisplaySettings.configure(flags.get("ui-scale"));
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Use --cli, --study or --self-test without a display.");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
            catch (Exception ignored) { }
            JFrame frame = new JFrame("Service Placement");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new AssessmentDashboard());
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setSize(Math.min(1440, screen.width), Math.min(940, screen.height));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
    static void layout(Container container) {
        container.invalidate();
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
}
