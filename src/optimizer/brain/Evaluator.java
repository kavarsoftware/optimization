package optimizer.brain;



import java.util.*;

/** The assessment's hard constraints and equations, shared by search, UI and tests. */
public final class Evaluator {
    public final ProblemData data;
    public Evaluator(ProblemData data) {
        if (!data.assessment() || data.servers().size() != 4 || data.services().size() != 12)
            throw new IllegalArgumentException("The benchmark requires 4 servers and 12 services");
        this.data = data;
    }
    /** Arrays are reused during Exact's full enumeration. */
    static final class Work {
        final double[] cpu, ram, storage, busy, power, latencies;
        final int[] gpu, count;
        final boolean details;
        final List<String> violations = new ArrayList<>();
        boolean feasible;
        double makespan, energy, worst, delay, cost, score;
        int sla;
        Work(int servers, int services, boolean details) {
            cpu = new double[servers]; ram = new double[servers]; storage = new double[servers];
            busy = new double[servers]; power = new double[servers];
            gpu = new int[servers]; count = new int[servers]; latencies = new double[services];
            this.details = details;
        }
    }
    Work newWork(boolean details) { return new Work(4, 12, details); }
    public Evaluation evaluate(Candidate candidate) {
        Work work = newWork(true);
        measure(candidate == null ? null : candidate.copy(), work);
        return snapshot(work);
    }
    private void reject(Work w, String reason) {
        w.feasible = false;
        if (w.details) w.violations.add(reason);
    }
    void measure(int[] assignment, Work w) {
        Arrays.fill(w.cpu, 0); Arrays.fill(w.ram, 0); Arrays.fill(w.storage, 0);
        Arrays.fill(w.busy, 0); Arrays.fill(w.power, 0); Arrays.fill(w.gpu, 0);
        Arrays.fill(w.count, 0); Arrays.fill(w.latencies, 0);
        w.violations.clear(); w.feasible = true;
        w.makespan = w.energy = w.worst = w.delay = w.cost = 0; w.sla = 0;
        if (assignment == null || assignment.length != 12) {
            reject(w, "Assign each of the 12 services exactly once");
            w.score = Double.POSITIVE_INFINITY; w.delay = Double.NaN; return;
        }
        for (int i = 0; i < 12; i++) {
            int j = assignment[i];
            if (j < 0 || j >= 4) { reject(w, "Invalid server for service " + (i+1)); continue; }
            var service = data.services().get(i);
            var server = data.servers().get(j);
            w.cpu[j] += service.cpu(); w.ram[j] += service.ram(); w.storage[j] += service.storage();
            w.gpu[j] += service.gpu(); w.count[j]++;
            w.busy[j] += service.work() / server.speed();
            double latency = networkLatency(i, j);
            w.latencies[i] = latency; w.delay += latency; w.worst = Math.max(w.worst, latency);
            if (latency > service.maxLatency() + 1e-9) {
                w.sla++; reject(w, service.name() + ": network SLA exceeded (" + latency + " > " + service.maxLatency() + " ms)");
            }
        }
        for (int j = 0; j < 4; j++) {
            var server = data.servers().get(j);
            if (w.cpu[j] > server.cpu() + 1e-9) reject(w, server.name() + ": CPU overload");
            if (w.ram[j] > server.ram() + 1e-9) reject(w, server.name() + ": RAM overload");
            if (w.storage[j] > server.storage() + 1e-9) reject(w, server.name() + ": storage overload");
            if (w.gpu[j] > server.gpu()) reject(w, server.name() + ": GPU slots exceeded");
            if (w.count[j] > server.maxServices()) reject(w, server.name() + ": maximum service count exceeded");
            w.makespan = Math.max(w.makespan, w.busy[j]);
            w.power[j] = w.busy[j] == 0 ? 0 : server.idlePower() + (server.peakPower()-server.idlePower())*w.cpu[j]/server.cpu();
            w.energy += w.power[j] * w.busy[j] / 3600;
            w.cost += server.busyPrice() * w.busy[j];
        }
        w.delay /= 12;
        w.score = w.feasible ? assessmentScore(w.makespan, w.cost, w.energy, w.delay) : Double.POSITIVE_INFINITY;
    }
    Evaluation snapshot(Work w) {
        var usage = new ArrayList<Evaluation.Usage>();
        var latencies = new ArrayList<Double>();
        for (int j = 0; j < 4; j++) usage.add(new Evaluation.Usage(w.cpu[j], w.ram[j], w.storage[j], w.count[j], w.gpu[j], w.busy[j], w.power[j]));
        for (double latency : w.latencies) latencies.add(latency);
        return new Evaluation(w.feasible, w.score, w.delay, w.cost, usage, w.violations, w.makespan, w.energy, w.worst, w.sla, latencies);
    }
    public double networkLatency(int service, int server) {
        return data.servers().get(server).latencyMs() + 8000 * data.services().get(service).payload() / data.servers().get(server).bandwidth();
    }
    public static double assessmentScore(double makespan, double cost, double energy, double latency) {
        return .35 * makespan / 30 + .20 * cost / .50 + .15 * energy / 5 + .30 * latency / 70;
    }
    public boolean compatible(int i, int j) {
        var service = data.services().get(i); var server = data.servers().get(j);
        return service.cpu() <= server.cpu() + 1e-9 && service.ram() <= server.ram() + 1e-9
                && service.storage() <= server.storage() + 1e-9 && service.gpu() <= server.gpu()
                && server.maxServices() > 0 && networkLatency(i,j) <= service.maxLatency() + 1e-9;
    }
    /** A ranking estimate for construction; the complete evaluator decides final quality. */
    public double contribution(int i, int j) {
        var service = data.services().get(i); var server = data.servers().get(j);
        double busy = service.work() / server.speed();
        double power = server.idlePower() + (server.peakPower()-server.idlePower())*service.cpu()/server.cpu();
        return assessmentScore(busy, server.busyPrice()*busy, power*busy/3600, networkLatency(i,j)/12);
    }
    public double optimisticBound() { return 0; }
}
