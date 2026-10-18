package optimizer.brain;



import java.util.List;

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
    public boolean completeEnumeration(long searchSpace) {
        return method == SearchMethod.EXACT
                && evaluations == searchSpace
                && !stopReason.contains("Cancelled");
    }

    public boolean provenOptimal(long searchSpace) {
        return completeEnumeration(searchSpace) && best != null;
    }

    public record Point(int evaluations, double score) {}

    public SearchResult {
        trace = List.copyOf(trace);
    }
}
