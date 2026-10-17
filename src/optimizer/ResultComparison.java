package optimizer;

/** Compare the selected objective, with feasibility and model compatibility kept explicit. */
public record ResultComparison(
        Reading previous,
        Reading current,
        String previousLabel,
        String currentLabel,
        Outcome outcome,
        String note) {
    public enum Outcome {
        FIRST,
        BETTER,
        SAME,
        WORSE,
        INFEASIBLE,
        UNAVAILABLE,
        INCOMPARABLE
    }

    public record Reading(double value, String unit, boolean available, boolean feasible) {
        static Reading of(Evaluation e, Evaluator model) {
            return e == null
                    ? new Reading(Double.NaN, ResultComparison.unit(model), false, false)
                    : new Reading(e.score(), ResultComparison.unit(model), true, e.feasible());
        }

        public boolean valid() {
            return available && feasible && Double.isFinite(value);
        }
    }

    public double improvement() {
        return comparable() ? previous.value() - current.value() : Double.NaN;
    }

    public double percent() {
        return comparable() && Math.abs(previous.value()) > 1e-12
                ? 100 * improvement() / Math.abs(previous.value())
                : Double.NaN;
    }

    public boolean comparable() {
        return previous.valid() && current.valid() && outcome != Outcome.INCOMPARABLE;
    }

    public static ResultComparison between(
            Evaluator model,
            Evaluation before,
            Evaluation after,
            String beforeLabel,
            String afterLabel,
            String note) {
        return create(
                Reading.of(before, model),
                Reading.of(after, model),
                beforeLabel,
                afterLabel,
                note,
                true);
    }

    public static ResultComparison step(
            Evaluator model, SearchEvent event, Evaluation previousRecordedBest) {
        Evaluation before =
                event.beforeEvaluation() != null ? event.beforeEvaluation() : previousRecordedBest;
        String beforeLabel =
                event.beforeEvaluation() != null ? "PREVIOUS PLACEMENT" : "PREVIOUS BEST";
        String afterLabel =
                switch (event.type()) {
                    case MOVE, SWAP -> "CURRENT PROPOSAL";
                    case ACCEPT -> "ACCEPTED PLACEMENT";
                    case CONSTRUCT -> "NEW START";
                    case RANDOM_SAMPLE -> "CURRENT SAMPLE";
                    default -> "CURRENT BEST";
                };
        return between(
                model,
                before,
                event.evaluation(),
                beforeLabel,
                afterLabel,
                "Selected objective, lower is better. A proposal is accepted only after local"
                        + " descent selects an improvement.");
    }

    public static ResultComparison runs(
            Evaluator previousModel,
            SearchResult previousRun,
            Evaluator currentModel,
            SearchResult currentRun) {
        String currentLabel =
                currentRun != null && currentRun.stopReason().contains("Cancelled")
                        ? "CURRENT RUN (STOPPED)"
                        : "CURRENT RUN (FINAL)";
        Reading before =
                Reading.of(
                        previousRun == null ? null : previousRun.evaluation(),
                        previousModel == null ? currentModel : previousModel);
        Reading after =
                Reading.of(currentRun == null ? null : currentRun.evaluation(), currentModel);
        boolean compatible = previousModel == null || sameModel(previousModel, currentModel);
        String note =
                previousRun == null
                        ? "Run the optimizer again to compare two completed runs in this session."
                        : compatible
                                ? "Final or stopped-run results. Seeds, methods and budgets may"
                                        + " differ; use Experiments for a controlled benchmark."
                                : "Inputs, constraints or objective changed. These scores cannot"
                                        + " support a direct improvement claim.";
        return create(before, after, "PREVIOUS RUN (FINAL)", currentLabel, note, compatible);
    }

    private static ResultComparison create(
            Reading previous,
            Reading current,
            String previousLabel,
            String currentLabel,
            String note,
            boolean compatible) {
        Outcome outcome;
        if (!compatible) outcome = Outcome.INCOMPARABLE;
        else if (!current.available()) outcome = Outcome.UNAVAILABLE;
        else if (!current.valid()) outcome = Outcome.INFEASIBLE;
        else if (!previous.valid()) outcome = Outcome.FIRST;
        else {
            double delta = previous.value() - current.value();
            outcome =
                    Math.abs(delta) < 1e-10
                            ? Outcome.SAME
                            : delta > 0 ? Outcome.BETTER : Outcome.WORSE;
        }
        return new ResultComparison(previous, current, previousLabel, currentLabel, outcome, note);
    }

    static boolean sameModel(Evaluator a, Evaluator b) {
        return a != null
                && b != null
                && a.objective == b.objective
                && a.minServices == b.minServices
                && (a.objective != Evaluator.Objective.BALANCED
                        || Double.compare(a.alpha, b.alpha) == 0)
                && a.data.servers().equals(b.data.servers())
                && a.data.services().equals(b.data.services());
    }

    static String unit(Evaluator e) {
        return e == null
                ? ""
                : switch (e.objective) {
                    case LATENCY, WEIGHTED -> "ms";
                    case COST -> "cost";
                    case BALANCED -> "score";
                    case ASSESSMENT -> "J";
                };
    }
}
