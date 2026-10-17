package optimizer;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** GRASP: randomized capacity-aware construction, strict local descent, independent restarts. */
public final class Optimizer {
    public static final String VERSION = "assessment-portfolio-4.0";

    public record Progress(
            int evaluations,
            int feasible,
            int unique,
            int restarts,
            Candidate best,
            Evaluation evaluation,
            long milliseconds,
            String phase,
            List<SearchResult.Point> trace) {}

    public SearchResult run(
            Evaluator evaluator,
            SearchMethod method,
            SearchConfig config,
            Consumer<Progress> listener,
            BooleanSupplier cancelled) {
        return run(evaluator, method, config, listener, cancelled, null);
    }

    public SearchResult run(
            Evaluator evaluator,
            SearchMethod method,
            SearchConfig config,
            Consumer<Progress> listener,
            BooleanSupplier cancelled,
            Consumer<SearchEvent> events) {
        return run(evaluator, method, config, listener, cancelled, events, null);
    }

    public SearchResult run(
            Evaluator evaluator,
            SearchMethod method,
            SearchConfig config,
            Consumer<Progress> listener,
            BooleanSupplier cancelled,
            Consumer<SearchEvent> events,
            SearchControl control) {
        if (method == SearchMethod.EXACT)
            return exact(evaluator, config, listener, cancelled, events, control);
        Context c = new Context(evaluator, method, config, listener, cancelled, events, control);
        String impossible = necessaryCheck(evaluator);
        if (impossible != null) c.reason = impossible;
        if (method.advanced()) AdvancedSearch.run(c);
        else
            while (c.running()) {
                if (method == SearchMethod.RANDOM || method == SearchMethod.TARGET) {
                    int[] values = new int[evaluator.data.services().size()];
                    for (int i = 0; i < values.length; i++)
                        values[i] = c.random.nextInt(evaluator.data.servers().size());
                    c.check(new Candidate(values), SearchEvent.Type.RANDOM_SAMPLE, null, null);
                } else {
                    c.constructions++;
                    Candidate start = construct(c);
                    if (start == null) {
                        c.failed++;
                        c.consecutiveFailures++;
                        if (c.consecutiveFailures >= Math.max(2_000, config.budget() * 2L))
                            c.reason = "Construction attempt limit (no infeasibility proof)";
                        continue;
                    }
                    c.consecutiveFailures = 0;
                    c.restarts++;
                    Evaluation first = c.check(start, SearchEvent.Type.CONSTRUCT, null, null);
                    if (method == SearchMethod.HYBRID && first.feasible() && c.running())
                        descend(c, start, first);
                }
            }
        c.record();
        c.publish("Finished");
        c.emit(
                SearchEvent.Type.FINISH,
                c.best,
                c.bestEvaluation,
                c.best,
                c.bestEvaluation,
                false,
                c.reason);
        return new SearchResult(
                method,
                config,
                c.best,
                c.bestEvaluation,
                c.evaluations,
                c.feasible,
                c.unique,
                c.constructions,
                c.failed,
                c.restarts,
                c.elapsed(),
                c.reason,
                List.copyOf(c.trace));
    }

    /** Count in base m, like an odometer. No assignment is skipped or repeated. */
    private SearchResult exact(
            Evaluator e,
            SearchConfig requested,
            Consumer<Progress> listener,
            BooleanSupplier cancelled,
            Consumer<SearchEvent> events,
            SearchControl control) {
        int total = Math.toIntExact(e.data.searchSpace());
        SearchConfig config =
                new SearchConfig(
                        requested.seed(),
                        total,
                        requested.batchSize(),
                        0,
                        requested.greediness(),
                        requested.target());
        int[] assignment = new int[e.data.services().size()];
        Evaluator.Work work = e.newWork(false);
        List<SearchResult.Point> trace = new ArrayList<>();
        Candidate best = null;
        Evaluation bestEvaluation = null;
        int checked = 0, feasible = 0, sequence = 0;
        long start = System.nanoTime();
        while (checked < total && !cancelled.getAsBoolean()) {
            if (control != null) {
                control.beforeEvaluation();
                if (control.stopped()) break;
            }
            // The evaluator reuses arrays; only a new incumbent needs an immutable snapshot.
            e.measure(assignment, work);
            checked++;
            Candidate previous = best;
            Evaluation previousEvaluation = bestEvaluation;
            boolean improved = false;
            if (work.feasible) {
                feasible++;
                if (best == null || work.score < bestEvaluation.score()) {
                    best = new Candidate(assignment);
                    bestEvaluation = e.snapshot(work);
                    improved = true;
                }
            }
            boolean checkpoint = checked % config.batchSize() == 0 || checked == total;
            if (best != null && (improved || checkpoint))
                trace.add(new SearchResult.Point(checked, bestEvaluation.score()));
            long elapsed =
                    (System.nanoTime() - start - (control == null ? 0 : control.waitingNanos()))
                            / 1_000_000;
            if (checkpoint && listener != null)
                listener.accept(
                        new Progress(
                                checked,
                                feasible,
                                checked,
                                0,
                                best,
                                bestEvaluation,
                                elapsed,
                                "Enumerating",
                                List.copyOf(trace)));
            if (events != null
                    && (improved
                            || checked % Math.max(1, total / 20) == 0
                            || control != null && control.paused())) {
                events.accept(
                        new SearchEvent(
                                ++sequence,
                                SearchEvent.Type.EXACT_SCAN,
                                checked,
                                feasible,
                                checked,
                                0,
                                elapsed,
                                previous,
                                previousEvaluation,
                                best,
                                bestEvaluation,
                                best,
                                bestEvaluation,
                                improved,
                                "Exact enumeration has checked "
                                        + checked
                                        + " of "
                                        + total
                                        + " assignments. Showing the best so far."));
            }
            for (int i = assignment.length - 1; i >= 0; i--) {
                assignment[i]++;
                if (assignment[i] < e.data.servers().size()) break;
                assignment[i] = 0;
            }
        }
        long elapsed =
                (System.nanoTime() - start - (control == null ? 0 : control.waitingNanos()))
                        / 1_000_000;
        String reason =
                checked == total
                        ? best == null
                                ? "Infeasible: complete enumeration found no feasible assignment"
                                : "Complete enumeration: global optimum proven for this model"
                        : "Cancelled by user; enumeration incomplete";
        if (best != null
                && (trace.isEmpty() || trace.get(trace.size() - 1).evaluations() != checked))
            trace.add(new SearchResult.Point(checked, bestEvaluation.score()));
        if (events != null)
            events.accept(
                    new SearchEvent(
                            ++sequence,
                            SearchEvent.Type.FINISH,
                            checked,
                            feasible,
                            checked,
                            0,
                            elapsed,
                            best,
                            bestEvaluation,
                            best,
                            bestEvaluation,
                            best,
                            bestEvaluation,
                            false,
                            reason));
        if (listener != null)
            listener.accept(
                    new Progress(
                            checked,
                            feasible,
                            checked,
                            0,
                            best,
                            bestEvaluation,
                            elapsed,
                            "Finished",
                            List.copyOf(trace)));
        return new SearchResult(
                SearchMethod.EXACT,
                config,
                best,
                bestEvaluation,
                checked,
                feasible,
                checked,
                0,
                0,
                0,
                elapsed,
                reason,
                trace);
    }

    private static String necessaryCheck(Evaluator e) {
        double cpu = 0, ram = 0, storage = 0, cpuCapacity = 0, ramCapacity = 0, storageCapacity = 0;
        int taskIndex = 0;
        int gpuDemand = 0, gpuCapacity = 0, slots = 0;
        for (ProblemData.Service task : e.data.services()) {
            boolean fits = false;
            for (ProblemData.Server server : e.data.servers()) {
                if (e.compatible(taskIndex, e.data.servers().indexOf(server))) fits = true;
            }
            if (!fits) return "Infeasible: no server can hold " + task.name();
            gpuDemand += task.gpu();
            taskIndex++;
            cpu += task.cpu();
            ram += task.ram();
            storage += task.storage();
        }
        for (ProblemData.Server server : e.data.servers()) {
            gpuCapacity += server.gpu();
            slots += Math.min(e.data.services().size(), server.maxServices());
            cpuCapacity += server.cpu();
            ramCapacity += server.ram();
            storageCapacity += server.storage();
        }
        if (e.objective == Evaluator.Objective.ASSESSMENT
                && (gpuDemand > gpuCapacity || slots < e.data.services().size()))
            return "Infeasible: total GPU demand or service slots exceed capacity";
        if (cpu > cpuCapacity + 1e-9
                || ram > ramCapacity + 1e-9
                || storage > storageCapacity + 1e-9)
            return "Infeasible: total demand exceeds total capacity";
        return null;
    }

    static Candidate construct(Context c) {
        var data = c.e.data;
        int n = data.services().size(), m = data.servers().size();
        Integer[] order = new Integer[n];
        double[] priority = new double[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            var task = data.services().get(i);
            int fits = 0;
            double pressure = 0;
            for (var host : data.servers()) {
                if (c.e.compatible(i, data.servers().indexOf(host))) fits++;
                pressure =
                        Math.max(
                                pressure,
                                Math.max(
                                        task.cpu() / host.cpu(),
                                        Math.max(
                                                task.ram() / host.ram(),
                                                task.storage() / host.storage())));
            }
            priority[i] = 100 * (m - fits) + pressure * (.7 + .6 * c.random.nextDouble());
        }
        // Twelve items are small enough for a simple insertion sort.
        for (int i = 1; i < n; i++) {
            int item = order[i], j = i - 1;
            while (j >= 0 && priority[order[j]] < priority[item]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = item;
        }
        int[] values = new int[n], counts = new int[m];
        double[] cpu = new double[m], ram = new double[m], storage = new double[m];
        double alpha = c.constructions % 5 == 0 ? 1.0 : c.config.greediness();
        for (int k = 0; k < n; k++) {
            if (c.cancelled.getAsBoolean()) return null;
            int i = order[k];
            var task = data.services().get(i);
            List<Integer> options = new ArrayList<>();
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int s = 0; s < m; s++) {
                var host = data.servers().get(s);
                if (cpu[s] + task.cpu() > host.cpu() + 1e-9
                        || ram[s] + task.ram() > host.ram() + 1e-9
                        || storage[s] + task.storage() > host.storage() + 1e-9) continue;
                if (c.e.objective == Evaluator.Objective.ASSESSMENT) {
                    int gpuUsed = 0;
                    for (int previous = 0; previous < k; previous++)
                        if (values[order[previous]] == s)
                            gpuUsed += data.services().get(order[previous]).gpu();
                    if (!c.e.compatible(i, s)
                            || counts[s] >= host.maxServices()
                            || gpuUsed + task.gpu() > host.gpu()) continue;
                }
                int deficit = 0;
                for (int j = 0; j < m; j++)
                    deficit += Math.max(0, c.e.minServices - counts[j] - (j == s ? 1 : 0));
                if (deficit > n - k - 1) continue;
                options.add(s);
                double value = c.e.contribution(i, s);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            if (options.isEmpty())
                return null; // discard this partial construction, never report it as a candidate
            double cutoff = low + alpha * (high - low) + 1e-12;
            List<Integer> restricted = new ArrayList<>();
            for (int s : options) if (c.e.contribution(i, s) <= cutoff) restricted.add(s);
            int selected = restricted.get(c.random.nextInt(restricted.size()));
            values[i] = selected;
            cpu[selected] += task.cpu();
            ram[selected] += task.ram();
            storage[selected] += task.storage();
            counts[selected]++;
        }
        return new Candidate(values);
    }

    private void descend(Context c, Candidate current, Evaluation currentEvaluation) {
        int n = current.size(), m = c.e.data.servers().size();
        while (c.running()) {
            Candidate next = current;
            Evaluation nextEvaluation = currentEvaluation;
            // Inspect a complete move/swap neighborhood unless the global budget/stop rule
            // interrupts it.
            for (int i = 0; i < n && c.running(); i++) {
                for (int s = 0; s < m && c.running(); s++) {
                    if (s == current.server(i)) continue;
                    int[] values = current.copy();
                    values[i] = s;
                    Candidate candidate = new Candidate(values);
                    Evaluation evaluation =
                            c.check(candidate, SearchEvent.Type.MOVE, current, currentEvaluation);
                    if (evaluation.feasible()
                            && evaluation.score() < nextEvaluation.score() - 1e-10) {
                        next = candidate;
                        nextEvaluation = evaluation;
                    }
                }
            }
            for (int i = 0; i < n && c.running(); i++) {
                for (int j = i + 1; j < n && c.running(); j++) {
                    if (current.server(i) == current.server(j)) continue;
                    int[] values = current.copy();
                    values[i] = current.server(j);
                    values[j] = current.server(i);
                    Candidate candidate = new Candidate(values);
                    Evaluation evaluation =
                            c.check(candidate, SearchEvent.Type.SWAP, current, currentEvaluation);
                    if (evaluation.feasible()
                            && evaluation.score() < nextEvaluation.score() - 1e-10) {
                        next = candidate;
                        nextEvaluation = evaluation;
                    }
                }
            }
            if (next == current) {
                if (c.running())
                    c.emit(
                            SearchEvent.Type.LOCAL_OPTIMUM,
                            current,
                            currentEvaluation,
                            current,
                            currentEvaluation,
                            false,
                            "No improving move or swap in a complete neighborhood. Start again.");
                return;
            }
            c.emit(
                    SearchEvent.Type.ACCEPT,
                    current,
                    currentEvaluation,
                    next,
                    nextEvaluation,
                    false,
                    "Accept a strict feasible improvement, then inspect its neighborhood.");
            current = next;
            currentEvaluation = nextEvaluation;
        }
    }

    static final class Context {
        final Evaluator e;
        final SearchMethod method;
        final SearchConfig config;
        final Random random;
        final Consumer<Progress> listener;
        final BooleanSupplier cancelled;
        final long start = System.nanoTime();
        final Consumer<SearchEvent> events;
        final SearchControl control;
        final BitSet seen;
        final Set<Long> sparseSeen;
        final List<SearchResult.Point> trace = new ArrayList<>();
        Candidate best;
        Evaluation bestEvaluation;
        int evaluations,
                feasible,
                unique,
                constructions,
                failed,
                restarts,
                stagnant,
                consecutiveFailures;
        double checkpointBest = Double.POSITIVE_INFINITY;
        String reason;
        int eventCount, movesShown, swapsShown, acceptsShown, localShown, startsShown, samplesShown;

        Context(
                Evaluator e,
                SearchMethod method,
                SearchConfig config,
                Consumer<Progress> listener,
                BooleanSupplier cancelled,
                Consumer<SearchEvent> events,
                SearchControl control) {
            this.e = e;
            this.method = method;
            this.config = config;
            this.listener = listener;
            this.cancelled = cancelled;
            random = new Random(config.seed());
            this.events = events;
            this.control = control;
            seen = e.data.searchSpace() <= 100_000_000 ? new BitSet() : null;
            sparseSeen = seen == null ? new HashSet<>() : null;
        }

        boolean running() {
            if (reason != null) return false;
            if (cancelled.getAsBoolean()) reason = "Cancelled by user";
            else if (evaluations >= config.budget()) reason = "Evaluation budget reached";
            return reason == null;
        }

        Evaluation check(
                Candidate candidate,
                SearchEvent.Type type,
                Candidate before,
                Evaluation beforeEvaluation) {
            if (control != null) control.beforeEvaluation();
            Evaluation evaluation = e.evaluate(candidate);
            evaluations++;
            long code = candidate.code(e.data.servers().size());
            if (seen != null) {
                if (!seen.get((int) code)) {
                    seen.set((int) code);
                    unique++;
                }
            } else if (sparseSeen.add(code)) unique++;
            boolean improved = false;
            if (evaluation.feasible()) {
                feasible++;
                if (best == null || evaluation.score() < bestEvaluation.score() - 1e-10) {
                    best = candidate;
                    bestEvaluation = evaluation;
                    improved = true;
                }
            }
            if (improved) record();
            emit(type, before, beforeEvaluation, candidate, evaluation, improved, "");
            if (method == SearchMethod.TARGET
                    && best != null
                    && bestEvaluation.score() <= config.target() + 1e-10)
                reason = "Target reached: score <= " + config.target();
            if (evaluations % config.batchSize() == 0) {
                double value = best == null ? Double.POSITIVE_INFINITY : bestEvaluation.score();
                stagnant = value < checkpointBest - 1e-10 ? 0 : stagnant + 1;
                checkpointBest = value;
                if (reason == null
                        && (method == SearchMethod.HYBRID || method == SearchMethod.CONSTRUCTIVE)
                        && best != null
                        && config.patience() > 0
                        && stagnant >= config.patience())
                    reason = "No global improvement in " + stagnant + " complete batches";
                record();
                publish("Searching");
                emit(
                        SearchEvent.Type.CHECKPOINT,
                        best,
                        bestEvaluation,
                        best,
                        bestEvaluation,
                        false,
                        "Batch complete. "
                                + stagnant
                                + " stagnant batch(es). Unshown trials are included in the"
                                + " counters.");
            }
            return evaluation;
        }

        void record() {
            if (best == null) return;
            var point = new SearchResult.Point(evaluations, bestEvaluation.score());
            if (!trace.isEmpty() && trace.get(trace.size() - 1).evaluations() == evaluations)
                trace.set(trace.size() - 1, point);
            else trace.add(point);
        }

        long elapsed() {
            return (System.nanoTime() - start - (control == null ? 0 : control.waitingNanos()))
                    / 1_000_000;
        }

        void publish(String phase) {
            if (listener != null)
                listener.accept(
                        new Progress(
                                evaluations,
                                feasible,
                                unique,
                                restarts,
                                best,
                                bestEvaluation,
                                elapsed(),
                                phase,
                                List.copyOf(trace)));
        }

        void emit(
                SearchEvent.Type type,
                Candidate before,
                Evaluation beforeEvaluation,
                Candidate candidate,
                Evaluation evaluation,
                boolean improved,
                String detail) {
            if (events == null
                    || (eventCount >= 511
                            && type != SearchEvent.Type.FINISH
                            && !(control != null && control.paused()))) return;
            boolean selected =
                    (control != null && control.paused())
                            || improved
                            || switch (type) {
                                case CONSTRUCT -> startsShown++ < 4;
                                case RANDOM_SAMPLE -> samplesShown++ < 12;
                                case MOVE -> movesShown++ < 12;
                                case SWAP -> swapsShown++ < 12;
                                case ACCEPT -> acceptsShown++ < 8;
                                case LOCAL_OPTIMUM -> localShown++ < 4;
                                case EXACT_SCAN, CHECKPOINT, FINISH -> true;
                            };
            if (selected)
                events.accept(
                        new SearchEvent(
                                ++eventCount,
                                type,
                                evaluations,
                                feasible,
                                unique,
                                restarts,
                                elapsed(),
                                before,
                                beforeEvaluation,
                                candidate,
                                evaluation,
                                best,
                                bestEvaluation,
                                improved,
                                detail));
        }
    }
}
