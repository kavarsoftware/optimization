package optimizer.brain;



public final class ResultComparison {
    public static String unit(Evaluator evaluator) { return "J"; }
    public static boolean sameModel(Evaluator a, Evaluator b) {
        return a != null && b != null && a.data.servers().equals(b.data.servers())
                && a.data.services().equals(b.data.services());
    }
}
