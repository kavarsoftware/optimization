package optimizer.brain;

import optimizer.experiments.*;
import optimizer.ui.*;

import static optimizer.brain.SelfTest.check;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/** Named assessment tests make the PDF acceptance evidence easy to audit. */
public final class AssessmentTest {
    private static void near(double value, double expected, double tolerance, String label) {
        check(Math.abs(value - expected) <= tolerance, label + " / got " + value);
    }

    private static ProblemData replaceServer(
            ProblemData data, int index, ProblemData.Server server) {
        List<ProblemData.Server> servers = new ArrayList<>(data.servers());
        servers.set(index, server);
        return new ProblemData("test", servers, data.services(), true);
    }

    private static ProblemData.Server change(
            ProblemData.Server s,
            double cpu,
            int gpu,
            int max,
            double speed,
            double rtt,
            double bandwidth) {
        return new ProblemData.Server(
                s.name(),
                cpu,
                s.ram(),
                s.storage(),
                rtt,
                gpu,
                max,
                speed,
                s.idlePower(),
                s.peakPower(),
                s.busyPrice(),
                bandwidth);
    }

    private static Evaluator evaluator(ProblemData data) {
        return new Evaluator(data);
    }

    private static void invalid(Runnable task, String label) {
        boolean caught = false;
        try {
            task.run();
        } catch (IllegalArgumentException ex) {
            caught = true;
        }
        check(caught, label);
    }

    public static void run() throws Exception {
        ProblemData data = ProblemData.assessmentDefaults();
        Evaluator e = evaluator(data);
        Candidate reference = ProblemData.referenceAssignment();
        Evaluation ref = e.evaluate(reference);
        SearchConfig config = new SearchConfig(2026, 5000, 1000, 0, .35);
        List<SearchEvent> events = new ArrayList<>();
        SearchResult result =
                new Optimizer().run(e, SearchMethod.LOCAL_SEARCH, config, null, () -> false, events::add);
        check(
                result.best() != null && e.evaluate(result.best()).feasible(),
                "T1 default benchmark returns a feasible best");
        for (SearchEvent event : events)
            if (event.bestEvaluation() != null)
                check(
                        event.bestEvaluation().feasible() && e.evaluate(event.best()).feasible(),
                        "T1 every accepted incumbent satisfies all hard constraints");
        int[] values = reference.copy();
        values[5] = 0;
        check(!e.evaluate(new Candidate(values)).feasible(), "T2 S06 on A is GPU-incompatible");
        values = reference.copy();
        values[3] = 0;
        Evaluation cpu = e.evaluate(new Candidate(values));
        check(!cpu.feasible() && cpu.usage().get(0).cpu() == 21, "T3 A overload is 21 vCPU > 16");
        ProblemData.Server c = data.servers().get(2);
        ProblemData noGpu =
                replaceServer(
                        data,
                        2,
                        change(
                                c,
                                c.cpu(),
                                0,
                                c.maxServices(),
                                c.speed(),
                                c.latencyMs(),
                                c.bandwidth()));
        SearchResult impossible =
                new Optimizer()
                        .run(evaluator(noGpu), SearchMethod.LOCAL_SEARCH, config, null, () -> false);
        check(
                impossible.best() == null
                        && impossible.stopReason().startsWith("Infeasible:")
                        && impossible.evaluations() == 0,
                "T4 aggregate GPU shortage proves infeasibility before searching");
        ProblemData.Server a = data.servers().get(0);
        ProblemData equality =
                replaceServer(
                        data,
                        0,
                        change(
                                a,
                                14,
                                a.gpu(),
                                a.maxServices(),
                                a.speed(),
                                a.latencyMs(),
                                a.bandwidth()));
        check(
                evaluator(equality).evaluate(reference).feasible(),
                "T5 exactly 14/14 vCPU is feasible");
        SearchResult again = new Optimizer().run(e, SearchMethod.LOCAL_SEARCH, config, null, () -> false);
        check(
                Arrays.equals(result.best().copy(), again.best().copy())
                        && result.evaluations() == again.evaluations()
                        && result.unique() == again.unique(),
                "T6 seeded result, evaluations and unique count reproduce");
        invalid(
                () ->
                        replaceServer(
                                data,
                                0,
                                change(
                                        a,
                                        a.cpu(),
                                        a.gpu(),
                                        a.maxServices(),
                                        0,
                                        a.latencyMs(),
                                        a.bandwidth())),
                "T7 speed zero rejected before search");
        invalid(
                () ->
                        replaceServer(
                                data,
                                0,
                                change(
                                        a,
                                        -1,
                                        a.gpu(),
                                        a.maxServices(),
                                        a.speed(),
                                        a.latencyMs(),
                                        a.bandwidth())),
                "T7 negative capacity rejected before search");
        double max = 0;
        for (int s = 0; s < 4; s++) {
            double time = 0;
            String previous = "";
            for (int i : AssessmentPlots.order(data, reference, s)) {
                check(
                        previous.compareTo(data.services().get(i).name()) <= 0,
                        "T8 timeline uses ascending service ID");
                previous = data.services().get(i).name();
                time += data.services().get(i).work() / data.servers().get(s).speed();
            }
            near(time, ref.usage().get(s).busySeconds(), 1e-10, "T8 timeline total matches B");
            max = Math.max(max, time);
        }
        near(max, ref.makespanSeconds(), 1e-10, "T8 maximum timeline total matches M");
        values = reference.copy();
        values[2] = 2;
        Evaluation sla = e.evaluate(new Candidate(values));
        near(e.networkLatency(2, 2), 70, 1e-10, "T9 S03 on C network latency");
        check(
                !sla.feasible() && sla.slaViolations() > 0,
                "T9 70 ms violates S03's 60 ms SLA regardless of objective");
        invalid(
                () -> replaceServer(data, 1, change(data.servers().get(1), 24, 0, 5, 20, 30, 0)),
                "T10 zero bandwidth rejected");
        invalid(
                () -> replaceServer(data, 1, change(data.servers().get(1), 24, 0, 5, 20, -1, 800)),
                "T10 negative RTT rejected");
        Evaluator slower =
                evaluator(
                        replaceServer(
                                data,
                                0,
                                change(
                                        a,
                                        a.cpu(),
                                        a.gpu(),
                                        a.maxServices(),
                                        a.speed(),
                                        a.latencyMs(),
                                        400)));
        Evaluation changed = slower.evaluate(reference);
        check(
                changed.feasible()
                        && changed.delayMs() > ref.delayMs()
                        && changed.score() > ref.score(),
                "T11 less A bandwidth raises L and J for fixed feasible mapping");
        for (int i : new int[] {0, 2, 6})
            check(
                    slower.networkLatency(i, 0) > e.networkLatency(i, 0),
                    "T11 each A service has higher communication latency");
        check(ref.feasible() && ref.slaViolations() == 0, "T12 all reference SLAs pass");
        near(ref.score(), .856845, 5e-7, "T12 reference J");
        near(ref.makespanSeconds(), 28.3333, 5e-5, "T12 reference M seconds");
        near(ref.cost(), .449583, 5e-7, "T12 reference USD");
        near(ref.energyWh(), 3.927416, 5e-7, "T12 reference Wh");
        near(ref.delayMs(), 53.347863, 5e-7, "T12 reference L ms");
        near(ref.worstLatencyMs(), 76.666667, 5e-7, "T12 reference Lmax ms");
        Evaluator.Work fast = e.newWork(false);
        Random random = new Random(4);
        for (int sample = 0; sample < 100; sample++) {
            values = new int[12];
            for (int i = 0; i < 12; i++) values[i] = random.nextInt(4);
            Evaluation rich = e.evaluate(new Candidate(values));
            e.measure(values, fast);
            check(
                    fast.feasible == rich.feasible(),
                    "Assessment fast and detailed feasibility agree");
            near(
                    fast.delay,
                    rich.delayMs(),
                    1e-10,
                    "Assessment fast and detailed network score agree");
            if (rich.feasible())
                near(fast.score, rich.score(), 1e-10, "Assessment fast and detailed J agree");
        }
        Path folder = Files.createTempDirectory("assessment-data-");
        try {
            CsvFiles.writeData(folder, data);
            ProblemData loaded = CsvFiles.read(folder);
            check(
                    loaded.assessment()
                            && loaded.servers().equals(data.servers())
                            && loaded.services().equals(data.services()),
                    "Assessment CSV preserves every resource/network/power/work value");
        } finally {
            Files.delete(folder.resolve("servers.csv"));
            Files.delete(folder.resolve("services.csv"));
            Files.delete(folder);
        }
        checkLiveControl(e);
        System.out.println(
                "PASS: Assessment T1–T12, reference arithmetic, shared evaluator and live control"
                        + " checks");
    }

    private static void checkLiveControl(Evaluator e) throws Exception {
        SearchControl control = new SearchControl(0);
        control.pause();
        List<SearchEvent> events = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<SearchResult> result = new AtomicReference<>();
        Thread worker =
                new Thread(
                        () ->
                                result.set(
                                        new Optimizer()
                                                .run(
                                                        e,
                                                        SearchMethod.RANDOM,
                                                        new SearchConfig(2026, 100, 10, 0, .35),
                                                        null,
                                                        control::stopped,
                                                        events::add,
                                                        control)));
        worker.start();
        Thread.sleep(40);
        check(events.isEmpty(), "Pause blocks real evaluations");
        control.step();
        long end = System.currentTimeMillis() + 2000;
        while (events.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(5);
        Thread.sleep(25);
        check(
                events.size() == 1 && events.get(0).evaluations() == 1,
                "Step permits exactly one real complete evaluation");
        control.resume();
        worker.join(5000);
        check(
                !worker.isAlive() && result.get().evaluations() == 100,
                "Resume completes the remaining real search");
        check(control.waitingNanos() > 0, "Paused time is tracked separately from active search");
        DashboardTest.run();
    }
}
