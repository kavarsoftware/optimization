package optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** All methods call this same definition of feasibility and quality. */
public final class Evaluator {
    public enum Objective {
        ASSESSMENT("Assessment objective J"),
        WEIGHTED("Weighted latency (main)"),
        LATENCY("Additive latency (optional)"),
        COST("Illustrative CPU cost"),
        BALANCED("Weighted delay / cost compromise");
        private final String label;

        Objective(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }

    public final ProblemData data;
    public final int minServices;
    public final Objective objective;
    public final double alpha, delayScale, costScale;
    private final double totalWeight;

    public Evaluator(ProblemData data, int minServices, Objective objective, double alpha) {
        if (minServices < 0 || minServices > data.services().size() / data.servers().size())
            throw new IllegalArgumentException("Minimum services per server cannot be satisfied");
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Alpha must be in [0,1]");
        if ((objective == Objective.ASSESSMENT) != data.assessment())
            throw new IllegalArgumentException(
                    "Assessment data must use the assessment J evaluator; original CSV modes are"
                            + " separate");
        this.data = data;
        this.minServices = minServices;
        this.objective = objective;
        this.alpha = alpha;
        double weights = 0, cpu = 0, highestDelay = 0, highestPrice = 0;
        for (ProblemData.Service service : data.services()) {
            weights += service.latencyValue();
            cpu += service.cpu();
        }
        for (ProblemData.Server server : data.servers()) {
            highestDelay = Math.max(highestDelay, server.latencyMs());
            highestPrice = Math.max(highestPrice, server.costPerCpu());
        }
        totalWeight = weights;
        if (objective != Objective.LATENCY && objective != Objective.ASSESSMENT && weights <= 0)
            throw new IllegalArgumentException(
                    "Weighted latency needs a positive sum of service latencies");
        delayScale = Math.max(1, highestDelay);
        costScale = Math.max(1e-9, cpu * highestPrice);
    }

    /** Reuse these small arrays during enumeration instead of creating millions of objects. */
    static final class Work {
        final double[] cpu, ram, storage, busy, power;
        final int[] gpu;
        double[] latencies;
        double makespan, energy, worst;
        int sla;
        final int[] count;
        final boolean details;
        final List<String> violations = new ArrayList<>();
        boolean feasible;
        double delay, cost, score;

        Work(int servers, boolean details) {
            busy = new double[servers];
            power = new double[servers];
            gpu = new int[servers];
            cpu = new double[servers];
            ram = new double[servers];
            storage = new double[servers];
            count = new int[servers];
            this.details = details;
        }
    }

    Work newWork(boolean details) {
        return new Work(data.servers().size(), details);
    }

    public Evaluation evaluate(Candidate candidate) {
        Work work = newWork(true);
        measure(candidate == null ? null : candidate.copy(), work);
        return snapshot(work);
    }

    /** The fast path and the explanatory GUI path share every rule and formula below. */
    void measure(int[] assignment, Work work) {
        Arrays.fill(work.cpu, 0);
        Arrays.fill(work.ram, 0);
        Arrays.fill(work.storage, 0);
        Arrays.fill(work.count, 0);
        Arrays.fill(work.gpu, 0);
        Arrays.fill(work.busy, 0);
        Arrays.fill(work.power, 0);
        if (work.latencies == null) work.latencies = new double[data.services().size()];
        work.makespan = 0;
        work.energy = 0;
        work.worst = 0;
        work.sla = 0;
        work.violations.clear();
        work.feasible = true;
        work.delay = 0;
        work.cost = 0;
        if (assignment == null || assignment.length != data.services().size()) {
            work.feasible = false;
            work.score = Double.POSITIVE_INFINITY;
            work.delay = Double.NaN;
            if (work.details) work.violations.add("Assignment length must equal service count");
            return;
        }
        for (int i = 0; i < assignment.length; i++) {
            int s = assignment[i];
            if (s < 0 || s >= data.servers().size()) {
                work.feasible = false;
                if (work.details)
                    work.violations.add("Invalid server for " + data.services().get(i).name());
                continue;
            }
            ProblemData.Service service = data.services().get(i);
            ProblemData.Server server = data.servers().get(s);
            work.cpu[s] += service.cpu();
            work.ram[s] += service.ram();
            work.storage[s] += service.storage();
            work.count[s]++;
            if (objective == Objective.ASSESSMENT) {
                work.gpu[s] += service.gpu();
                work.busy[s] += service.work() / server.speed();
                double latency = networkLatency(i, s);
                work.latencies[i] = latency;
                work.delay += latency;
                work.worst = Math.max(work.worst, latency);
                if (latency > service.maxLatency() + 1e-9) {
                    work.sla++;
                    work.feasible = false;
                    if (work.details)
                        work.violations.add(
                                service.name()
                                        + ": network SLA exceeded ("
                                        + latency
                                        + " > "
                                        + service.maxLatency()
                                        + " ms)");
                }
                continue;
            }
            if (objective == Objective.LATENCY)
                work.delay += service.latencyValue() + server.latencyMs();
            else work.delay += service.latencyValue() * server.latencyMs();
            work.cost += service.cpu() * server.costPerCpu();
        }
        for (int s = 0; s < work.count.length; s++) {
            ProblemData.Server server = data.servers().get(s);
            if (objective == Objective.ASSESSMENT) {
                if (work.gpu[s] > server.gpu()) {
                    work.feasible = false;
                    if (work.details) work.violations.add(server.name() + ": GPU slots exceeded");
                }
                if (work.count[s] > server.maxServices()) {
                    work.feasible = false;
                    if (work.details)
                        work.violations.add(server.name() + ": maximum service count exceeded");
                }
                work.makespan = Math.max(work.makespan, work.busy[s]);
                work.power[s] =
                        work.busy[s] > 0
                                ? server.idlePower()
                                        + (server.peakPower() - server.idlePower())
                                                * work.cpu[s]
                                                / server.cpu()
                                : 0;
                work.energy += work.power[s] * work.busy[s] / 3600.;
                work.cost += server.busyPrice() * work.busy[s];
            }
            if (work.count[s] < minServices) {
                work.feasible = false;
                if (work.details)
                    work.violations.add(server.name() + ": service count < " + minServices);
            }
            if (work.cpu[s] > server.cpu() + 1e-9) {
                work.feasible = false;
                if (work.details) work.violations.add(server.name() + ": CPU overload");
            }
            if (work.ram[s] > server.ram() + 1e-9) {
                work.feasible = false;
                if (work.details) work.violations.add(server.name() + ": RAM overload");
            }
            if (work.storage[s] > server.storage() + 1e-9) {
                work.feasible = false;
                if (work.details) work.violations.add(server.name() + ": storage overload");
            }
        }
        work.delay /=
                objective == Objective.LATENCY || objective == Objective.ASSESSMENT
                        ? assignment.length
                        : totalWeight;
        double quality =
                objective == Objective.ASSESSMENT
                        ? assessmentScore(work.makespan, work.cost, work.energy, work.delay)
                        : score(work.delay, work.cost);
        work.score = work.feasible ? quality : Double.POSITIVE_INFINITY;
    }

    Evaluation snapshot(Work work) {
        List<Evaluation.Usage> usage = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();
        for (int s = 0; s < work.count.length; s++)
            usage.add(
                    new Evaluation.Usage(
                            work.cpu[s],
                            work.ram[s],
                            work.storage[s],
                            work.count[s],
                            work.gpu[s],
                            work.busy[s],
                            work.power[s]));
        if (objective == Objective.ASSESSMENT && work.latencies != null)
            for (double latency : work.latencies) latencies.add(latency);
        return new Evaluation(
                work.feasible,
                work.score,
                work.delay,
                work.cost,
                usage,
                work.violations,
                work.makespan,
                work.energy,
                work.worst,
                work.sla,
                latencies);
    }

    public double networkLatency(int service, int server) {
        return data.servers().get(server).latencyMs()
                + 1000.
                        * 8.
                        * data.services().get(service).payload()
                        / data.servers().get(server).bandwidth();
    }

    public static double assessmentScore(
            double makespan, double cost, double energy, double latency) {
        return .35 * makespan / 30. + .20 * cost / .50 + .15 * energy / 5. + .30 * latency / 70.;
    }

    public boolean compatible(int serviceIndex, int serverIndex) {
        ProblemData.Service service = data.services().get(serviceIndex);
        ProblemData.Server server = data.servers().get(serverIndex);
        return service.cpu() <= server.cpu() + 1e-9
                && service.ram() <= server.ram() + 1e-9
                && service.storage() <= server.storage() + 1e-9
                && (objective != Objective.ASSESSMENT
                        || service.gpu() <= server.gpu()
                                && server.maxServices() > 0
                                && networkLatency(serviceIndex, serverIndex)
                                        <= service.maxLatency() + 1e-9);
    }

    public double score(double delay, double cost) {
        switch (objective) {
            case COST:
                return cost;
            case BALANCED:
                return alpha * delay / delayScale + (1 - alpha) * cost / costScale;
            default:
                return delay;
        }
    }

    /** Greedy ranking uses the same per-service contribution as the final score. */
    public double contribution(int serviceIndex, int serverIndex) {
        ProblemData.Service service = data.services().get(serviceIndex);
        ProblemData.Server server = data.servers().get(serverIndex);
        if (objective == Objective.ASSESSMENT) {
            double busy = service.work() / server.speed();
            double power =
                    server.idlePower()
                            + (server.peakPower() - server.idlePower())
                                    * service.cpu()
                                    / server.cpu();
            return assessmentScore(
                    busy,
                    server.busyPrice() * busy,
                    power * busy / 3600.,
                    networkLatency(serviceIndex, serverIndex) / data.services().size());
        }
        double delay;
        if (objective == Objective.LATENCY)
            delay = (service.latencyValue() + server.latencyMs()) / data.services().size();
        else delay = service.latencyValue() * server.latencyMs() / totalWeight;
        return score(delay, service.cpu() * server.costPerCpu());
    }

    /** This deliberately ignores capacity and count limits, so it cannot prove optimality. */
    public double optimisticBound() {
        if (objective == Objective.ASSESSMENT)
            return 0; // All four nonnegative terms make zero a safe weak bound.
        double bound = 0;
        for (int i = 0; i < data.services().size(); i++) {
            double best = Double.POSITIVE_INFINITY;
            for (int s = 0; s < data.servers().size(); s++)
                best = Math.min(best, contribution(i, s));
            bound += best;
        }
        return bound;
    }
}
