package optimizer;

import java.util.*;

/** Seven teaching implementations. Every complete trial goes through the same counted evaluator. */
final class AdvancedSearch {
    private record State(Candidate candidate, Evaluation value) {}

    static void run(Optimizer.Context c) {
        switch (c.method) {
            case HILL_CLIMB -> hill(c);
            case ANNEALING -> anneal(c);
            case TABU -> tabu(c);
            case GENETIC -> genetic(c);
            case ITERATED -> iterated(c, false);
            case VNS -> iterated(c, true);
            case BEAM -> beam(c);
            default -> throw new IllegalArgumentException("Not an advanced method");
        }
    }

    private static State check(Optimizer.Context c, Candidate a, State before) {
        Evaluation value =
                c.check(
                        a,
                        SearchEvent.Type.MOVE,
                        before == null ? null : before.candidate,
                        before == null ? null : before.value);
        return value.feasible() ? new State(a, value) : null;
    }

    private static State start(Optimizer.Context c) {
        while (c.running()) {
            c.constructions++;
            Candidate a = Optimizer.construct(c);
            if (a == null) {
                c.failed++;
                if (++c.consecutiveFailures >= Math.max(2000, c.config.budget() * 2L))
                    c.reason = "Construction attempt limit (no infeasibility proof)";
                continue;
            }
            c.consecutiveFailures = 0;
            c.restarts++;
            Evaluation value = c.check(a, SearchEvent.Type.CONSTRUCT, null, null);
            if (value.feasible()) return new State(a, value);
        }
        return null;
    }

    /** Explicit move and/or swap neighborhoods; unchanged assignments are excluded. */
    private static List<Candidate> neighbors(Optimizer.Context c, Candidate a, boolean swaps) {
        List<Candidate> list = new ArrayList<>();
        int m = c.e.data.servers().size();
        for (int i = 0; i < a.size(); i++)
            for (int s = 0; s < m; s++)
                if (s != a.server(i)) {
                    int[] v = a.copy();
                    v[i] = s;
                    list.add(new Candidate(v));
                }
        if (swaps)
            for (int i = 0; i < a.size(); i++)
                for (int j = i + 1; j < a.size(); j++)
                    if (a.server(i) != a.server(j)) {
                        int[] v = a.copy();
                        v[i] = a.server(j);
                        v[j] = a.server(i);
                        list.add(new Candidate(v));
                    }
        return list;
    }

    private static State climb(Optimizer.Context c, State current, boolean swaps, boolean first) {
        while (c.running()) {
            State next = current;
            List<Candidate> neighborhood = neighbors(c, current.candidate, swaps);
            if (first) Collections.shuffle(neighborhood, c.random);
            for (Candidate a : neighborhood) {
                if (!c.running()) break;
                State trial = check(c, a, current);
                if (trial != null && trial.value.score() < next.value.score() - 1e-10) {
                    next = trial;
                    if (first) break;
                }
            }
            if (next == current) return current;
            current = next;
        }
        return current;
    }

    private static void hill(Optimizer.Context c) {
        while (c.running()) {
            State s = start(c);
            if (s != null) climb(c, s, false, true);
        }
    }

    private static Candidate shake(Optimizer.Context c, Candidate a, int strength) {
        int[] v = a.copy();
        int m = c.e.data.servers().size();
        for (int k = 0; k < strength; k++) {
            int i = c.random.nextInt(v.length);
            if (v.length > 1 && c.random.nextBoolean()) {
                int j = c.random.nextInt(v.length - 1);
                if (j >= i) j++;
                int temp = v[i];
                v[i] = v[j];
                v[j] = temp;
            } else if (m > 1) {
                int s = c.random.nextInt(m - 1);
                v[i] = s >= v[i] ? s + 1 : s;
            }
        }
        return new Candidate(v);
    }

    private static void anneal(Optimizer.Context c) {
        while (c.running()) {
            State current = start(c);
            if (current == null) continue;
            // Geometric cooling over 2000 trials, then a fresh constructed start / reheat.
            double t0 = .05 * Math.max(1, Math.abs(current.value.score()));
            for (int step = 0; step < 2000 && c.running(); step++) {
                State trial = check(c, shake(c, current.candidate, 1), current);
                if (trial == null) continue;
                double delta = trial.value.score() - current.value.score();
                double temperature = t0 * Math.pow(.001, step / 1999.0);
                if (delta <= 0 || c.random.nextDouble() < Math.exp(-delta / temperature))
                    current = trial;
            }
        }
    }

    private static void tabu(Optimizer.Context c) {
        while (c.running()) {
            State current = start(c);
            if (current == null) continue;
            Deque<Long> memory = new ArrayDeque<>();
            memory.add(current.candidate.code(c.e.data.servers().size()));
            int stale = 0;
            while (c.running() && stale < 20) {
                State selected = null;
                double oldBest = c.bestEvaluation.score();
                for (Candidate a : neighbors(c, current.candidate, true)) {
                    if (!c.running()) break;
                    // Read the aspiration threshold BEFORE check updates the global incumbent.
                    double aspiration = c.bestEvaluation.score();
                    State trial = check(c, a, current);
                    if (trial == null) continue;
                    long code = a.code(c.e.data.servers().size());
                    if (memory.contains(code) && trial.value.score() >= aspiration - 1e-10)
                        continue;
                    if (selected == null || trial.value.score() < selected.value.score())
                        selected = trial;
                }
                if (selected == null) break;
                current = selected; // tabu may deliberately accept a worse current state
                memory.addLast(current.candidate.code(c.e.data.servers().size()));
                if (memory.size() > 12) memory.removeFirst();
                stale = c.bestEvaluation.score() < oldBest - 1e-10 ? 0 : stale + 1;
            }
        }
    }

    private static State tournament(Optimizer.Context c, List<State> population) {
        State best = population.get(c.random.nextInt(population.size()));
        for (int i = 1; i < 3; i++) {
            State s = population.get(c.random.nextInt(population.size()));
            if (s.value.score() < best.value.score()) best = s;
        }
        return best;
    }

    private static void genetic(Optimizer.Context c) {
        List<State> population = new ArrayList<>();
        while (population.size() < 32 && c.running()) {
            State s = start(c);
            if (s != null) population.add(s);
        }
        int children = 0;
        while (!population.isEmpty() && c.running()) {
            State child;
            if (++children % 100 == 0) child = start(c); // diversity injection
            else {
                State p = tournament(c, population), q = tournament(c, population);
                int[] v = p.candidate.copy();
                for (int i = 0; i < v.length; i++) {
                    if (c.random.nextBoolean()) v[i] = q.candidate.server(i);
                    if (c.random.nextDouble() < 1.0 / v.length)
                        v[i] = c.random.nextInt(c.e.data.servers().size());
                }
                child = check(c, new Candidate(v), p);
            }
            if (child == null) continue;
            int worst = 0;
            for (int i = 1; i < population.size(); i++)
                if (population.get(i).value.score() > population.get(worst).value.score())
                    worst = i;
            population.set(worst, child); // steady-state replacement, external best is retained
        }
    }

    private static void iterated(Optimizer.Context c, boolean variable) {
        while (c.running()) {
            State current = start(c);
            if (current == null) continue;
            current = climb(c, current, true, false);
            int k = 1, stale = 0;
            while (c.running() && stale < (variable ? 12 : 8)) {
                State trial = null;
                for (int attempt = 0; attempt < 20 && trial == null && c.running(); attempt++)
                    trial = check(c, shake(c, current.candidate, variable ? k : 2), current);
                if (trial != null) trial = climb(c, trial, true, false);
                if (trial != null && trial.value.score() < current.value.score() - 1e-10) {
                    current = trial;
                    k = 1;
                    stale = 0;
                } else {
                    stale++;
                    if (variable) k = k % 3 + 1;
                }
            }
        }
    }

    private static void beam(Optimizer.Context c) {
        while (c.running()) {
            List<State> beam = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            for (int i = 0; i < 8 && c.running(); i++) {
                State s = start(c);
                if (s != null && visited.add(s.candidate.code(c.e.data.servers().size())))
                    beam.add(s);
            }
            for (int layer = 0; layer < 20 && !beam.isEmpty() && c.running(); layer++) {
                Map<Long, State> children = new HashMap<>();
                for (State parent : beam)
                    for (Candidate a : neighbors(c, parent.candidate, true)) {
                        if (!c.running()) break;
                        State child = check(c, a, parent);
                        long code = a.code(c.e.data.servers().size());
                        if (child != null && !visited.contains(code))
                            children.putIfAbsent(code, child);
                    }
                List<State> next = new ArrayList<>(children.values());
                // Explicit code tie-break makes results independent of HashMap iteration order.
                next.sort(
                        Comparator.comparingDouble((State s) -> s.value.score())
                                .thenComparingLong(
                                        s -> s.candidate.code(c.e.data.servers().size())));
                beam = new ArrayList<>(next.subList(0, Math.min(8, next.size())));
                for (State s : beam) visited.add(s.candidate.code(c.e.data.servers().size()));
            }
        }
    }
}
