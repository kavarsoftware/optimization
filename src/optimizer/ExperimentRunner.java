package optimizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Three named protocols keep early stopping separate from equal-budget evidence. */
public final class ExperimentRunner {
    public static final SearchMethod[] MAIN_METHODS = {
        SearchMethod.EXACT, SearchMethod.RANDOM, SearchMethod.TARGET, SearchMethod.HYBRID
    };

    public record Trial(long seed, SearchMethod method, SearchResult result, String protocol) {
        public Trial(long seed, SearchMethod method, SearchResult result) {
            this(seed, method, result, "main");
        }
    }

    public record Study(
            Evaluator evaluator,
            SearchResult exact,
            List<Trial> trials,
            int seeds,
            int equalBudget) {
        public Study {
            trials = List.copyOf(trials);
        }
    }

    public record Summary(
            SearchMethod method,
            String protocol,
            int runs,
            int feasibleRuns,
            double best,
            double mean,
            double median,
            double worst,
            double sd,
            double successRate,
            double optimumRate,
            double evaluations,
            double unique,
            double runtime) {}

    public static Study study(
            Evaluator e,
            SearchConfig settings,
            int seeds,
            int equalBudget,
            Consumer<String> progress,
            BooleanSupplier cancel) {
        List<Trial> trials = new ArrayList<>();
        Optimizer optimizer = new Optimizer();
        say(progress, "Exact Search: enumerating every assignment...");
        SearchResult exact =
                optimizer.run(
                        e,
                        SearchMethod.EXACT,
                        settings,
                        p -> {
                            if (p.evaluations() % 1_000_000 == 0)
                                say(
                                        progress,
                                        "Exact Search: "
                                                + p.evaluations()
                                                + " assignments checked");
                        },
                        cancel);
        trials.add(new Trial(0, SearchMethod.EXACT, exact, "main"));
        if (!exact.completeEnumeration(e.data.searchSpace()))
            return new Study(e, exact, trials, seeds, equalBudget);
        SearchMethod[] randomMethods = {
            SearchMethod.RANDOM, SearchMethod.TARGET, SearchMethod.HYBRID
        };
        for (int i = 0; i < seeds && !cancel.getAsBoolean(); i++) {
            long seed = settings.seed() + i;
            SearchResult stoppedSrs = null;
            // Rotate the order to reduce the advantage of always running after JVM warmup.
            for (int j = 0; j < randomMethods.length && !cancel.getAsBoolean(); j++) {
                SearchMethod method = randomMethods[(i + j) % randomMethods.length];
                int budget =
                        method == SearchMethod.RANDOM
                                ? 100_000
                                : method == SearchMethod.TARGET ? 500_000 : settings.budget();
                int patience = method == SearchMethod.HYBRID ? settings.patience() : 0;
                say(progress, "Seed " + seed + " / " + method + " / main protocol");
                SearchConfig config =
                        new SearchConfig(
                                seed,
                                budget,
                                settings.batchSize(),
                                patience,
                                settings.greediness(),
                                settings.target());
                SearchResult result = optimizer.run(e, method, config, null, cancel);
                trials.add(new Trial(seed, method, result, "main"));
                if (method == SearchMethod.HYBRID) stoppedSrs = result;
            }
            if (cancel.getAsBoolean()) break;
            for (SearchMethod method :
                    new SearchMethod[] {SearchMethod.RANDOM, SearchMethod.HYBRID}) {
                say(progress, "Seed " + seed + " / " + method + " / equal budget " + equalBudget);
                SearchConfig config =
                        new SearchConfig(
                                seed,
                                equalBudget,
                                settings.batchSize(),
                                0,
                                settings.greediness(),
                                settings.target());
                trials.add(
                        new Trial(
                                seed,
                                method,
                                optimizer.run(e, method, config, null, cancel),
                                "equal_budget"));
                if (cancel.getAsBoolean()) break;
            }
            if (!cancel.getAsBoolean() && stoppedSrs != null && stoppedSrs.evaluations() > 0) {
                say(progress, "Seed " + seed + " / Random / budget matched to SRS stop");
                SearchConfig matched =
                        new SearchConfig(
                                seed,
                                stoppedSrs.evaluations(),
                                settings.batchSize(),
                                0,
                                settings.greediness(),
                                settings.target());
                trials.add(
                        new Trial(
                                seed,
                                SearchMethod.RANDOM,
                                optimizer.run(e, SearchMethod.RANDOM, matched, null, cancel),
                                "srs_stop_budget"));
            }
        }
        return new Study(e, exact, trials, seeds, equalBudget);
    }

    private static void say(Consumer<String> progress, String message) {
        if (progress != null) progress.accept(message);
    }

    public static List<Trial> select(List<Trial> trials, SearchMethod method, String protocol) {
        List<Trial> selected = new ArrayList<>();
        for (Trial trial : trials)
            if (trial.method() == method && trial.protocol().equals(protocol)) selected.add(trial);
        return selected;
    }

    public static Summary summarize(Study study, SearchMethod method, String protocol) {
        List<Trial> selected = select(study.trials(), method, protocol);
        double[] values = new double[selected.size()];
        int valid = 0, targetHits = 0, optimumHits = 0;
        double evaluations = 0, unique = 0, runtime = 0, sum = 0;
        double optimum =
                study.exact().provenOptimal(study.evaluator().data.searchSpace())
                                && study.exact().evaluation() != null
                        ? study.exact().evaluation().score()
                        : Double.NaN;
        for (Trial trial : selected) {
            SearchResult result = trial.result();
            evaluations += result.evaluations();
            unique += result.unique();
            runtime += result.milliseconds();
            Evaluation evaluation = result.evaluation();
            if (evaluation != null && evaluation.feasible()) {
                double value = evaluation.score();
                values[valid++] = value;
                sum += value;
                if (value <= result.config().target() + 1e-9) targetHits++;
                if (Double.isFinite(optimum) && Math.abs(value - optimum) < 1e-9) optimumHits++;
            }
        }
        values = Arrays.copyOf(values, valid);
        Arrays.sort(values);
        double mean = valid == 0 ? Double.NaN : sum / valid, variance = 0;
        for (double value : values) variance += (value - mean) * (value - mean);
        double median =
                valid == 0
                        ? Double.NaN
                        : valid % 2 == 1
                                ? values[valid / 2]
                                : (values[valid / 2 - 1] + values[valid / 2]) / 2;
        int count = selected.size();
        return new Summary(
                method,
                protocol,
                count,
                valid,
                valid == 0 ? Double.NaN : values[0],
                mean,
                median,
                valid == 0 ? Double.NaN : values[valid - 1],
                valid < 2 ? Double.NaN : Math.sqrt(variance / (valid - 1)),
                count == 0 ? 0 : 100.0 * targetHits / count,
                count == 0 ? 0 : 100.0 * optimumHits / count,
                count == 0 ? 0 : evaluations / count,
                count == 0 ? 0 : unique / count,
                count == 0 ? 0 : runtime / count);
    }

    public static double gap(double value, SearchResult exact, long space) {
        if (exact == null
                || !exact.provenOptimal(space)
                || exact.evaluation() == null
                || !Double.isFinite(value)) return Double.NaN;
        double optimum = exact.evaluation().score();
        if (Math.abs(optimum) < 1e-12) return Math.abs(value) < 1e-12 ? 0 : Double.NaN;
        return 100 * (value - optimum) / Math.abs(optimum);
    }

    public static String confidence(Study study, Summary summary) {
        if (summary.method() == SearchMethod.EXACT
                && study.exact().completeEnumeration(study.evaluator().data.searchSpace()))
            return study.exact().best() == null
                    ? "Proven infeasible"
                    : "Proven by complete enumeration";
        if (summary.optimumRate() > 0)
            return "Best found; matches Exact in "
                    + String.format(java.util.Locale.ROOT, "%.0f%%", summary.optimumRate())
                    + " of runs";
        return "Best found; no method guarantee";
    }

    public static void write(Path directory, Study study) throws Exception {
        Files.createDirectories(directory);
        List<String> rows = new ArrayList<>();
        rows.add(
                "protocol,seed,method,objective,score_unit,best_score,feasible,target_reached,evaluations,unique,unique_space_percent,runtime_ms,proven_optimal,stop_reason");
        int index = 0;
        for (Trial trial : study.trials()) {
            SearchResult result = trial.result();
            Evaluation value = result.evaluation();
            rows.add(
                    CsvFiles.row(
                            trial.protocol(),
                            trial.method() == SearchMethod.EXACT ? "N/A" : trial.seed(),
                            trial.method(),
                            study.evaluator().objective,
                            ResultComparison.unit(study.evaluator()),
                            value == null ? "NA" : value.score(),
                            value != null,
                            value != null && value.score() <= result.config().target() + 1e-9,
                            result.evaluations(),
                            result.unique(),
                            100.0 * result.unique() / study.evaluator().data.searchSpace(),
                            result.milliseconds(),
                            result.provenOptimal(study.evaluator().data.searchSpace()),
                            result.stopReason()));
            Path run =
                    directory
                            .resolve("runs")
                            .resolve(
                                    String.format(
                                            java.util.Locale.ROOT,
                                            "%03d-%s-%s",
                                            ++index,
                                            trial.protocol(),
                                            trial.method().name()));
            CsvFiles.export(run, study.evaluator(), result);
        }
        Files.write(directory.resolve("trials.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add(
                "protocol,method,objective,score_unit,runs,feasible_runs,best,mean,median,worst,sample_sd,target_success_percent,optimum_hit_percent,mean_evaluations,evaluation_space_percent,mean_unique,unique_space_percent,mean_runtime_ms,best_gap_percent,confidence");
        for (String protocol : new String[] {"main", "equal_budget", "srs_stop_budget"}) {
            for (SearchMethod method : MAIN_METHODS) {
                Summary s = summarize(study, method, protocol);
                if (s.runs() == 0) continue;
                rows.add(
                        CsvFiles.row(
                                protocol,
                                method,
                                study.evaluator().objective,
                                ResultComparison.unit(study.evaluator()),
                                s.runs(),
                                s.feasibleRuns(),
                                s.best(),
                                s.mean(),
                                s.median(),
                                s.worst(),
                                s.sd(),
                                s.successRate(),
                                s.optimumRate(),
                                s.evaluations(),
                                100 * s.evaluations() / study.evaluator().data.searchSpace(),
                                s.unique(),
                                100 * s.unique() / study.evaluator().data.searchSpace(),
                                s.runtime(),
                                gap(s.best(), study.exact(), study.evaluator().data.searchSpace()),
                                confidence(study, s)));
            }
        }
        Files.write(directory.resolve("summary.csv"), rows, StandardCharsets.UTF_8);
        CsvFiles.writeData(directory.resolve("inputs"), study.evaluator().data);
        StudyCharts.export(directory.resolve("charts"), study);
        if (study.evaluator().objective == Evaluator.Objective.ASSESSMENT)
            AssessmentExperiments.writeTrialMetrics(directory, study);
    }
}
