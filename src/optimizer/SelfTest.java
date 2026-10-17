package optimizer;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

/** Small manual cases and behavioral tests, runnable without JUnit or external dependencies. */
public final class SelfTest {
    private static int checks;

    static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    static void close(double a, double b, String message) {
        check(Math.abs(a - b) < 1e-8, message + ": " + a + " != " + b);
    }

    static ProblemData toy() {
        return new ProblemData(
                "manual-2x3",
                List.of(
                        new ProblemData.Server("S1", 10, 10, 100, 5, .2),
                        new ProblemData.Server("S2", 10, 10, 100, 20, .1)),
                List.of(
                        new ProblemData.Service("A", 2, 1, 10, 1),
                        new ProblemData.Service("B", 4, 2, 10, 2),
                        new ProblemData.Service("C", 1, 1, 5, 3)));
    }

    static void throwsInvalid(Runnable task) {
        boolean caught = false;
        try {
            task.run();
        } catch (IllegalArgumentException ex) {
            caught = true;
        }
        check(caught, "Invalid input rejected");
    }

    public static void run() throws Exception {
        checks = 0;
        var d = toy();
        var e = new Evaluator(d, 1, Evaluator.Objective.LATENCY, .75);
        var a = e.evaluate(new Candidate(new int[] {0, 1, 0}));
        var b = e.evaluate(new Candidate(new int[] {1, 0, 1}));
        check(a.feasible() && b.feasible(), "Manual candidates feasible");
        close(a.delayMs(), 12, "Manual A delay");
        close(a.cost(), 1, "Manual A cost");
        close(b.delayMs(), 17, "Manual B delay");
        close(b.cost(), 1.1, "Manual B cost");
        checkComparisons(e, a, b);
        check(!e.evaluate(new Candidate(new int[] {0, 0, 0})).feasible(), "Minimum service rule");
        check(!e.evaluate(new Candidate(new int[] {0, -1, 1})).feasible(), "Negative index");
        check(!e.evaluate(new Candidate(new int[] {0, 2, 1})).feasible(), "High index");
        check(!e.evaluate(new Candidate(new int[] {0})).feasible(), "Short candidate");
        check(!e.evaluate(null).feasible(), "Null candidate");
        int[] raw = {0, 1, 0};
        Candidate immutable = new Candidate(raw);
        raw[0] = 1;
        immutable.copy()[1] = 0;
        check(immutable.server(0) == 0 && immutable.server(1) == 1, "Defensive copies");
        var weighted =
                new Evaluator(d, 1, Evaluator.Objective.WEIGHTED, .75)
                        .evaluate(new Candidate(new int[] {0, 1, 0}));
        close(weighted.delayMs(), 10, "Hand-checked weighted candidate A");
        check(ProblemData.demo().searchSpace() == 16_777_216, "Search-space count");
        throwsInvalid(() -> new SearchConfig(1, 0, 10, 3, .3));
        throwsInvalid(() -> new SearchConfig(1, 100, 0, 3, .3));
        throwsInvalid(() -> new Evaluator(d, 2, Evaluator.Objective.LATENCY, .5));
        throwsInvalid(() -> new Evaluator(d, 1, Evaluator.Objective.LATENCY, Double.NaN));
        throwsInvalid(
                () ->
                        new ProblemData(
                                "bad",
                                List.of(new ProblemData.Server("s", 0, 2, 3, 4, 1)),
                                d.services()));
        var equality =
                new ProblemData(
                        "equality",
                        List.of(new ProblemData.Server("s", 7, 4, 25, 1, 1)),
                        d.services());
        check(
                new Evaluator(equality, 0, Evaluator.Objective.LATENCY, .5)
                        .evaluate(new Candidate(new int[] {0, 0, 0}))
                        .feasible(),
                "Capacity equality accepted");
        var over =
                new ProblemData(
                        "overload",
                        List.of(new ProblemData.Server("s", 6, 3, 24, 1, 1)),
                        d.services());
        var bad =
                new Evaluator(over, 0, Evaluator.Objective.LATENCY, .5)
                        .evaluate(new Candidate(new int[] {0, 0, 0}));
        check(
                !bad.feasible() && bad.violations().size() == 3,
                "Three resource constraints independently checked");
        var opt = new Optimizer();
        checkExactAndTarget(d, opt);
        for (var method :
                new SearchMethod[] {
                    SearchMethod.HYBRID, SearchMethod.CONSTRUCTIVE, SearchMethod.RANDOM
                }) {
            var c = new SearchConfig(2026, 2000, 100, 0, .35);
            var one = opt.run(e, method, c, null, () -> false);
            var two = opt.run(e, method, c, null, () -> false);
            check(one.evaluations() == 2000, "Full evaluation budget " + method);
            check(one.best() != null, "Feasible result " + method);
            check(
                    Arrays.equals(one.best().copy(), two.best().copy())
                            && one.unique() == two.unique(),
                    "Seed reproducibility " + method);
            close(one.evaluation().score(), two.evaluation().score(), "Same score");
            check(
                    one.unique() <= d.searchSpace() && one.unique() <= one.evaluations(),
                    "Distinct candidate count");
            check(
                    one.trace().get(one.trace().size() - 1).evaluations() == 2000,
                    "Final trace checkpoint");
            double prev = Double.POSITIVE_INFINITY;
            for (var point : one.trace()) {
                check(point.score() <= prev + 1e-10, "Monotone incumbent trace");
                prev = point.score();
            }
            var oneEval =
                    opt.run(e, method, new SearchConfig(1, 1, 100, 3, .35), null, () -> false);
            check(oneEval.evaluations() == 1, "Budget of one respected");
        }
        var stop =
                opt.run(
                        e,
                        SearchMethod.HYBRID,
                        new SearchConfig(2026, 10_000, 100, 2, .35),
                        null,
                        () -> false);
        check(
                stop.evaluations() < 10_000
                        && stop.evaluations() % 100 == 0
                        && stop.stopReason().contains("batches"),
                "Complete-batch early stopping");
        var cancel = opt.run(e, SearchMethod.RANDOM, SearchConfig.defaults(), null, () -> true);
        check(
                cancel.evaluations() == 0 && cancel.stopReason().contains("Cancelled"),
                "Cancellation");
        var impossible =
                new ProblemData(
                        "impossible",
                        List.of(new ProblemData.Server("tiny", 1, 1, 1, 1, 1)),
                        d.services());
        check(
                opt.run(
                                new Evaluator(impossible, 0, Evaluator.Objective.LATENCY, .5),
                                SearchMethod.HYBRID,
                                SearchConfig.defaults(),
                                null,
                                () -> false)
                        .stopReason()
                        .startsWith("Infeasible:"),
                "Necessary infeasibility detection");
        // An independent eight-candidate calculation validates the teaching case.
        double known = Double.POSITIVE_INFINITY;
        for (int code = 0; code < 8; code++)
            known =
                    Math.min(
                            known,
                            e.evaluate(
                                            new Candidate(
                                                    new int[] {
                                                        code & 1, (code >> 1) & 1, (code >> 2) & 1
                                                    }))
                                    .score());
        close(
                opt.run(
                                e,
                                SearchMethod.HYBRID,
                                new SearchConfig(7, 1000, 100, 0, .35),
                                null,
                                () -> false)
                        .evaluation()
                        .score(),
                known,
                "Known small-instance optimum");
        for (var objective :
                new Evaluator.Objective[] {
                    Evaluator.Objective.WEIGHTED,
                    Evaluator.Objective.LATENCY,
                    Evaluator.Objective.COST,
                    Evaluator.Objective.BALANCED
                }) {
            var evaluator = new Evaluator(ProblemData.demo(), 2, objective, .75);
            var r =
                    opt.run(
                            evaluator,
                            SearchMethod.HYBRID,
                            new SearchConfig(2026, 5000, 1000, 0, .35),
                            null,
                            () -> false);
            check(
                    r.best() != null && evaluator.evaluate(r.best()).feasible(),
                    "Original data feasible " + objective);
            check(
                    evaluator.optimisticBound() <= r.evaluation().score() + 1e-9,
                    "Optimistic bound " + objective);
        }
        for (var method :
                new SearchMethod[] {
                    SearchMethod.HYBRID, SearchMethod.CONSTRUCTIVE, SearchMethod.RANDOM
                }) {
            var evaluator = new Evaluator(ProblemData.demo(), 2, Evaluator.Objective.LATENCY, .75);
            var r =
                    opt.run(
                            evaluator,
                            method,
                            new SearchConfig(2026, 10000, 1000, 0, .35),
                            null,
                            () -> false);
            check(
                    r.evaluations() == 10000,
                    "Original instance uses full comparison budget " + method);
        }
        Path temp = Files.createTempDirectory("placement-test-");
        try {
            CsvFiles.writeData(temp, d);
            var loaded = CsvFiles.read(temp);
            check(
                    loaded.services().equals(d.services()) && loaded.servers().equals(d.servers()),
                    "CSV roundtrip");
        } finally {
            Files.deleteIfExists(temp.resolve("servers.csv"));
            Files.deleteIfExists(temp.resolve("services.csv"));
            Files.deleteIfExists(temp);
        }
        AtomicReference<Dashboard> view = new AtomicReference<>();
        AtomicReference<SearchResult> completed = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    Main.style();
                    Dashboard dashboard = new Dashboard();
                    dashboard.budget.setValue(2000);
                    dashboard.batch.setValue(1000);
                    dashboard.patience.setValue(0);
                    view.set(dashboard);
                    dashboard.run.doClick();
                    check(
                            dashboard.busy
                                    && !dashboard.run.isEnabled()
                                    && dashboard.stop.isEnabled(),
                            "GUI enters running state");
                });
        waitForGui(view.get());
        SwingUtilities.invokeAndWait(
                () -> {
                    var dashboard = view.get();
                    check(
                            dashboard.lastResult != null
                                    && dashboard.lastResult.evaluations() == 2000,
                            "GUI background run completes");
                    check(
                            dashboard.assignment.getRowCount() == 12
                                    && dashboard.export.isEnabled(),
                            "GUI displays assignment and enables export");
                    completed.set(dashboard.completedResult);
                    check(
                            completed.get() == dashboard.lastResult,
                            "GUI remembers completed run for next comparison");
                    dashboard.budget.setValue(1000000);
                    dashboard.run.doClick();
                    dashboard.stop.doClick();
                });
        waitForGui(view.get());
        SwingUtilities.invokeAndWait(
                () -> {
                    check(
                            view.get().lastResult.stopReason().contains("Cancelled"),
                            "GUI stop button cancels search");
                    check(
                            view.get().completedResult == completed.get(),
                            "Cancelled run preserves the previous completed result");
                });
        var replayEvaluator =
                new Evaluator(ProblemData.demo(), 2, Evaluator.Objective.LATENCY, .75);
        List<SearchEvent> recording = new ArrayList<>();
        var plain =
                opt.run(
                        replayEvaluator,
                        SearchMethod.HYBRID,
                        SearchConfig.defaults(),
                        null,
                        () -> false);
        var recorded =
                opt.run(
                        replayEvaluator,
                        SearchMethod.HYBRID,
                        SearchConfig.defaults(),
                        null,
                        () -> false,
                        recording::add);
        check(
                Arrays.equals(plain.best().copy(), recorded.best().copy()),
                "Recording preserves assignment");
        check(
                plain.evaluations() == recorded.evaluations()
                        && plain.unique() == recorded.unique()
                        && plain.feasible() == recorded.feasible(),
                "Recording preserves evaluation accounting");
        check(plain.trace().equals(recorded.trace()), "Recording preserves convergence trace");
        check(recording.size() > 10 && recording.size() <= 512, "Recording bounded and populated");
        check(
                hasEvent(recording, SearchEvent.Type.MOVE)
                        && hasEvent(recording, SearchEvent.Type.SWAP),
                "Both trial types recorded");
        check(hasEvent(recording, SearchEvent.Type.ACCEPT), "Accepted improvement recorded");
        check(
                recording.get(recording.size() - 1).type() == SearchEvent.Type.FINISH,
                "Final search state recorded");
        int previous = 0, sequence = 0;
        double best = Double.POSITIVE_INFINITY;
        for (var event : recording) {
            check(
                    event.sequence() == ++sequence && event.evaluations() >= previous,
                    "Chronological recording");
            previous = event.evaluations();
            if (event.candidate() != null) {
                var actual = replayEvaluator.evaluate(event.candidate());
                check(
                        actual.feasible() == event.evaluation().feasible(),
                        "Recorded proposal feasibility matches evaluator");
                if (actual.feasible())
                    close(
                            actual.score(),
                            event.evaluation().score(),
                            "Recorded objective matches evaluator");
            }
            if (event.bestEvaluation() != null) {
                check(
                        event.bestEvaluation().feasible()
                                && event.bestEvaluation().score() <= best + 1e-10,
                        "Replay best is feasible and monotone");
                best = event.bestEvaluation().score();
            }
        }
        SwingUtilities.invokeAndWait(
                () -> {
                    var panel = view.get().replay;
                    panel.load(replayEvaluator, recorded, recording, false);
                    check(!panel.isPlaying() && panel.frameIndex() == 0, "Replay loads paused");
                    panel.play.doClick();
                    check(panel.isPlaying(), "Play control starts replay");
                    panel.play.doClick();
                    check(!panel.isPlaying(), "Pause control pauses replay");
                    panel.next.doClick();
                    check(
                            panel.frameIndex() == 1 && !panel.isPlaying(),
                            "Step advances one recorded decision");
                    panel.pause();
                    panel.speed.setSelectedIndex(3);
                    panel.seek(recording.size() - 1);
                    check(
                            panel.frameIndex() == recording.size() - 1,
                            "Seek reaches final recorded state");
                    panel.back.doClick();
                    check(
                            panel.frameIndex() == recording.size() - 2,
                            "Back control seeks one decision");
                    panel.pause();
                    int accepted = 0, invalid = 0;
                    for (int i = 0; i < recording.size(); i++) {
                        if (recording.get(i).type() == SearchEvent.Type.ACCEPT) accepted = i;
                        if (recording.get(i).evaluation() != null
                                && !recording.get(i).evaluation().feasible()) invalid = i;
                    }
                    panel.seek(accepted);
                    check(
                            panel.canvas.comparison.value().outcome()
                                    == ResultComparison.Outcome.BETTER,
                            "Accepted move shows improved before/after scores");
                    close(
                            panel.canvas.comparison.value().improvement(),
                            recording.get(accepted).beforeEvaluation().score()
                                    - recording.get(accepted).evaluation().score(),
                            "Replay comparison uses the actual starting placement");
                    check(panel.canvas.isComparisonPulsing(), "Improvement starts a pulse");
                    panel.canvas.advanceComparison(1000);
                    check(
                            !panel.canvas.isComparisonPulsing(),
                            "Pulse ends after its bounded duration");
                    panel.seek(invalid);
                    check(
                            panel.canvas.comparison.value().outcome()
                                    == ResultComparison.Outcome.INFEASIBLE,
                            "Invalid proposal shows rejection instead of improvement");
                    panel.setPreviousRun(null, null);
                    panel.comparisonMode.setSelectedIndex(1);
                    check(
                            panel.canvas.comparison.value().outcome()
                                    == ResultComparison.Outcome.FIRST,
                            "First run does not invent an earlier score");
                    panel.setPreviousRun(replayEvaluator, plain);
                    panel.comparisonMode.setSelectedIndex(0);
                    panel.comparisonMode.setSelectedIndex(1);
                    check(
                            panel.canvas.comparison.value().outcome()
                                    == ResultComparison.Outcome.SAME,
                            "Previous-run mode compares completed results even during replay");
                    close(
                            panel.canvas.comparison.value().current().value(),
                            recorded.evaluation().score(),
                            "Run comparison uses final score");
                    panel.comparisonMode.setSelectedIndex(0);
                    panel.pause();
                });
        SwingUtilities.invokeAndWait(
                () -> {
                    Dashboard dashboard = view.get();
                    dashboard.runs.setValue(2);
                    dashboard.experimentBudget.setValue(1000);
                    dashboard.budget.setValue(2000);
                    dashboard.patience.setValue(1);
                    dashboard.compare.doClick();
                    check(
                            dashboard.busy && dashboard.stopStudy.isEnabled(),
                            "GUI study runs in a cancellable worker");
                });
        waitForGui(view.get());
        SwingUtilities.invokeAndWait(
                () -> {
                    Dashboard dashboard = view.get();
                    check(
                            dashboard.study != null && dashboard.trials.size() == 13,
                            "GUI study preserves exact once plus all paired protocols");
                    check(
                            dashboard.qualityModel.getRowCount() == 4
                                    && dashboard.statisticsModel.getRowCount() == 4,
                            "GUI shows four main methods");
                    check(
                            dashboard.exactResult.provenOptimal(16_777_216),
                            "GUI study keeps a complete exact reference");
                    check(
                            dashboard.exportTrials.isEnabled()
                                    && !dashboard.progress.isIndeterminate(),
                            "GUI study enables exports and finishes progress");
                    check(
                            Files.exists(dashboard.studySaved.resolve("charts/space.png")),
                            "GUI study autosaves charts and records");
                    check(
                            ((Number) dashboard.patience.getValue()).intValue() == 1,
                            "Completing a run keeps user batch settings");
                    dashboard.replay.pause();
                });
        AssessmentTest.run();
        PortfolioTest.run();
        System.out.println(
                "PASS: "
                        + checks
                        + " checks (model/search, GUI worker/stop, truthful recording, playback and"
                        + " result comparisons)");
    }

    private static boolean hasEvent(List<SearchEvent> events, SearchEvent.Type type) {
        for (SearchEvent event : events) if (event.type() == type) return true;
        return false;
    }

    private static void checkExactAndTarget(ProblemData data, Optimizer opt) throws Exception {
        Evaluator e = new Evaluator(data, 1, Evaluator.Objective.WEIGHTED, .75);
        Candidate a = new Candidate(new int[] {0, 1, 0}),
                b = new Candidate(new int[] {1, 0, 1}),
                c = new Candidate(new int[] {0, 0, 0});
        close(e.evaluate(a).score(), 10, "Weighted A = (5+40+15)/6 = 10 ms");
        close(e.evaluate(b).score(), 15, "Weighted B = (20+5+60)/6 = 15 ms");
        check(
                !e.evaluate(c).feasible(),
                "Weighted C is rejected even though its raw delay is 5 ms");
        Evaluator.Work fast = e.newWork(false);
        for (int code = 0; code < 8; code++) {
            int[] values = {code & 1, (code >> 1) & 1, (code >> 2) & 1};
            Evaluation rich = e.evaluate(new Candidate(values));
            e.measure(values, fast);
            check(fast.feasible == rich.feasible(), "Fast and detailed paths share feasibility");
            if (rich.feasible())
                close(fast.score, rich.score(), "Fast and detailed paths share score");
        }
        SearchResult exact =
                opt.run(e, SearchMethod.EXACT, SearchConfig.defaults(), null, () -> false);
        check(
                exact.evaluations() == 8 && exact.unique() == 8 && exact.feasible() == 6,
                "Exact enumerates each toy assignment once");
        close(
                exact.evaluation().score(),
                7.5,
                "Exact optimum is hand-known: give the smallest weight to S2");
        check(exact.provenOptimal(8), "Completed enumeration certifies its optimum");
        SearchResult otherSeed =
                opt.run(
                        e,
                        SearchMethod.EXACT,
                        new SearchConfig(99, 1, 10, 5, .35),
                        null,
                        () -> false);
        check(
                Arrays.equals(exact.best().copy(), otherSeed.best().copy())
                        && otherSeed.evaluations() == 8,
                "Exact ignores seed, heuristic cap and patience");
        SearchResult stopped =
                opt.run(e, SearchMethod.EXACT, SearchConfig.defaults(), null, () -> true);
        check(
                !stopped.provenOptimal(8) && !stopped.completeEnumeration(8),
                "Cancelled Exact cannot certify anything");
        SearchConfig reachable = new SearchConfig(2026, 500, 10, 1, .35, 18);
        SearchResult target = opt.run(e, SearchMethod.TARGET, reachable, null, () -> false);
        check(
                target.evaluation() != null
                        && target.evaluation().score() <= 18
                        && target.evaluations() < 500
                        && target.stopReason().startsWith("Target reached"),
                "Target stops at its first qualifying feasible sample");
        SearchResult repeat = opt.run(e, SearchMethod.TARGET, reachable, null, () -> false);
        check(
                repeat.evaluations() == target.evaluations()
                        && Arrays.equals(repeat.best().copy(), target.best().copy()),
                "Target seed reproduces its stopping point");
        SearchResult unreachable =
                opt.run(
                        e,
                        SearchMethod.TARGET,
                        new SearchConfig(2026, 500, 10, 1, .35, 0),
                        null,
                        () -> false);
        check(
                unreachable.evaluations() == 500,
                "Unreachable target uses the entire attempt cap and ignores patience");
        SearchResult random =
                opt.run(
                        e,
                        SearchMethod.RANDOM,
                        new SearchConfig(2026, 100000, 10000, 1, .35),
                        null,
                        () -> false);
        check(random.evaluations() == 100000, "Random baseline checks exactly 100,000 assignments");
        List<ExperimentRunner.Trial> trials =
                List.of(
                        new ExperimentRunner.Trial(0, SearchMethod.EXACT, exact),
                        new ExperimentRunner.Trial(2026, SearchMethod.TARGET, target),
                        new ExperimentRunner.Trial(
                                2027,
                                SearchMethod.TARGET,
                                new SearchResult(
                                        SearchMethod.TARGET,
                                        reachable,
                                        null,
                                        null,
                                        500,
                                        0,
                                        8,
                                        0,
                                        0,
                                        0,
                                        1,
                                        "Budget reached",
                                        List.of())));
        ExperimentRunner.Study study = new ExperimentRunner.Study(e, exact, trials, 1, 100);
        close(
                ExperimentRunner.summarize(study, SearchMethod.TARGET, "main").successRate(),
                50,
                "Target success denominator includes failed runs");
        close(ExperimentRunner.gap(15, exact, 8), 100, "Gap uses the exact optimum denominator");
        Path folder = Files.createTempDirectory("study-export-test-");
        try {
            ExperimentRunner.write(folder, study);
            check(
                    Files.exists(folder.resolve("charts/convergence.png"))
                            && Files.exists(folder.resolve("charts/growth.csv")),
                    "Study exports PNG and CSV charts");
            check(
                    Files.readString(folder.resolve("summary.csv")).contains("score_unit"),
                    "Summary labels score units");
            check(
                    CsvFiles.metadata(e, exact).toString().contains("N/A (deterministic)"),
                    "Exact record has no random seed");
        } finally {
            try (var walk = Files.walk(folder)) {
                List<Path> files = new ArrayList<>();
                walk.forEach(files::add);
                files.sort(java.util.Comparator.reverseOrder());
                for (Path path : files) Files.delete(path);
            }
        }
    }

    private static void checkComparisons(Evaluator model, Evaluation a, Evaluation b) {
        var better = ResultComparison.between(model, b, a, "before", "after", "");
        check(better.outcome() == ResultComparison.Outcome.BETTER, "Lower score is an improvement");
        close(better.improvement(), 5, "Manual improvement is 5 ms");
        close(better.percent(), 100. * 5 / 17, "Improvement percentage uses previous score");
        var worse = ResultComparison.between(model, a, b, "before", "after", "");
        check(worse.outcome() == ResultComparison.Outcome.WORSE, "Higher score is worse");
        close(worse.improvement(), -5, "Worse result has negative improvement");
        check(
                ResultComparison.between(model, a, a, "", "", "").outcome()
                        == ResultComparison.Outcome.SAME,
                "Equal scores show no change");
        var first = ResultComparison.between(model, null, a, "", "", "");
        check(
                first.outcome() == ResultComparison.Outcome.FIRST
                        && Double.isNaN(first.improvement()),
                "First result has no fictional improvement");
        var invalid = model.evaluate(new Candidate(new int[] {0, 0, 0}));
        var rejected = ResultComparison.between(model, a, invalid, "", "", "");
        check(
                rejected.outcome() == ResultComparison.Outcome.INFEASIBLE && !rejected.comparable(),
                "Invalid proposals cannot claim improvement");
        check(
                !ComparisonPulse.changeText(rejected).contains("Infinity"),
                "Rejected result hides infinite penalty");
        check(
                ResultComparison.between(model, a, null, "", "", "").outcome()
                        == ResultComparison.Outcome.UNAVAILABLE,
                "Missing current result is explicit");
        var zero = new Evaluation(true, 0, 0, 0, List.of(), List.of());
        var fromZero = ResultComparison.between(model, zero, a, "", "", "");
        check(
                Double.isNaN(fromZero.percent())
                        && !ComparisonPulse.changeText(fromZero).contains("NaN"),
                "Zero baseline never displays an undefined percentage");
        var renamed =
                new Evaluator(
                        new ProblemData(
                                "another-file-name", model.data.servers(), model.data.services()),
                        1,
                        Evaluator.Objective.LATENCY,
                        .25);
        check(
                ResultComparison.sameModel(model, renamed),
                "Filename and inactive priority do not invalidate comparison");
        check(
                !ResultComparison.sameModel(
                        model, new Evaluator(model.data, 0, Evaluator.Objective.LATENCY, .75)),
                "Changed constraints invalidate direct comparison");
        check(
                !ResultComparison.sameModel(
                        model, new Evaluator(model.data, 1, Evaluator.Objective.COST, .75)),
                "Changed objective invalidates direct comparison");
        var changedServers = new ArrayList<>(model.data.servers());
        var s = changedServers.get(0);
        changedServers.set(
                0,
                new ProblemData.Server(
                        s.name(),
                        s.cpu(),
                        s.ram(),
                        s.storage(),
                        s.latencyMs() + 1,
                        s.costPerCpu()));
        check(
                !ResultComparison.sameModel(
                        model,
                        new Evaluator(
                                new ProblemData("edited", changedServers, model.data.services()),
                                1,
                                Evaluator.Objective.LATENCY,
                                .75)),
                "Edited model invalidates direct comparison");
        check(
                !ResultComparison.sameModel(
                        new Evaluator(model.data, 1, Evaluator.Objective.BALANCED, .5),
                        new Evaluator(model.data, 1, Evaluator.Objective.BALANCED, .75)),
                "Changed compromise priority invalidates comparison");
        var result =
                new SearchResult(
                        SearchMethod.HYBRID,
                        SearchConfig.defaults(),
                        new Candidate(new int[] {0, 1, 0}),
                        a,
                        10,
                        2,
                        8,
                        0,
                        0,
                        0,
                        1,
                        "Budget",
                        List.of());
        var different = new Evaluator(model.data, 1, Evaluator.Objective.COST, .75);
        var comparison = ResultComparison.runs(model, result, different, result);
        check(
                comparison.outcome() == ResultComparison.Outcome.INCOMPARABLE
                        && Double.isNaN(comparison.improvement()),
                "Changed run objective suppresses numerical improvement claim");
        check(
                comparison.previous().unit().equals("ms")
                        && comparison.current().unit().equals("cost"),
                "Each run keeps its own objective units");
    }

    private static void waitForGui(Dashboard view) throws Exception {
        long end = System.nanoTime() + 30_000_000_000L;
        AtomicBoolean running = new AtomicBoolean(true);
        while (running.get() && System.nanoTime() < end) {
            SwingUtilities.invokeAndWait(() -> running.set(view.busy));
            if (running.get()) Thread.sleep(10);
        }
        check(!running.get(), "GUI worker terminates promptly");
    }
}
