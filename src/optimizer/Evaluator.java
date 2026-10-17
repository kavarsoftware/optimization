package optimizer;

import java.util.*;

/** The only definition of feasibility and objective, shared by every search method. */
public final class Evaluator {
    public enum Objective {
        LATENCY("Service + server delay"), WEIGHTED("Weighted delay (original code)"),
        COST("Illustrative CPU cost"), BALANCED("Delay / cost compromise");
        private final String label;
        Objective(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public final ProblemData data;
    public final int minServices;
    public final Objective objective;
    public final double alpha;
    public final double delayScale, costScale;
    public Evaluator(ProblemData data, int minServices, Objective objective, double alpha) {
        if (minServices < 0 || minServices > data.services().size() / data.servers().size())
            throw new IllegalArgumentException("Minimum services per server cannot be satisfied");
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("Alpha must be in [0,1]");
        if (objective == Objective.WEIGHTED && data.services().stream().mapToDouble(ProblemData.Service::latencyValue).sum() <= 0)
            throw new IllegalArgumentException("Weighted mode requires a positive total weight");
        this.data = data; this.minServices = minServices; this.objective = objective; this.alpha = alpha;
        delayScale = Math.max(1, data.servers().stream().mapToDouble(ProblemData.Server::latencyMs).max().orElse(1)
            + data.services().stream().mapToDouble(ProblemData.Service::latencyValue).average().orElse(0));
        costScale = Math.max(1e-9, data.services().stream().mapToDouble(ProblemData.Service::cpu).sum()
            * data.servers().stream().mapToDouble(ProblemData.Server::costPerCpu).max().orElse(1));
    }
    public Evaluation evaluate(Candidate candidate) {
        int m = data.servers().size(), n = data.services().size();
        List<String> violations = new ArrayList<>();
        double[] cpu = new double[m], ram = new double[m], storage = new double[m];
        int[] count = new int[m]; double delay = 0, cost = 0, weights = 0;
        if (candidate == null || candidate.size() != n)
            return new Evaluation(false, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, List.of(), List.of("Assignment length must equal service count"));
        for (int i = 0; i < n; i++) {
            int s = candidate.server(i);
            if (s < 0 || s >= m) { violations.add("Invalid server for " + data.services().get(i).name()); continue; }
            var task = data.services().get(i); var host = data.servers().get(s);
            cpu[s] += task.cpu(); ram[s] += task.ram(); storage[s] += task.storage(); count[s]++;
            if (objective == Objective.WEIGHTED) { delay += task.latencyValue() * host.latencyMs(); weights += task.latencyValue(); }
            else delay += task.latencyValue() + host.latencyMs();
            cost += task.cpu() * host.costPerCpu();
        }
        List<Evaluation.Usage> usage = new ArrayList<>();
        for (int s = 0; s < m; s++) {
            var host = data.servers().get(s);
            if (count[s] < minServices) violations.add(host.name()+": service count < "+minServices);
            if (cpu[s] > host.cpu()+1e-9) violations.add(host.name()+": CPU overload");
            if (ram[s] > host.ram()+1e-9) violations.add(host.name()+": RAM overload");
            if (storage[s] > host.storage()+1e-9) violations.add(host.name()+": storage overload");
            usage.add(new Evaluation.Usage(cpu[s],ram[s],storage[s],count[s]));
        }
        delay /= objective == Objective.WEIGHTED ? weights : n;
        double score = score(delay, cost);
        return new Evaluation(violations.isEmpty(), violations.isEmpty() ? score : Double.POSITIVE_INFINITY,
            delay, cost, usage, violations);
    }
    public double score(double delay, double cost) {
        return switch (objective) {
            case LATENCY, WEIGHTED -> delay;
            case COST -> cost;
            case BALANCED -> alpha * delay / delayScale + (1-alpha) * cost / costScale;
        };
    }
    /** Placement contribution used only for the greedy ranking. Final decisions use evaluate(). */
    public double contribution(int service, int server) {
        var task = data.services().get(service); var host = data.servers().get(server);
        double delay = objective == Objective.WEIGHTED
            ? task.latencyValue()*host.latencyMs()/data.services().stream().mapToDouble(ProblemData.Service::latencyValue).sum()
            : (task.latencyValue()+host.latencyMs())/data.services().size();
        return score(delay,task.cpu()*host.costPerCpu());
    }
    /** An optimistic bound ignoring all capacities and minimum counts. A gap is not proof of optimality. */
    public double optimisticBound() {
        double bound = 0;
        for (int i = 0; i < data.services().size(); i++) {
            double best = Double.POSITIVE_INFINITY;
            for (int s = 0; s < data.servers().size(); s++) best = Math.min(best,contribution(i,s));
            bound += best;
        }
        return bound;
    }
}
