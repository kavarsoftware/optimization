package optimizer;

public enum SearchMethod {
    LOCAL_SEARCH("Restart local search"), RANDOM("Random baseline"), EXACT("Exact verification");
    private final String label;
    SearchMethod(String label) { this.label = label; }
    public String toString() { return label; }
    public String shortName() { return label; }
}
