package optimizer;

public enum SearchMethod {
    EXACT("Exact enumeration"), HYBRID("GRASP + move / swap descent"), CONSTRUCTIVE("Randomized greedy construction"), RANDOM("Uniform random baseline");
    private final String label;
    SearchMethod(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
