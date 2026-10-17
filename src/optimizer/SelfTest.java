package optimizer;

public final class SelfTest {
    private static int checks;
    static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void run() throws Exception {
        AssessmentTest.run();
        Evaluator model = new Evaluator(ProblemData.assessmentDefaults());
        SearchConfig settings = new SearchConfig(2026, 75, 100, 0, .35);
        for (SearchMethod method : new SearchMethod[] {SearchMethod.LOCAL_SEARCH, SearchMethod.RANDOM}) {
            SearchResult run = new Optimizer().run(model, method, settings, null, () -> false);
            check(run.evaluations() == 75, "Both search and baseline honor the requested budget");
        }
        SearchResult exact = new Optimizer().run(model, SearchMethod.EXACT, settings, null, () -> false);
        check(exact.provenOptimal(model.data.searchSpace()), "Complete Exact run proves optimality");
        check(exact.feasible() == 92643, "Exact reproduces the benchmark's feasible count");
        check(Math.abs(exact.evaluation().score() - .7151543595803707) < 1e-12, "Exact reproduces the benchmark optimum");
        SearchControl control = new SearchControl(0);
        control.pause();
        var stopped = new java.util.concurrent.atomic.AtomicReference<SearchResult>();
        Thread worker = new Thread(() -> stopped.set(new Optimizer().run(model, SearchMethod.RANDOM,
                settings, null, control::stopped, null, control)));
        worker.start();
        Thread.sleep(30);
        control.stop();
        worker.join(2000);
        check(!worker.isAlive() && stopped.get().evaluations() == 0, "Stop while paused performs no extra evaluation");
        var partial = java.nio.file.Files.createTempDirectory("placement-stopped-study-");
        AssessmentExperiments.additional(partial, model, settings, 10, null, () -> true);
        check(java.nio.file.Files.exists(partial.resolve("assessment-sensitivity.csv")), "Cancelled study preserves partial records without failing chart export");
        System.out.println("PASS: " + checks + " assessment checks");
    }
}
