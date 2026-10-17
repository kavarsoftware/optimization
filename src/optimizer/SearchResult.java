package optimizer;

import java.util.List;

public record SearchResult(SearchMethod method, SearchConfig config, Candidate best,
                           Evaluation evaluation, int evaluations, int feasible, int unique,
                           int constructions, int failedConstructions, int restarts,
                           long milliseconds, String stopReason, List<Point> trace) {
    public record Point(int evaluations, double score) {}
    public SearchResult { trace = List.copyOf(trace); }
}
