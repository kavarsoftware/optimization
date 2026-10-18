package optimizer.brain;



/** An actual, sampled search decision. Animation never creates or evaluates extra candidates. */
public record SearchEvent(
        int sequence,
        Type type,
        int evaluations,
        int feasible,
        int unique,
        int restarts,
        long milliseconds,
        Candidate before,
        Evaluation beforeEvaluation,
        Candidate candidate,
        Evaluation evaluation,
        Candidate best,
        Evaluation bestEvaluation,
        boolean newBest,
        String detail) {
    public enum Type {
        EXACT_SCAN,
        CONSTRUCT,
        RANDOM_SAMPLE,
        MOVE,
        SWAP,
        ACCEPT,
        LOCAL_OPTIMUM,
        CHECKPOINT,
        FINISH
    }
}
