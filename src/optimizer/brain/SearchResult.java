package optimizer.brain;

import java.util.List;

/**
 * Captures the outcome of a complete optimization run, including the best candidate,
 * evidence summary, and the checkpoints accumulated while searching.
 */
public record SearchResult(
        SearchMethod method,
        SearchConfig config,
        Candidate best,
        Evaluation evaluation,
        int evaluations,
        int feasible,
        int unique,
        int constructions,
        int failedConstructions,
        int restarts,
        long milliseconds,
        String stopReason,
        List<Point> trace) {
    /**
     * Reports whether the exact method reached and validated the full search space.
     */
    public boolean completeEnumeration(long searchSpace) {
        return method == SearchMethod.EXACT
                && evaluations == searchSpace
                && !stopReason.contains("Cancelled");
    }

    /**
     * Reports whether the exact method both completed full enumeration and found a feasible optimum.
     */
    public boolean provenOptimal(long searchSpace) {
        return completeEnumeration(searchSpace) && best != null;
    }

    /**
     * A single milestone in the run, measuring how the best score changed over time.
     */
    public record Point(int evaluations, double score) {}

    /**
     * Ensures the trace is unmodifiable once the result is published.
     */
    public SearchResult {
        trace = List.copyOf(trace);
    }
}
