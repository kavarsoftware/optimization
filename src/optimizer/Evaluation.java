package optimizer;

import java.util.List;

/** Measured quality plus explicit evidence about mandatory rules. */
public record Evaluation(boolean feasible, double score, double delayMs, double cost,
                         List<Usage> usage, List<String> violations) {
    public record Usage(double cpu, double ram, double storage, int count) {}
    public Evaluation { usage = List.copyOf(usage); violations = List.copyOf(violations); }
}
