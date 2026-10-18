package optimizer.brain;

/**
 * The optimization strategies exposed by the application and the command-line runner.
 */
public enum SearchMethod {
    LOCAL_SEARCH("Restart local search"), RANDOM("Random baseline"), EXACT("Exact verification");
    private final String label;

    /**
     * Stores the display label used in text output and GUI controls.
     */
    SearchMethod(String label) { this.label = label; }

    /**
     * Returns the user-facing strategy name.
     */
    @Override public String toString() { return label; }

    /**
     * Returns a short variant suitable for logs and compact summaries.
     */
    public String shortName() { return label; }
}
