package optimizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A separate, equal-budget protocol. The original four-method study remains unchanged. */
public final class AlgorithmComparison {
    public static final String PROTOCOL = "ten_equal_budget";
    public static final List<SearchMethod> METHODS =
            List.of(
                    SearchMethod.RANDOM,
                    SearchMethod.CONSTRUCTIVE,
                    SearchMethod.HILL_CLIMB,
                    SearchMethod.HYBRID,
                    SearchMethod.ANNEALING,
                    SearchMethod.TABU,
                    SearchMethod.GENETIC,
                    SearchMethod.ITERATED,
                    SearchMethod.VNS,
                    SearchMethod.BEAM);

    public static String description(SearchMethod method) {
        return switch (method) {
            case RANDOM -> "Uniform complete assignments; no construction or local improvement.";
            case CONSTRUCTIVE -> "Capacity/GPU/SLA-aware randomized greedy starts; no descent.";
            case HILL_CLIMB ->
                    "Randomized first improving single-service move; restart at a local minimum.";
            case HYBRID ->
                    "SRS / GRASP: greedy randomized starts, steepest moves + swaps, restart.";
            case ANNEALING ->
                    "Random move/swap; Metropolis acceptance, geometric cooling, reheat/restart.";
            case TABU ->
                    "Best admissible move/swap, even if worse; 12-state memory with aspiration.";
            case GENETIC ->
                    "32 individuals, tournament of 3, uniform crossover, per-gene mutation 1/n.";
            case ITERATED -> "Steepest move/swap descent, 2-edit perturbation, improve or restart.";
            case VNS ->
                    "Steepest descent with 1/2/3-edit shaking; reset strength after improvement.";
            case BEAM ->
                    "Expand move/swap neighbors; retain best 8 distinct unvisited states per"
                            + " layer.";
            default -> "Exact enumeration is an external reference; Target is an early-stop rule.";
        };
    }

    public static ExperimentRunner.Study run(
            Evaluator e,
            SearchConfig settings,
            int seeds,
            Consumer<String> progress,
            Consumer<ExperimentRunner.Study> partial,
            BooleanSupplier cancel) {
        if (seeds < 1 || seeds > 100) throw new IllegalArgumentException("Use 1 to 100 seeds");
        if (settings.seed() > Long.MAX_VALUE - (seeds - 1L))
            throw new IllegalArgumentException("Seed range overflows");
        Optimizer optimizer = new Optimizer();
        List<ExperimentRunner.Trial> trials = new ArrayList<>();
        say(
                progress,
                "Exact reference: enumerate all " + e.data.searchSpace() + " assignments once.");
        SearchResult exact = optimizer.run(e, SearchMethod.EXACT, settings, null, cancel);
        if (!exact.provenOptimal(e.data.searchSpace()))
            return new ExperimentRunner.Study(e, exact, trials, seeds, settings.budget());
        // Unrecorded, identical warmup caps. Timings remain local JVM measurements, not benchmarks.
        say(progress, "Warming all ten implementations (excluded from recorded trials)...");
        for (SearchMethod method : METHODS) {
            if (cancel.getAsBoolean()) break;
            optimizer.run(
                    e,
                    method,
                    fixed(settings, settings.seed(), Math.min(1000, settings.budget())),
                    null,
                    cancel);
        }
        for (int i = 0; i < seeds && !cancel.getAsBoolean(); i++) {
            long seed = settings.seed() + i;
            for (int j = 0; j < METHODS.size() && !cancel.getAsBoolean(); j++) {
                SearchMethod method = METHODS.get((i + j) % METHODS.size());
                say(
                        progress,
                        "Run "
                                + (trials.size() + 1)
                                + "/"
                                + (seeds * METHODS.size())
                                + " / seed "
                                + seed
                                + " / "
                                + method.shortName());
                SearchResult result =
                        optimizer.run(
                                e, method, fixed(settings, seed, settings.budget()), null, cancel);
                trials.add(new ExperimentRunner.Trial(seed, method, result, PROTOCOL));
            }
            if (partial != null)
                partial.accept(
                        new ExperimentRunner.Study(e, exact, trials, seeds, settings.budget()));
        }
        return new ExperimentRunner.Study(e, exact, trials, seeds, settings.budget());
    }

    private static SearchConfig fixed(SearchConfig base, long seed, int budget) {
        return new SearchConfig(
                seed,
                budget,
                Math.min(base.batchSize(), budget),
                0,
                base.greediness(),
                base.target());
    }

    private static void say(Consumer<String> sink, String message) {
        if (sink != null) sink.accept(message);
    }

    public static ExperimentRunner.Summary summary(
            ExperimentRunner.Study study, SearchMethod method) {
        return ExperimentRunner.summarize(study, method, PROTOCOL);
    }

    public static List<ExperimentRunner.Summary> ranking(ExperimentRunner.Study study) {
        List<ExperimentRunner.Summary> rows = new ArrayList<>();
        for (SearchMethod method : METHODS) rows.add(summary(study, method));
        // Feasible-run coverage first; an algorithm with failed runs cannot win via a lucky best.
        rows.sort(
                Comparator.comparingInt(ExperimentRunner.Summary::feasibleRuns)
                        .reversed()
                        .thenComparingDouble(
                                s ->
                                        Double.isFinite(s.mean())
                                                ? s.mean()
                                                : Double.POSITIVE_INFINITY)
                        .thenComparing(s -> s.method().ordinal()));
        return rows;
    }

    public static boolean complete(ExperimentRunner.Study study) {
        if (study.trials().size() != study.seeds() * METHODS.size()) return false;
        for (ExperimentRunner.Trial t : study.trials())
            if (t.result().evaluations() != study.equalBudget()
                    || t.result().stopReason().contains("Cancelled")) return false;
        return true;
    }

    public static ExperimentRunner.Trial trial(
            ExperimentRunner.Study study, SearchMethod method, long seed) {
        for (ExperimentRunner.Trial t : study.trials())
            if (t.method() == method && t.seed() == seed) return t;
        return null;
    }

    public static void write(Path folder, ExperimentRunner.Study study) throws Exception {
        Files.createDirectories(folder);
        CsvFiles.writeData(folder.resolve("inputs"), study.evaluator().data);
        CsvFiles.export(folder.resolve("exact-reference"), study.evaluator(), study.exact());
        List<String> rows = new ArrayList<>();
        rows.add(
                "seed,method,best_score,feasible,evaluations,unique,runtime_ms,gap_percent,stop_reason,makespan_s,cost_usd,energy_wh,mean_network_ms,worst_network_ms,sla_violations");
        for (ExperimentRunner.Trial t : study.trials()) {
            SearchResult r = t.result();
            Evaluation v = r.evaluation();
            rows.add(
                    CsvFiles.row(
                            t.seed(),
                            t.method().name(),
                            v == null ? "NA" : v.score(),
                            v != null,
                            r.evaluations(),
                            r.unique(),
                            r.milliseconds(),
                            ExperimentRunner.gap(
                                    v == null ? Double.NaN : v.score(),
                                    study.exact(),
                                    study.evaluator().data.searchSpace()),
                            r.stopReason(),
                            v == null ? "NA" : v.makespanSeconds(),
                            v == null ? "NA" : v.cost(),
                            v == null ? "NA" : v.energyWh(),
                            v == null ? "NA" : v.delayMs(),
                            v == null ? "NA" : v.worstLatencyMs(),
                            v == null ? "NA" : v.slaViolations()));
            CsvFiles.export(
                    folder.resolve("runs").resolve(t.seed() + "-" + t.method().name()),
                    study.evaluator(),
                    r);
        }
        Files.write(folder.resolve("trials.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add(
                "rank,method,planned_seeds,recorded_runs,feasible_runs,best,mean,median,worst,sample_sd,mean_gap_percent,optimum_hit_percent,target_hit_percent,mean_evaluations,mean_unique,mean_runtime_ms");
        int rank = 0;
        for (ExperimentRunner.Summary s : ranking(study))
            rows.add(
                    CsvFiles.row(
                            ++rank,
                            s.method().name(),
                            study.seeds(),
                            s.runs(),
                            s.feasibleRuns(),
                            s.best(),
                            s.mean(),
                            s.median(),
                            s.worst(),
                            s.sd(),
                            ExperimentRunner.gap(
                                    s.mean(), study.exact(), study.evaluator().data.searchSpace()),
                            s.optimumRate(),
                            s.successRate(),
                            s.evaluations(),
                            s.unique(),
                            s.runtime()));
        Files.write(folder.resolve("ranking.csv"), rows, StandardCharsets.UTF_8);
        PortfolioCharts.export(folder.resolve("charts"), study);
        Files.writeString(folder.resolve("COMPARISON.md"), report(study), StandardCharsets.UTF_8);
    }

    public static String report(ExperimentRunner.Study study) {
        StringBuilder out = new StringBuilder("# Ten-algorithm comparison\n\n");
        out.append("Implementation: ").append(Optimizer.VERSION).append(". ");
        if (!study.trials().isEmpty()) {
            var config = study.trials().get(0).result().config();
            long first =
                    study.trials().stream().mapToLong(ExperimentRunner.Trial::seed).min().orElse(0);
            out.append("Seed range: ")
                    .append(first)
                    .append("–")
                    .append(first + study.seeds() - 1L)
                    .append(". Constructor restriction: ")
                    .append(config.greediness())
                    .append(". ");
        }
        out.append("\n\n");
        out.append("Status: ")
                .append(complete(study) ? "complete" : "PARTIAL / ranking provisional")
                .append(". Same input CSVs, evaluator, hard rules and objective: ")
                .append(study.evaluator().objective)
                .append(". ")
                .append(study.seeds())
                .append(" paired seeds; ")
                .append(study.equalBudget())
                .append(" complete evaluations per run. Patience and target stopping disabled.\n\n")
                .append(
                        "Rank by feasible-run coverage, then mean feasible score (lower is better)."
                            + " Failed runs are shown explicitly; means exclude failures. Equal"
                            + " scores share quality; row order breaks exact ties. This is one"
                            + " instance and one set of parameters, not a universal ranking or a"
                            + " statistical significance claim.\n\n")
                .append(
                        "Exact is an additional reference, outside the ten approximate methods."
                                + " Exact completed: ")
                .append(study.exact().completeEnumeration(study.evaluator().data.searchSpace()))
                .append("; score: ")
                .append(
                        study.exact().evaluation() == null
                                ? "NA"
                                : study.exact().evaluation().score())
                .append(
                        ". Its solution is never supplied to a heuristic; it is used only for"
                                + " reporting gaps/hits.\n\n")
                .append(
                        "| Rank | Algorithm | Feasible / planned | Mean | SD | Best | Mean gap % |"
                                + " Exact hits % | Mean ms |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int rank = 0;
        for (ExperimentRunner.Summary s : ranking(study))
            out.append(
                    String.format(
                            Locale.ROOT,
                            "| %d | %s | %d/%d | %.9f | %.9f | %.9f | %.4f | %.1f | %.2f |%n",
                            ++rank,
                            s.method(),
                            s.feasibleRuns(),
                            study.seeds(),
                            s.mean(),
                            s.sd(),
                            s.best(),
                            ExperimentRunner.gap(
                                    s.mean(), study.exact(), study.evaluator().data.searchSpace()),
                            s.optimumRate(),
                            s.runtime()));
        out.append("\n## Reproduce and interpret\n\n")
                .append(
                        "Every invalid or repeated complete proposal counts. Feasibility checks"
                            + " during partial construction are heuristic work, not full candidate"
                            + " evaluations; construction attempts/failures are recorded per run."
                            + " No repair or local-search evaluation is free. Stored"
                            + " population/neighborhood scores may be compared without"
                            + " re-evaluation.\n\n")
                .append(
                        "Each method gets the same seed identifiers (different methods consume"
                            + " random numbers differently), input, and full evaluation cap."
                            + " Execution order rotates by seed. Each implementation first receives"
                            + " an unrecorded warmup of up to 1000 evaluations. Timing includes"
                            + " construction/search overhead, excludes file export/display, and is"
                            + " local millisecond JVM timing. Equal evaluations do not mean equal"
                            + " runtime.\n\n")
                .append(
                        "Target Search is kept in the original four-method study. It uses Random"
                                + " Search with a threshold stop, so including both with disabled"
                                + " stopping would duplicate an algorithm here. The ten methods are"
                                + " simplified teaching implementations, not tuned production"
                                + " libraries.\n\n")
                .append("## Implementations and fixed parameters\n\n")
                .append(
                        "All except uniform Random use the existing randomized capacity-aware"
                            + " constructor: restriction coefficient 0.35 by default, unrestricted"
                            + " eligible choices every fifth attempt. No known answer is"
                            + " hard-coded.\n\n");
        for (SearchMethod method : METHODS)
            out.append("- **")
                    .append(method)
                    .append(":** ")
                    .append(description(method))
                    .append('\n');
        out.append(
                        "\n"
                            + "Annealing: T0=0.05*max(1,|initial score|), cooling to T0*0.001 over"
                            + " 2000 proposals, then restart. Tabu: stop/restart after 20"
                            + " non-improving neighborhoods; tabu aspiration beats the"
                            + " pre-evaluation global best. Genetic: replace worst with each"
                            + " feasible child, fresh constructed individual every 100 offspring;"
                            + " retain global best externally. ILS: restart after 8 unsuccessful"
                            + " perturbations. VNS: 12 unsuccessful shakes before restart."
                            + " Perturbation tries up to 20 complete proposals before moving on."
                            + " Beam: width 8, up to 20 layers per restart, no revisiting retained"
                            + " states within a restart.\n\n")
                .append(
                        "J/latency never includes a penalty for invalid assignments: infeasible"
                            + " candidates cannot become the reported best. The exact optimum is"
                            + " instance-specific. Algorithm names describe these explicit"
                            + " variants; other parameter choices can change the ranking.\n");
        out.append("\n## Background references\n\n")
                .append(
                        "These references explain the algorithm families; this project's Java"
                            + " variants and parameters are stated above. No external algorithm"
                            + " library is bundled.\n\n")
                .append(
                        "- [Feo & Resende, Greedy Randomized Adaptive Search Procedures"
                            + " (1995)](https://mauricio.resende.info/doc/gtut.pdf): randomized"
                            + " construction followed by local improvement.\n")
                .append(
                        "- [Kirkpatrick, Gelatt & Vecchi, Optimization by Simulated Annealing"
                            + " (1983)](https://doi.org/10.1126/science.220.4598.671): annealing"
                            + " framework.\n")
                .append(
                        "- [Glover, Tabu Search—Part I"
                            + " (1989)](https://doi.org/10.1287/ijoc.1.3.190): memory and"
                            + " aspiration.\n")
                .append(
                        "- [Lourenço, Martin & Stützle, Iterated Local"
                            + " Search](https://arxiv.org/abs/math/0102188): perturbation and local"
                            + " search.\n")
                .append(
                        "- [Mladenović & Hansen, Variable neighborhood search"
                            + " (1997)](https://researchrepository.mi.sanu.ac.rs/handle/123456789/2568):"
                            + " systematic neighborhood changes.\n")
                .append(
                        "- [Norvig, PAIP Chapter"
                            + " 6](https://github.com/norvig/paip-lisp/blob/main/docs/chapter6.md):"
                            + " heuristic search and beam search.\n");
        return out.toString();
    }
}
