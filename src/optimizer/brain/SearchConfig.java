package optimizer.brain;



/** Patience counts consecutive batches without improvement; zero disables early stopping. */
public record SearchConfig(long seed, int budget, int batchSize, int patience, double greediness) {
    public SearchConfig {
        if (budget < 1 || batchSize < 1 || patience < 0
                || !Double.isFinite(greediness) || greediness < 0 || greediness > 1)
            throw new IllegalArgumentException("Positive budgets/batches, nonnegative patience and greediness in [0,1] are required");
    }
}
