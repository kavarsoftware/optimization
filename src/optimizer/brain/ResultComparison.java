package optimizer.brain;

/**
 * Utility checks for comparing optimizer runs and verifying that different evaluations
 * are measured against the same service and server model.
 */
public final class ResultComparison {
    /**
     * Returns the canonical objective name used throughout the research project.
     */
    public static String unit(Evaluator evaluator) { return "J"; }

    /**
     * Compares the underlying scenario definition rather than the search output itself.
     */
    public static boolean sameModel(Evaluator a, Evaluator b) {
        return a != null && b != null && a.data.servers().equals(b.data.servers())
                && a.data.services().equals(b.data.services());
    }
}
