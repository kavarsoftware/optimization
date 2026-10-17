package optimizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** UTF-8, quoted CSV, decimal points independent of the computer's locale. */
public final class CsvFiles {
    private CsvFiles() {}

    public static String row(Object... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object value : values) {
            String s = String.valueOf(value);
            joiner.add("\"" + s.replace("\"", "\"\"") + "\"");
        }
        return joiner.toString();
    }

    private static List<String> parse(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        cells.add(cell.toString());
        return cells;
    }

    public static ProblemData read(Path directory) throws IOException {
        List<ProblemData.Server> servers = new ArrayList<>();
        List<ProblemData.Service> services = new ArrayList<>();
        List<String> serverLines =
                Files.readAllLines(directory.resolve("servers.csv"), StandardCharsets.UTF_8);
        for (int lineIndex = 1; lineIndex < serverLines.size(); lineIndex++) {
            String line = serverLines.get(lineIndex);
            if (line.isBlank()) continue;
            var c = parse(line);
            if (c.size() == 12) {
                servers.add(
                        new ProblemData.Server(
                                c.get(0),
                                number(c, 1),
                                number(c, 2),
                                number(c, 3),
                                number(c, 4),
                                0,
                                integer(c, 5),
                                integer(c, 6),
                                number(c, 7),
                                number(c, 8),
                                number(c, 9),
                                number(c, 10),
                                number(c, 11)));
                continue;
            }
            if (c.size() != 6)
                throw new IllegalArgumentException(
                        "servers.csv needs 6 original or 12 assessment columns");
            servers.add(
                    new ProblemData.Server(
                            c.get(0),
                            number(c, 1),
                            number(c, 2),
                            number(c, 3),
                            number(c, 4),
                            number(c, 5)));
        }
        List<String> serviceLines =
                Files.readAllLines(directory.resolve("services.csv"), StandardCharsets.UTF_8);
        for (int lineIndex = 1; lineIndex < serviceLines.size(); lineIndex++) {
            String line = serviceLines.get(lineIndex);
            if (line.isBlank()) continue;
            var c = parse(line);
            if (c.size() == 8) {
                services.add(
                        new ProblemData.Service(
                                c.get(0),
                                number(c, 1),
                                number(c, 2),
                                number(c, 3),
                                0,
                                number(c, 4),
                                integer(c, 5),
                                number(c, 6),
                                number(c, 7)));
                continue;
            }
            if (c.size() != 5)
                throw new IllegalArgumentException(
                        "services.csv needs 5 original or 8 assessment columns");
            services.add(
                    new ProblemData.Service(
                            c.get(0), number(c, 1), number(c, 2), number(c, 3), number(c, 4)));
        }
        return new ProblemData(
                directory.getFileName().toString(),
                servers,
                services,
                parse(serverLines.get(0)).size() == 12);
    }

    private static int integer(List<String> cells, int index) {
        double value = number(cells, index);
        if (!Double.isFinite(value)
                || value < 0
                || value > Integer.MAX_VALUE
                || value != Math.rint(value))
            throw new IllegalArgumentException("GPU and service-slot values must be integers");
        return (int) value;
    }

    private static double number(List<String> c, int i) {
        return Double.parseDouble(c.get(i).strip());
    }

    public static void writeData(Path directory, ProblemData data) throws IOException {
        Files.createDirectories(directory);
        if (data.assessment()) {
            writeAssessmentData(directory, data);
            return;
        }
        try (var out =
                Files.newBufferedWriter(directory.resolve("servers.csv"), StandardCharsets.UTF_8)) {
            out.write("server,cpu_cores,ram_gb,storage_gb,latency_ms,demo_cost_per_cpu\n");
            for (var s : data.servers())
                out.write(
                        row(s.name(), s.cpu(), s.ram(), s.storage(), s.latencyMs(), s.costPerCpu())
                                + "\n");
        }
        try (var out =
                Files.newBufferedWriter(
                        directory.resolve("services.csv"), StandardCharsets.UTF_8)) {
            out.write("service,cpu_cores,ram_gb,storage_gb,service_latency_ms\n");
            for (var s : data.services())
                out.write(row(s.name(), s.cpu(), s.ram(), s.storage(), s.latencyValue()) + "\n");
        }
    }

    private static void writeAssessmentData(Path folder, ProblemData data) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add(
                "server,cpu_cores,ram_gb,storage_gb,rtt_ms,gpu_slots,max_services,speed_gi_s,idle_w,peak_w,busy_price_usd_s,bandwidth_mbps");
        for (ProblemData.Server server : data.servers())
            rows.add(
                    row(
                            server.name(),
                            server.cpu(),
                            server.ram(),
                            server.storage(),
                            server.latencyMs(),
                            server.gpu(),
                            server.maxServices(),
                            server.speed(),
                            server.idlePower(),
                            server.peakPower(),
                            server.busyPrice(),
                            server.bandwidth()));
        Files.write(folder.resolve("servers.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add("service,cpu_cores,ram_gb,storage_gb,work_gi,gpu_slots,payload_mb,max_latency_ms");
        for (ProblemData.Service service : data.services())
            rows.add(
                    row(
                            service.name(),
                            service.cpu(),
                            service.ram(),
                            service.storage(),
                            service.work(),
                            service.gpu(),
                            service.payload(),
                            service.maxLatency()));
        Files.write(folder.resolve("services.csv"), rows, StandardCharsets.UTF_8);
    }

    public static void writeEvents(Path directory, List<SearchEvent> events) throws IOException {
        Files.createDirectories(directory);
        List<String> rows = new ArrayList<>();
        rows.add(
                "step,event,evaluations,feasible_evaluations,unique,restarts,search_runtime_ms,new_best,candidate_feasible,candidate_score,best_score,before_assignment,candidate_assignment,detail");
        for (var e : events)
            rows.add(
                    row(
                            e.sequence(),
                            e.type(),
                            e.evaluations(),
                            e.feasible(),
                            e.unique(),
                            e.restarts(),
                            e.milliseconds(),
                            e.newBest(),
                            e.evaluation() == null ? "NA" : e.evaluation().feasible(),
                            e.evaluation() == null ? "NA" : e.evaluation().score(),
                            e.bestEvaluation() == null ? "NA" : e.bestEvaluation().score(),
                            e.before() == null ? "" : Arrays.toString(e.before().copy()),
                            e.candidate() == null ? "" : Arrays.toString(e.candidate().copy()),
                            e.detail()));
        Files.write(directory.resolve("search-replay.csv"), rows, StandardCharsets.UTF_8);
    }

    private static double weightedContribution(Evaluator e, int service, int server) {
        double sum = 0;
        for (ProblemData.Service s : e.data.services()) sum += s.latencyValue();
        return sum == 0
                ? Double.NaN
                : e.data.services().get(service).latencyValue()
                        * e.data.servers().get(server).latencyMs()
                        / sum;
    }

    public static List<String> metadata(Evaluator evaluator, SearchResult result) {
        Evaluation e = result.evaluation();
        List<String> metadata = new ArrayList<>();
        metadata.add("field,value");
        Object[][] fields = {
            {"instance", evaluator.data.name()},
            {"version", Optimizer.VERSION},
            {
                "method",
                result.stopReason().startsWith("PDF numerical reference")
                        ? "REFERENCE_NO_SEARCH"
                        : result.method().name()
            },
            {"objective", evaluator.objective.name()},
            {"alpha", evaluator.alpha},
            {"minimum_services", evaluator.minServices},
            {
                "seed",
                result.method() == SearchMethod.EXACT
                        ? "N/A (deterministic)"
                        : result.config().seed()
            },
            {"budget", result.config().budget()},
            {"batch_size", result.config().batchSize()},
            {"target_score", result.config().target()},
            {"score_unit", ResultComparison.unit(evaluator)},
            {"feasible", e != null && e.feasible()},
            {
                "evaluation_space_percent",
                100.0 * result.evaluations() / evaluator.data.searchSpace()
            },
            {"unique_space_percent", 100.0 * result.unique() / evaluator.data.searchSpace()},
            {"patience_batches", result.config().patience()},
            {"greediness", result.config().greediness()},
            {"evaluations", result.evaluations()},
            {"unique_candidates", result.unique()},
            {"feasible_evaluations", result.feasible()},
            {"construction_attempts", result.constructions()},
            {"failed_constructions", result.failedConstructions()},
            {"restarts", result.restarts()},
            {"runtime_ms", result.milliseconds()},
            {"stop_reason", result.stopReason()},
            {"search_space", evaluator.data.searchSpace()},
            {"best_score", e == null ? "NA" : e.score()},
            {"average_delay_ms", e == null ? "NA" : e.delayMs()},
            {"makespan_seconds", e == null ? "NA" : e.makespanSeconds()},
            {"energy_wh", e == null ? "NA" : e.energyWh()},
            {"worst_latency_ms", e == null ? "NA" : e.worstLatencyMs()},
            {"sla_violations", e == null ? "NA" : e.slaViolations()},
            {"assessment_J_formula", "0.35*M/30 + 0.20*C/0.50 + 0.15*E/5 + 0.30*L/70"},
            {
                evaluator.data.assessment() ? "cost_usd" : "illustrative_cost",
                e == null ? "NA" : e.cost()
            },
            {"optimistic_bound", evaluator.optimisticBound()},
            {"java", System.getProperty("java.version")},
            {"os", System.getProperty("os.name")},
            {"arch", System.getProperty("os.arch")},
            {"processors", Runtime.getRuntime().availableProcessors()},
            {
                "cost_note",
                evaluator.data.assessment()
                        ? "Fixed assessment PDF prices in USD per busy second; see cost_usd"
                        : "Illustrative prices, cost units per observation period; replace with"
                              + " measured tariffs"
            },
            {
                "quality_claim",
                result.completeEnumeration(evaluator.data.searchSpace())
                        ? result.best() == null
                                ? "Proven infeasible by complete enumeration"
                                : "Proven by complete enumeration"
                        : "Best found; global optimality not certified"
            }
        };
        for (Object[] field : fields) metadata.add(row(field));
        return metadata;
    }

    private static void writeAssessmentResults(Path folder, Evaluator e, SearchResult result)
            throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add(
                "service,server,cpu_cores,ram_gb,disk_gb,gpu_slots,work_gi,payload_mb,rtt_ms,bandwidth_mbps,transmission_ms,network_ms,sla_ms,margin_ms,processing_seconds");
        if (result.best() != null)
            for (int i = 0; i < e.data.services().size(); i++) {
                ProblemData.Service service = e.data.services().get(i);
                int serverIndex = result.best().server(i);
                ProblemData.Server server = e.data.servers().get(serverIndex);
                double latency = e.networkLatency(i, serverIndex);
                rows.add(
                        row(
                                service.name(),
                                server.name(),
                                service.cpu(),
                                service.ram(),
                                service.storage(),
                                service.gpu(),
                                service.work(),
                                service.payload(),
                                server.latencyMs(),
                                server.bandwidth(),
                                latency - server.latencyMs(),
                                latency,
                                service.maxLatency(),
                                service.maxLatency() - latency,
                                service.work() / server.speed()));
            }
        Files.write(folder.resolve("network_assignment.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add("server,service,start_seconds,end_seconds,duration_seconds");
        if (result.best() != null)
            for (int s = 0; s < e.data.servers().size(); s++) {
                double cursor = 0;
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < e.data.services().size(); i++)
                    if (result.best().server(i) == s) order.add(i);
                for (int a = 0; a < order.size(); a++)
                    for (int b = a + 1; b < order.size(); b++)
                        if (e.data.services()
                                        .get(order.get(a))
                                        .name()
                                        .compareTo(e.data.services().get(order.get(b)).name())
                                > 0) {
                            int temp = order.get(a);
                            order.set(a, order.get(b));
                            order.set(b, temp);
                        }
                for (int i : order) {
                    double duration =
                            e.data.services().get(i).work() / e.data.servers().get(s).speed();
                    rows.add(
                            row(
                                    e.data.servers().get(s).name(),
                                    e.data.services().get(i).name(),
                                    cursor,
                                    cursor + duration,
                                    duration));
                    cursor += duration;
                }
            }
        Files.write(folder.resolve("timeline.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add(
                "server,cpu_cores,ram_gb,disk_gb,gpu_used,gpu_capacity,services,max_services,busy_seconds,power_w");
        if (result.evaluation() != null)
            for (int s = 0; s < e.data.servers().size(); s++) {
                Evaluation.Usage usage = result.evaluation().usage().get(s);
                ProblemData.Server server = e.data.servers().get(s);
                rows.add(
                        row(
                                server.name(),
                                usage.cpu(),
                                usage.ram(),
                                usage.storage(),
                                usage.gpu(),
                                server.gpu(),
                                usage.count(),
                                server.maxServices(),
                                usage.busySeconds(),
                                usage.powerWatts()));
            }
        Files.write(folder.resolve("assessment_usage.csv"), rows, StandardCharsets.UTF_8);
    }

    public static void export(Path directory, Evaluator evaluator, SearchResult result)
            throws IOException {
        Files.createDirectories(directory);
        writeData(directory.resolve("inputs"), evaluator.data);
        var e = result.evaluation();
        if (evaluator.objective == Evaluator.Objective.ASSESSMENT) {
            writeAssessmentResults(directory, evaluator, result);
            Files.copy(
                    directory.resolve("network_assignment.csv"),
                    directory.resolve("assignment.csv"),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (var out =
                    Files.newBufferedWriter(
                            directory.resolve("assignment.csv"), StandardCharsets.UTF_8)) {
                out.write(
                        "service,server,cpu_cores,ram_gb,storage_gb,service_latency_ms,server_latency_ms,weighted_contribution_ms,total_additive_delay_ms\n");
                if (result.best() != null)
                    for (int i = 0; i < evaluator.data.services().size(); i++) {
                        var s = evaluator.data.services().get(i);
                        var host = evaluator.data.servers().get(result.best().server(i));
                        out.write(
                                row(
                                                s.name(),
                                                host.name(),
                                                s.cpu(),
                                                s.ram(),
                                                s.storage(),
                                                s.latencyValue(),
                                                host.latencyMs(),
                                                weightedContribution(
                                                        evaluator, i, result.best().server(i)),
                                                s.latencyValue() + host.latencyMs())
                                        + "\n");
                    }
            }
        }
        try (var out =
                Files.newBufferedWriter(
                        directory.resolve("convergence.csv"), StandardCharsets.UTF_8)) {
            out.write("evaluations,best_score\n");
            for (var p : result.trace()) out.write(row(p.evaluations(), p.score()) + "\n");
        }
        Files.write(
                directory.resolve("run.csv"), metadata(evaluator, result), StandardCharsets.UTF_8);
        if (e != null)
            try (var out =
                    Files.newBufferedWriter(
                            directory.resolve("server_usage.csv"), StandardCharsets.UTF_8)) {
                out.write(
                        "server,count,cpu_used,cpu_capacity,ram_used,ram_capacity,storage_used,storage_capacity\n");
                for (int s = 0; s < e.usage().size(); s++) {
                    var u = e.usage().get(s);
                    var host = evaluator.data.servers().get(s);
                    out.write(
                            row(
                                            host.name(),
                                            u.count(),
                                            u.cpu(),
                                            host.cpu(),
                                            u.ram(),
                                            host.ram(),
                                            u.storage(),
                                            host.storage())
                                    + "\n");
                }
            }
    }
}
