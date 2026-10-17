package optimizer;

/** A zero patience value disables early stopping for equal-budget experiments. */
public record SearchConfig(long seed, int budget, int batchSize, int patience, double greediness) {
    public SearchConfig {
        if (budget < 1 || budget > 1_000_000) throw new IllegalArgumentException("Budget must be 1..1,000,000");
        if (batchSize < 1 || patience < 0 || !Double.isFinite(greediness) || greediness < 0 || greediness > 1)
            throw new IllegalArgumentException("Invalid search settings");
    }
    public static SearchConfig defaults() { return new SearchConfig(2026,1_000_000,10_000,3,.35); }
}
