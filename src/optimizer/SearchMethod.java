package optimizer;

public enum SearchMethod {
    HYBRID("Smart Restart Search (SRS)"),
    EXACT("Exact Search"),
    RANDOM("Random Search"),
    TARGET("Target Search"),
    CONSTRUCTIVE("Randomized greedy construction"),
    HILL_CLIMB("Restart hill climbing"),
    ANNEALING("Simulated annealing"),
    TABU("Tabu search"),
    GENETIC("Genetic algorithm"),
    ITERATED("Iterated local search"),
    VNS("Variable neighborhood search"),
    BEAM("Beam search");
    private final String label;

    SearchMethod(String label) {
        this.label = label;
    }

    public String toString() {
        return label;
    }

    public boolean advanced() {
        return switch (this) {
            case HILL_CLIMB, ANNEALING, TABU, GENETIC, ITERATED, VNS, BEAM -> true;
            default -> false;
        };
    }

    public String shortName() {
        return switch (this) {
            case HYBRID -> "SRS / GRASP";
            case EXACT -> "Exact reference";
            case RANDOM -> "Random";
            case TARGET -> "Target search";
            case CONSTRUCTIVE -> "Greedy construction";
            case HILL_CLIMB -> "Hill climbing";
            case ANNEALING -> "Annealing";
            case TABU -> "Tabu";
            case GENETIC -> "Genetic";
            case ITERATED -> "Iterated local";
            case VNS -> "VNS";
            case BEAM -> "Beam";
        };
    }
}
