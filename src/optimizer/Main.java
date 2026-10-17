package optimizer;

import java.awt.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.*;

/** One entry point for the desktop, reproducible experiments and self-tests. */
public final class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--"))
                throw new IllegalArgumentException("Expected --option: " + args[i]);
            String key = args[i].substring(2), value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) value = args[++i];
            flags.put(key, value);
        }
        if (flags.containsKey("help")) {
            System.out.println(
                    """
java -jar ServerPlacementLab.jar                      Open desktop app
java -jar ServerPlacementLab.jar --self-test          Verify model and methods
java -jar ServerPlacementLab.jar --cli --method EXACT --out results/exact
java -jar ServerPlacementLab.jar --cli --method RANDOM --out results/random
java -jar ServerPlacementLab.jar --study 20 --out results/study
java -jar ServerPlacementLab.jar --compare-ten 20 --budget 20000 --out results/ten-algorithms
Methods: HYBRID (SRS), EXACT, RANDOM, TARGET; CONSTRUCTIVE is an extra ablation.
Main protocol: Exact all assignments, Random 100000, Target <= 500000, SRS batch stopping.
Defaults: assessment PDF data / --objective ASSESSMENT --seed 2026 --budget 20000 --batch 10000 --patience 1
          --target 0.8 --min 0 --alpha 0.75 --equal-budget 100000
Use --original for the preserved weighted CSV project (target 18 ms, minimum two per server).
--original --objective LATENCY keeps the old additive score. COST/BALANCED use illustrative prices.
--input data reads the supplied CSVs. --ui-scale 1|2 overrides desktop scaling.
Study and single-run exports include CSV records and PNG charts. Exact has no random seed.
""");
            return;
        }
        if (flags.containsKey("self-test")) {
            SelfTest.run();
            return;
        }
        if (flags.containsKey("cli")
                || flags.containsKey("study")
                || flags.containsKey("compare")
                || flags.containsKey("compare-ten")) {
            ProblemData data =
                    flags.containsKey("input")
                            ? CsvFiles.read(Path.of(flags.get("input")))
                            : flags.containsKey("original")
                                    ? ProblemData.supplied()
                                    : ProblemData.assessmentSupplied();
            Evaluator e =
                    new Evaluator(
                            data,
                            Integer.parseInt(
                                    flags.getOrDefault("min", data.assessment() ? "0" : "2")),
                            Evaluator.Objective.valueOf(
                                    flags.getOrDefault(
                                            "objective",
                                            data.assessment() ? "ASSESSMENT" : "WEIGHTED")),
                            Double.parseDouble(flags.getOrDefault("alpha", ".75")));
            SearchMethod method = SearchMethod.valueOf(flags.getOrDefault("method", "HYBRID"));
            int standardBudget =
                    flags.containsKey("compare-ten")
                            ? 20_000
                            : method == SearchMethod.RANDOM
                                    ? 100_000
                                    : method == SearchMethod.TARGET
                                            ? 500_000
                                            : data.assessment() ? 20_000 : 1_000_000;
            SearchConfig config =
                    new SearchConfig(
                            Long.parseLong(flags.getOrDefault("seed", "2026")),
                            Integer.parseInt(
                                    flags.getOrDefault("budget", String.valueOf(standardBudget))),
                            Integer.parseInt(flags.getOrDefault("batch", "10000")),
                            Integer.parseInt(
                                    flags.getOrDefault(
                                            "patience", method == SearchMethod.HYBRID ? "1" : "0")),
                            .35,
                            Double.parseDouble(
                                    flags.getOrDefault(
                                            "target", data.assessment() ? "0.8" : "18")));
            Path output =
                    Path.of(flags.getOrDefault("out", "results/run-" + System.currentTimeMillis()));
            if (flags.containsKey("compare-ten")) {
                var comparison =
                        AlgorithmComparison.run(
                                e,
                                config,
                                Integer.parseInt(flags.get("compare-ten")),
                                System.out::println,
                                null,
                                () -> false);
                AlgorithmComparison.write(output, comparison);
                System.out.println(AlgorithmComparison.report(comparison));
                System.out.println("Saved comparison: " + output.toAbsolutePath());
            } else if (flags.containsKey("study") || flags.containsKey("compare")) {
                int seeds =
                        Integer.parseInt(
                                flags.getOrDefault("study", flags.getOrDefault("compare", "20")));
                ExperimentRunner.Study study =
                        ExperimentRunner.study(
                                e,
                                config,
                                seeds,
                                Integer.parseInt(flags.getOrDefault("equal-budget", "100000")),
                                System.out::println,
                                () -> false);
                ExperimentRunner.write(output, study);
                if (data.assessment())
                    AssessmentExperiments.additional(
                            output, e, config.seed(), seeds, System.out::println, () -> false);
                System.out.println("Saved study and charts: " + output.toAbsolutePath());
                for (SearchMethod m : ExperimentRunner.MAIN_METHODS) {
                    ExperimentRunner.Summary s = ExperimentRunner.summarize(study, m, "main");
                    System.out.printf(
                            Locale.ROOT,
                            "%s best=%.6f mean=%.6f worst=%.6f target=%.1f%% evals=%.0f time=%.2f"
                                    + " ms%n",
                            m,
                            s.best(),
                            s.mean(),
                            s.worst(),
                            s.successRate(),
                            s.evaluations(),
                            s.runtime());
                }
            } else {
                java.util.List<SearchEvent> events = new java.util.ArrayList<>();
                SearchResult result =
                        new Optimizer().run(e, method, config, null, () -> false, events::add);
                CsvFiles.export(output, e, result);
                CsvFiles.writeEvents(output, events);
                StudyCharts.exportRun(output, e, result);
                if (data.assessment()) {
                    java.util.List<AssessmentPlots.Point> points = new java.util.ArrayList<>();
                    for (SearchEvent event : events)
                        if (event.bestEvaluation() != null)
                            points.add(
                                    new AssessmentPlots.Point(
                                            event.evaluations(), event.bestEvaluation()));
                    AssessmentPlots.export(output, data, result, points);
                }
                System.out.println(
                        "Stop: "
                                + result.stopReason()
                                + "; evaluations="
                                + result.evaluations()
                                + "; unique="
                                + result.unique()
                                + "; feasible="
                                + result.feasible());
                if (result.evaluation() != null)
                    System.out.printf(
                            Locale.ROOT,
                            "Best score %.9f %s; runtime %d ms; proven optimal %s%n",
                            result.evaluation().score(),
                            ResultComparison.unit(e),
                            result.milliseconds(),
                            result.provenOptimal(data.searchSpace()));
                System.out.println("Saved run: " + output.toAbsolutePath());
            }
            return;
        }
        DisplaySettings.configure(flags.get("ui-scale"));
        if (GraphicsEnvironment.isHeadless())
            throw new IllegalStateException(
                    "No graphical display. Use --cli, --study or --self-test.");
        SwingUtilities.invokeLater(
                () -> {
                    style();
                    JFrame frame = new JFrame("Service Placement Lab - Optimization Methods");
                    Dashboard dashboard = flags.containsKey("original") ? new Dashboard() : null;
                    AssessmentDashboard assessment =
                            dashboard == null ? new AssessmentDashboard() : null;
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setContentPane(dashboard != null ? dashboard : assessment);
                    Rectangle available =
                            GraphicsEnvironment.getLocalGraphicsEnvironment()
                                    .getMaximumWindowBounds();
                    frame.setMinimumSize(
                            new Dimension(
                                    Math.min(1180, available.width),
                                    Math.min(740, available.height)));
                    frame.setSize(Math.min(1440, available.width), Math.min(940, available.height));
                    frame.setLocationRelativeTo(null);
                    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    frame.setVisible(true);
                    if (assessment != null) {
                        if (flags.containsKey("ten-demo")) assessment.compareTen.doClick();
                        else if (flags.containsKey("demo")) assessment.start(false);
                        else if (flags.containsKey("study-demo")) assessment.experiments.doClick();
                    } else if (flags.containsKey("study-demo")) {
                        dashboard.tabs.setSelectedIndex(2);
                        dashboard.compare.doClick();
                    } else if (flags.containsKey("demo")) dashboard.run.doClick();
                });
    }

    static void style() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 12));
        UIManager.put("Panel.background", Visuals.BACK);
        UIManager.put("TabbedPane.selected", Color.WHITE);
    }

    static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container c) layout(c);
    }
}
