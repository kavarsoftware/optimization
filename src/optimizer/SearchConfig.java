package optimizer;

/** Patience counts complete stagnant batches. Zero disables batch stopping. */
public record SearchConfig(
        long seed, int budget, int batchSize, int patience, double greediness, double target) {
    public SearchConfig(long seed, int budget, int batchSize, int patience, double greediness) {
        this(seed, budget, batchSize, patience, greediness, 18.0);
    }

    public SearchConfig {
        if (budget < 1) throw new IllegalArgumentException("Budget must be positive");
        if (batchSize < 1
                || patience < 0
                || !Double.isFinite(greediness)
                || greediness < 0
                || greediness > 1
                || !Double.isFinite(target))
            throw new IllegalArgumentException("Invalid search settings");
    }

    public static SearchConfig defaults() {
        return new SearchConfig(2026, 1_000_000, 10_000, 1, .35, 18);
    }
}
