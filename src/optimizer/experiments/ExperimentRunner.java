package optimizer.experiments;

import optimizer.brain.*;
import optimizer.ui.*;

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
        SearchMethod.EXACT, SearchMethod.RANDOM, SearchMethod.LOCAL_SEARCH
    };

    /**
     * One run under a given seed and optimization strategy, grouped into a protocol-specific study.
     */
    public record Trial(long seed, SearchMethod method, SearchResult result, String protocol) {
        public Trial(long seed, SearchMethod method, SearchResult result) {
            this(seed, method, result, "main");
        }
    }

    /**
     * A complete study containing one exact reference run and all strategy trials sampled across seeds.
     */
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

    /**
     * Aggregate statistics for a single method under a particular protocol.
     */
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
        if (seeds < 1) throw new IllegalArgumentException("Seed count must be positive");
        for (int i = 0; i < seeds && !cancel.getAsBoolean(); i++) {
            long seed = settings.seed() + i;
            SearchMethod[] methods = i % 2 == 0
                    ? new SearchMethod[] {SearchMethod.LOCAL_SEARCH, SearchMethod.RANDOM}
                    : new SearchMethod[] {SearchMethod.RANDOM, SearchMethod.LOCAL_SEARCH};
            for (SearchMethod method : methods) {
                if (cancel.getAsBoolean()) break;
                say(progress, "Seed " + seed + " / " + method + " / budget " + settings.budget());
                SearchConfig config = new SearchConfig(seed, settings.budget(),
                        settings.batchSize(), 0, settings.greediness());
                trials.add(new Trial(seed, method, optimizer.run(e, method, config, null, cancel), "main"));
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
        int valid = 0, optimumHits = 0;
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
                count == 0 ? 0 : 100.0 * valid / count,
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
                "protocol,seed,method,objective,score_unit,best_score,feasible,result_valid,evaluations,unique,unique_space_percent,runtime_ms,proven_optimal,stop_reason");
        int index = 0;
        for (Trial trial : study.trials()) {
            SearchResult result = trial.result();
            Evaluation value = result.evaluation();
            rows.add(
                    CsvFiles.row(
                            trial.protocol(),
                            trial.method() == SearchMethod.EXACT ? "N/A" : trial.seed(),
                            trial.method(),
                            "ASSESSMENT",
                            ResultComparison.unit(study.evaluator()),
                            value == null ? "NA" : value.score(),
                            value != null,
                            value != null && value.feasible(),
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
                "protocol,method,objective,score_unit,runs,feasible_runs,best,mean,median,worst,sample_sd,feasible_percent,optimum_hit_percent,mean_evaluations,evaluation_space_percent,mean_unique,unique_space_percent,mean_runtime_ms,best_gap_percent,confidence");
        for (String protocol : new String[] {"main"}) {
            for (SearchMethod method : MAIN_METHODS) {
                Summary s = summarize(study, method, protocol);
                if (s.runs() == 0) continue;
                rows.add(
                        CsvFiles.row(
                                protocol,
                                method,
                                "ASSESSMENT",
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
        AssessmentExperiments.writeTrialMetrics(directory, study);
    }
}
