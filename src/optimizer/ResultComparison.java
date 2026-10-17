package optimizer;

final class ResultComparison {
    static String unit(Evaluator evaluator) { return "J"; }
    static boolean sameModel(Evaluator a, Evaluator b) {
        return a != null && b != null && a.data.servers().equals(b.data.servers())
                && a.data.services().equals(b.data.services());
    }
}
