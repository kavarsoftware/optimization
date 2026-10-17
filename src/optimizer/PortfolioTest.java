package optimizer;

import static optimizer.SelfTest.check;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import javax.swing.SwingUtilities;

/**
 * Cross-method behavioral checks: no hard-coded solution, hidden evaluations or invalid incumbents.
 */
final class PortfolioTest {
    static void run() throws Exception {
        Evaluator e =
                new Evaluator(
                        ProblemData.assessmentDefaults(), 0, Evaluator.Objective.ASSESSMENT, .75);
        SearchConfig config = new SearchConfig(2026, 1500, 300, 0, .35, .8);
        check(
                AlgorithmComparison.METHODS.size() == 10
                        && new HashSet<>(AlgorithmComparison.METHODS).size() == 10,
                "Ten distinct algorithms, excluding Exact and duplicate Target sampler");
        Optimizer optimizer = new Optimizer();
        for (SearchMethod method : AlgorithmComparison.METHODS) {
            List<SearchEvent> events = new ArrayList<>();
            SearchResult a = optimizer.run(e, method, config, null, () -> false, events::add);
            SearchResult b = optimizer.run(e, method, config, null, () -> false);
            check(a.evaluations() == config.budget(), "Equal full evaluation cap: " + method);
            check(
                    a.best() != null && e.evaluate(a.best()).feasible(),
                    "All hard constraints: " + method);
            check(
                    a.evaluation().score() == b.evaluation().score()
                            && a.trace().equals(b.trace())
                            && Arrays.equals(a.best().copy(), b.best().copy())
                            && a.unique() == b.unique()
                            && a.constructions() == b.constructions(),
                    "Reproducible with/without events: " + method);
            check(
                    a.unique() <= a.evaluations() && a.feasible() <= a.evaluations(),
                    "Accounting bounds: " + method);
            double previous = Double.POSITIVE_INFINITY;
            for (var point : a.trace()) {
                check(
                        point.score() <= previous && point.evaluations() <= a.evaluations(),
                        "Monotone real incumbent trace: " + method);
                previous = point.score();
            }
            for (var event : events)
                if (event.bestEvaluation() != null)
                    check(
                            e.evaluate(event.best()).feasible(),
                            "Recorded incumbent always feasible: " + method);
            check(
                    optimizer.run(e, method, config, null, () -> true).evaluations() == 0,
                    "Cancellation before search consumes no evaluations: " + method);
            SearchResult one =
                    optimizer.run(
                            e, method, new SearchConfig(10, 1, 1, 0, .35, .8), null, () -> false);
            check(
                    one.evaluations() == 1,
                    "Budget one is respected during initialization: " + method);
        }
        Evaluator tiny = new Evaluator(SelfTest.toy(), 1, Evaluator.Objective.LATENCY, .75);
        var study =
                AlgorithmComparison.run(
                        tiny,
                        new SearchConfig(20, 200, 50, 3, .35, 100),
                        2,
                        null,
                        null,
                        () -> false);
        check(
                AlgorithmComparison.complete(study) && study.trials().size() == 20,
                "Paired protocol records all ten × two runs");
        check(study.exact().provenOptimal(8), "Exact reference is separate and complete");
        for (var trial : study.trials())
            check(
                    trial.result().evaluations() == 200 && trial.result().config().patience() == 0,
                    "Comparison overrides early stopping, including easy targets");
        var summary = AlgorithmComparison.ranking(study);
        check(
                summary.size() == 10
                        && summary.stream().allMatch(s -> s.runs() == 2 && s.feasibleRuns() == 2),
                "Ranking includes every seed and feasible-run denominator");
        AtomicInteger calls = new AtomicInteger();
        var cancelled =
                AlgorithmComparison.run(
                        tiny, config, 2, null, null, () -> calls.incrementAndGet() > 20);
        check(!AlgorithmComparison.complete(cancelled), "Cancelled study cannot claim completion");
        Path temp = Files.createTempDirectory("ten-algorithm-test-");
        try {
            AlgorithmComparison.write(temp, study);
            check(
                    Files.readAllLines(temp.resolve("ranking.csv")).size() == 11
                            && Files.readAllLines(temp.resolve("trials.csv")).size() == 21,
                    "Ranking/raw exports include all methods and seeds");
            check(
                    Files.exists(temp.resolve("charts/paired.png"))
                            && Files.readString(temp.resolve("COMPARISON.md"))
                                    .contains("Patience and target stopping disabled"),
                    "Charts and protocol are exported");
            check(
                    CsvFiles.read(temp.resolve("inputs")).services().equals(tiny.data.services()),
                    "Comparison exports actual input snapshot");
            SwingUtilities.invokeAndWait(
                    () -> {
                        PortfolioPanel panel = new PortfolioPanel(() -> {});
                        panel.show(study, temp);
                        check(
                                panel.statistics.getRowCount() == 10
                                        && panel.selectedSeed.getItemCount() == 2,
                                "Dashboard shows ten methods and two paired seeds");
                        panel.selected.setSelectedItem(SearchMethod.GENETIC);
                        panel.selectedSeed.setSelectedItem(21L);
                        check(
                                panel.paired.selected == SearchMethod.GENETIC
                                        && panel.paired.selectedSeed == 21,
                                "Pulse selection uses actual chosen algorithm and seed");
                        panel.pulseTimer.stop();
                    });
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
        // Exercise the actual assessment worker, data snapshot, controls and saved records.
        AtomicReference<AssessmentDashboard> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    AssessmentDashboard d = new AssessmentDashboard();
                    view.set(d);
                    d.portfolio.seeds.setValue(1);
                    d.portfolio.evaluations.setValue(100);
                    d.compareTen.doClick();
                    check(
                            d.busy
                                    && !d.compareTen.isEnabled()
                                    && !d.start.isEnabled()
                                    && d.stop.isEnabled(),
                            "Ten-method worker gates conflicting actions");
                });
        boolean[] busy = {true};
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (busy[0] && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> busy[0] = view.get().busy);
            if (busy[0]) Thread.sleep(20);
        }
        check(!busy[0], "Ten-method SwingWorker completes");
        SwingUtilities.invokeAndWait(
                () -> {
                    var d = view.get();
                    check(
                            d.errorMessage == null
                                    && d.portfolio.study != null
                                    && d.portfolio.study.trials().size() == 10,
                            "Ten-method GUI receives all actual trial results");
                    check(
                            d.portfolio.run.isEnabled()
                                    && d.compareTen.isEnabled()
                                    && d.exact.provenOptimal(16_777_216),
                            "Ten-method GUI restores controls and retains exact reference");
                    d.portfolio.pulseTimer.stop();
                    d.motion.stop();
                    d.replay.pause();
                });
    }
}
