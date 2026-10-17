package optimizer;

import java.util.List;

/** Resource reservations and separate compute/network/runtime quantities. */
public record Evaluation(
        boolean feasible,
        double score,
        double delayMs,
        double cost,
        List<Usage> usage,
        List<String> violations,
        double makespanSeconds,
        double energyWh,
        double worstLatencyMs,
        int slaViolations,
        List<Double> serviceLatencies) {
    public Evaluation(
            boolean feasible,
            double score,
            double delayMs,
            double cost,
            List<Usage> usage,
            List<String> violations) {
        this(feasible, score, delayMs, cost, usage, violations, 0, 0, delayMs, 0, List.of());
    }

    public record Usage(
            double cpu,
            double ram,
            double storage,
            int count,
            int gpu,
            double busySeconds,
            double powerWatts) {
        public Usage(double cpu, double ram, double storage, int count) {
            this(cpu, ram, storage, count, 0, 0, 0);
        }
    }

    public Evaluation {
        usage = List.copyOf(usage);
        violations = List.copyOf(violations);
        serviceLatencies = List.copyOf(serviceLatencies);
    }
}
