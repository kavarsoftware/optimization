package optimizer.experiments;

import optimizer.brain.*;
import optimizer.ui.*;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.JPanel;

/** The PDF budget and network sensitivity protocols keep all other parameters fixed. */
public final class AssessmentExperiments {
    public record Measurement(String scenario, long seed, int budget, SearchResult result) {}

    public static ProblemData networkVariant(
            ProblemData data, double rttFactor, double bandwidthFactor) {
        List<ProblemData.Server> servers = new ArrayList<>();
        for (ProblemData.Server s : data.servers())
            servers.add(
                    new ProblemData.Server(
                            s.name(),
                            s.cpu(),
                            s.ram(),
                            s.storage(),
                            s.latencyMs() * rttFactor,
                            s.gpu(),
                            s.maxServices(),
                            s.speed(),
                            s.idlePower(),
                            s.peakPower(),
                            s.busyPrice(),
                            s.bandwidth() * bandwidthFactor));
        return new ProblemData(
                data.name() + "-network-" + rttFactor + "-" + bandwidthFactor,
                servers,
                data.services(),
                true);
    }

    public static void additional(
            Path folder,
            Evaluator model,
            SearchConfig settings,
            int seeds,
            Consumer<String> progress,
            BooleanSupplier cancel)
            throws Exception {
        long firstSeed = settings.seed();
        List<Measurement> all = new ArrayList<>();
        for (int budget : new int[] {20, 50, 100, 200})
            for (int i = 0; i < seeds && !cancel.getAsBoolean(); i++) {
                if (progress != null)
                    progress.accept(
                            "Budget sensitivity: "
                                    + budget
                                    + " evaluations / seed "
                                    + (firstSeed + i));
                SearchResult result =
                        new Optimizer()
                                .run(
                                        model,
                                        SearchMethod.LOCAL_SEARCH,
                                        new SearchConfig(firstSeed + i, budget, settings.batchSize(), 0, settings.greediness()),
                                        null,
                                        cancel);
                all.add(new Measurement("budget", firstSeed + i, budget, result));
                CsvFiles.export(
                        folder.resolve("budget-runs/" + budget + "-" + (firstSeed + i)),
                        model,
                        result);
            }
        for (String scenario :
                new String[] {"default", "RTT_plus_25_percent", "bandwidth_minus_30_percent"}) {
            ProblemData data =
                    scenario.equals("default")
                            ? model.data
                            : scenario.startsWith("RTT")
                                    ? networkVariant(model.data, 1.25, 1)
                                    : networkVariant(model.data, 1, .70);
            Evaluator e = new Evaluator(data);
            for (int i = 0; i < seeds && !cancel.getAsBoolean(); i++) {
                if (progress != null)
                    progress.accept(
                            "Network sensitivity: " + scenario + " / seed " + (firstSeed + i));
                SearchResult result =
                        new Optimizer()
                                .run(
                                        e,
                                        SearchMethod.LOCAL_SEARCH,
                                        new SearchConfig(firstSeed + i, settings.budget(), settings.batchSize(), 0, settings.greediness()),
                                        null,
                                        cancel);
                all.add(new Measurement(scenario, firstSeed + i, settings.budget(), result));
                CsvFiles.export(
                        folder.resolve("network-runs/" + scenario + "-" + (firstSeed + i)),
                        e,
                        result);
            }
        }
        Files.createDirectories(folder);
        List<String> rows = new ArrayList<>();
        rows.add(
                "scenario,seed,budget,feasible,J,M_seconds,C_usd,E_wh,L_ms,Lmax_ms,sla_violations,evaluations,search_runtime_ms");
        for (Measurement measurement : all) {
            SearchResult r = measurement.result();
            Evaluation e = r.evaluation();
            rows.add(
                    CsvFiles.row(
                            measurement.scenario(),
                            measurement.seed(),
                            measurement.budget(),
                            e != null,
                            e == null ? "NA" : e.score(),
                            e == null ? "NA" : e.makespanSeconds(),
                            e == null ? "NA" : e.cost(),
                            e == null ? "NA" : e.energyWh(),
                            e == null ? "NA" : e.delayMs(),
                            e == null ? "NA" : e.worstLatencyMs(),
                            e == null ? "NA" : e.slaViolations(),
                            r.evaluations(),
                            r.milliseconds()));
        }
        Files.write(folder.resolve("assessment-sensitivity.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add(
                "scenario,budget,runs,successful_runs,best_J,mean_J,sample_sd_J,mean_L_ms,mean_Lmax_ms,mean_runtime_ms");
        for (String scenario :
                new String[] {
                    "budget", "default", "RTT_plus_25_percent", "bandwidth_minus_30_percent"
                })
            for (int budget : java.util.stream.IntStream.of(20, 50, 100, 200, settings.budget()).distinct().toArray()) {
                List<Measurement> selected = select(all, scenario, budget);
                if (selected.isEmpty()) continue;
                double[] stats = stats(selected);
                rows.add(
                        CsvFiles.row(
                                scenario,
                                budget,
                                selected.size(),
                                (int) stats[0],
                                stats[1],
                                stats[2],
                                stats[3],
                                stats[4],
                                stats[5],
                                stats[6]));
            }
        Files.write(
                folder.resolve("assessment-sensitivity-summary.csv"), rows, StandardCharsets.UTF_8);
        if (cancel.getAsBoolean()) return; // Preserve partial records without plotting missing scenarios.
        StudyCharts.png(
                folder.resolve("budget-sensitivity.png"),
                new SensitivityPlot(all, false),
                1400,
                800);
        StudyCharts.png(
                folder.resolve("network-sensitivity.png"),
                new SensitivityPlot(all, true),
                1500,
                800);
    }

    public static void writeTrialMetrics(Path folder, ExperimentRunner.Study study)
            throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add(
                "protocol,method,seed,feasible,J,M_seconds,C_usd,E_wh,L_ms,Lmax_ms,sla_violations,evaluations,unique_space_percent,search_runtime_ms,stop_reason");
        for (ExperimentRunner.Trial trial : study.trials()) {
            SearchResult r = trial.result();
            Evaluation e = r.evaluation();
            rows.add(
                    CsvFiles.row(
                            trial.protocol(),
                            trial.method(),
                            trial.method() == SearchMethod.EXACT ? "N/A" : trial.seed(),
                            e != null,
                            e == null ? "NA" : e.score(),
                            e == null ? "NA" : e.makespanSeconds(),
                            e == null ? "NA" : e.cost(),
                            e == null ? "NA" : e.energyWh(),
                            e == null ? "NA" : e.delayMs(),
                            e == null ? "NA" : e.worstLatencyMs(),
                            e == null ? "NA" : e.slaViolations(),
                            r.evaluations(),
                            100. * r.unique() / study.evaluator().data.searchSpace(),
                            r.milliseconds(),
                            r.stopReason()));
        }
        Files.write(folder.resolve("assessment-trials.csv"), rows, StandardCharsets.UTF_8);
    }

    private static List<Measurement> select(List<Measurement> all, String scenario, int budget) {
        List<Measurement> selected = new ArrayList<>();
        for (Measurement m : all)
            if (m.scenario().equals(scenario) && m.budget() == budget) selected.add(m);
        return selected;
    }

    private static double[] stats(List<Measurement> list) {
        int valid = 0;
        double best = Double.POSITIVE_INFINITY, sum = 0, l = 0, worst = 0, time = 0;
        for (Measurement m : list) {
            time += m.result().milliseconds();
            Evaluation e = m.result().evaluation();
            if (e != null) {
                valid++;
                best = Math.min(best, e.score());
                sum += e.score();
                l += e.delayMs();
                worst += e.worstLatencyMs();
            }
        }
        double mean = valid == 0 ? Double.NaN : sum / valid, ss = 0;
        for (Measurement m : list)
            if (m.result().evaluation() != null)
                ss += Math.pow(m.result().evaluation().score() - mean, 2);
        return new double[] {
            valid,
            best,
            mean,
            valid < 2 ? Double.NaN : Math.sqrt(ss / (valid - 1)),
            valid == 0 ? Double.NaN : l / valid,
            valid == 0 ? Double.NaN : worst / valid,
            time / Math.max(1, list.size())
        };
    }

    public static String summary(ExperimentRunner.Study study) {
        StringBuilder text =
                new StringBuilder(
                        "\n"
                            + "METHOD / BEST J / MEAN J / WORST J / FEASIBLE RUNS / MEAN EVALS / TIME"
                            + " ms / CONFIDENCE\n");
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary s = ExperimentRunner.summarize(study, method, "main");
            text.append(
                    String.format(
                            Locale.ROOT,
                            "%s / %.6f / %.6f / %.6f / %d/%d / %.0f / %.2f / %s%n",
                            method,
                            s.best(),
                            s.mean(),
                            s.worst(),
                            s.feasibleRuns(),
                            s.runs(),
                            s.evaluations(),
                            s.runtime(),
                            ExperimentRunner.confidence(study, s)));
        }
        return text.toString();
    }

    /**
     * Off-screen chart panel used to visualize budget and network sensitivity across repeated trials.
     */
    private static final class SensitivityPlot extends JPanel {
        final List<Measurement> points;
        final boolean network;

        SensitivityPlot(List<Measurement> points, boolean network) {
            this.points = points;
            this.network = network;
            setBackground(Color.WHITE);
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            Visuals.quality(g);
            Visuals.text(
                    g,
                    network
                            ? "NETWORK SENSITIVITY / identical seeds and search settings"
                            : "BUDGET SENSITIVITY / Local search, paired seeds, patience off",
                    25,
                    32,
                    20,
                    Visuals.INK,
                    true);
            int panels = network ? 3 : 1;
            for (int panel = 0; panel < panels; panel++) {
                int x = panel * getWidth() / panels,
                        left = x + 65,
                        right = x + getWidth() / panels - 25,
                        top = 100,
                        bottom = getHeight() - 130;
                String[] scenarios =
                        network
                                ? new String[] {
                                    "default", "RTT_plus_25_percent", "bandwidth_minus_30_percent"
                                }
                                : new String[] {"20", "50", "100", "200"};
                double[] values = new double[scenarios.length];
                double max = 0;
                for (int i = 0; i < scenarios.length; i++) {
                    double[] stats =
                            stats(
                                    select(
                                            points,
                                            network ? scenarios[i] : "budget",
                                            network ? points.stream().filter(m -> m.scenario().equals("default")).findFirst().orElseThrow().budget() : Integer.parseInt(scenarios[i])));
                    values[i] = stats[network ? panel == 0 ? 2 : panel == 1 ? 4 : 5 : 2];
                    max = Math.max(max, values[i]);
                }
                max *= 1.15;
                Visuals.text(
                        g,
                        network
                                ? panel == 0
                                        ? "Mean J"
                                        : "Mean " + (panel == 1 ? "L (ms)" : "Lmax (ms)")
                                : "Mean objective J (lower is better)",
                        left,
                        70,
                        14,
                        Visuals.INK,
                        true);
                for (int i = 0; i < values.length; i++) {
                    int center = left + (right - left) * (2 * i + 1) / (2 * values.length),
                            width = Math.min(110, (right - left) / 6),
                            y = bottom - (int) ((bottom - top) * values[i] / Math.max(.001, max));
                    g.setColor(Visuals.HOSTS[i % 4]);
                    g.fillRoundRect(center - width / 2, y, width, bottom - y, 6, 6);
                    Visuals.text(
                            g,
                            String.format(Locale.ROOT, "%.4f", values[i]),
                            center - width / 2,
                            y - 12,
                            12,
                            Visuals.INK,
                            true);
                    Visuals.text(
                            g,
                            network
                                    ? i == 0 ? "Default" : i == 1 ? "RTT +25%" : "BW -30%"
                                    : scenarios[i] + " evals",
                            center - 35,
                            bottom + 28,
                            11,
                            Visuals.MUTED,
                            false);
                }
            }
            Visuals.text(
                    g,
                    "CSV includes feasibility, J, M, C, E, L, Lmax, SLA counts and search runtime"
                            + " for every seed.",
                    25,
                    getHeight() - 35,
                    13,
                    Visuals.MUTED,
                    false);
            g.dispose();
        }
    }
}
