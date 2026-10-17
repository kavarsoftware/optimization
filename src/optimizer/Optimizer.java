package optimizer;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** GRASP: randomized capacity-aware construction, strict local descent, independent restarts. */
public final class Optimizer {
    public static final String VERSION = "grasp-vnd-1.0";
    public record Progress(int evaluations, int feasible, int unique, int restarts,
                           Candidate best, Evaluation evaluation, long milliseconds, String phase,
                           List<SearchResult.Point> trace) {}

    public SearchResult run(Evaluator evaluator, SearchMethod method, SearchConfig config,
                            Consumer<Progress> listener, BooleanSupplier cancelled) {
        Context c = new Context(evaluator,method,config,listener,cancelled);
        String impossible = necessaryCheck(evaluator);
        if (impossible != null) c.reason = impossible;
        while (c.running()) {
            if (method == SearchMethod.RANDOM) {
                int[] values = new int[evaluator.data.services().size()];
                for (int i = 0; i < values.length; i++) values[i] = c.random.nextInt(evaluator.data.servers().size());
                c.check(new Candidate(values));
            } else {
                c.constructions++;
                Candidate start = construct(c);
                if (start == null) {
                    c.failed++; c.consecutiveFailures++;
                    if (c.consecutiveFailures >= Math.max(2_000,config.budget()*2L))
                        c.reason = "Construction attempt limit (no infeasibility proof)";
                    continue;
                }
                c.consecutiveFailures=0;
                c.restarts++;
                Evaluation first = c.check(start);
                if (method == SearchMethod.HYBRID && first.feasible() && c.running()) descend(c,start,first);
            }
        }
        c.record(); c.publish("Finished");
        return new SearchResult(method,config,c.best,c.bestEvaluation,c.evaluations,c.feasible,c.unique,
            c.constructions,c.failed,c.restarts,c.elapsed(),c.reason,List.copyOf(c.trace));
    }
    private static String necessaryCheck(Evaluator e) {
        for (var task : e.data.services()) {
            boolean fits = e.data.servers().stream().anyMatch(s -> task.cpu() <= s.cpu()+1e-9
                && task.ram() <= s.ram()+1e-9 && task.storage() <= s.storage()+1e-9);
            if (!fits) return "Infeasible: no server can hold " + task.name();
        }
        double cpu = e.data.services().stream().mapToDouble(ProblemData.Service::cpu).sum();
        double ram = e.data.services().stream().mapToDouble(ProblemData.Service::ram).sum();
        double storage = e.data.services().stream().mapToDouble(ProblemData.Service::storage).sum();
        if (cpu > e.data.servers().stream().mapToDouble(ProblemData.Server::cpu).sum()+1e-9
            || ram > e.data.servers().stream().mapToDouble(ProblemData.Server::ram).sum()+1e-9
            || storage > e.data.servers().stream().mapToDouble(ProblemData.Server::storage).sum()+1e-9)
            return "Infeasible: total demand exceeds total capacity";
        return null;
    }
    private Candidate construct(Context c) {
        var data = c.e.data; int n = data.services().size(), m = data.servers().size();
        Integer[] order = new Integer[n]; double[] priority = new double[n];
        for (int i = 0; i < n; i++) {
            order[i] = i; var task = data.services().get(i); int fits = 0; double pressure = 0;
            for (var host : data.servers()) {
                if (task.cpu() <= host.cpu() && task.ram() <= host.ram() && task.storage() <= host.storage()) fits++;
                pressure = Math.max(pressure,Math.max(task.cpu()/host.cpu(),Math.max(task.ram()/host.ram(),task.storage()/host.storage())));
            }
            priority[i] = 100*(m-fits)+pressure*(.7+.6*c.random.nextDouble());
        }
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->priority[i]).reversed());
        int[] values = new int[n], counts = new int[m];
        double[] cpu = new double[m], ram = new double[m], storage = new double[m];
        double alpha = c.constructions % 5 == 0 ? 1.0 : c.config.greediness();
        for (int k = 0; k < n; k++) {
            if (c.cancelled.getAsBoolean()) return null;
            int i = order[k]; var task = data.services().get(i);
            List<Integer> options = new ArrayList<>(); double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int s = 0; s < m; s++) {
                var host = data.servers().get(s);
                if (cpu[s]+task.cpu() > host.cpu()+1e-9 || ram[s]+task.ram() > host.ram()+1e-9
                    || storage[s]+task.storage() > host.storage()+1e-9) continue;
                int deficit = 0;
                for (int j = 0; j < m; j++) deficit += Math.max(0,c.e.minServices-counts[j]-(j==s?1:0));
                if (deficit > n-k-1) continue;
                options.add(s); double value = c.e.contribution(i,s); low = Math.min(low,value); high = Math.max(high,value);
            }
            if (options.isEmpty()) return null; // discard this partial construction, never report it as a candidate
            double cutoff = low+alpha*(high-low)+1e-12;
            List<Integer> restricted = new ArrayList<>();
            for (int s : options) if (c.e.contribution(i,s) <= cutoff) restricted.add(s);
            int selected = restricted.get(c.random.nextInt(restricted.size()));
            values[i] = selected; cpu[selected]+=task.cpu(); ram[selected]+=task.ram(); storage[selected]+=task.storage(); counts[selected]++;
        }
        return new Candidate(values);
    }
    private void descend(Context c, Candidate current, Evaluation currentEvaluation) {
        int n = current.size(), m = c.e.data.servers().size();
        while (c.running()) {
            Candidate next = current; Evaluation nextEvaluation = currentEvaluation;
            // Inspect a complete move/swap neighborhood unless the global budget/stop rule interrupts it.
            for (int i = 0; i < n && c.running(); i++) {
                for (int s = 0; s < m && c.running(); s++) {
                    if (s == current.server(i)) continue;
                    int[] values = current.copy(); values[i] = s;
                    Candidate candidate = new Candidate(values); Evaluation evaluation = c.check(candidate);
                    if (evaluation.feasible() && evaluation.score() < nextEvaluation.score()-1e-10) { next=candidate; nextEvaluation=evaluation; }
                }
            }
            for (int i = 0; i < n && c.running(); i++) {
                for (int j = i+1; j < n && c.running(); j++) {
                    if (current.server(i) == current.server(j)) continue;
                    int[] values = current.copy(); values[i]=current.server(j); values[j]=current.server(i);
                    Candidate candidate = new Candidate(values); Evaluation evaluation = c.check(candidate);
                    if (evaluation.feasible() && evaluation.score() < nextEvaluation.score()-1e-10) { next=candidate; nextEvaluation=evaluation; }
                }
            }
            if (next == current) return; // local optimum within these neighborhoods, then restart
            current = next; currentEvaluation = nextEvaluation;
        }
    }
    private static final class Context {
        final Evaluator e; final SearchMethod method; final SearchConfig config; final Random random;
        final Consumer<Progress> listener; final BooleanSupplier cancelled; final long start = System.nanoTime();
        final BitSet seen; final Set<Long> sparseSeen; final List<SearchResult.Point> trace = new ArrayList<>();
        Candidate best; Evaluation bestEvaluation; int evaluations, feasible, unique, constructions, failed, restarts, stagnant, consecutiveFailures;
        double checkpointBest = Double.POSITIVE_INFINITY; String reason;
        Context(Evaluator e, SearchMethod method, SearchConfig config, Consumer<Progress> listener, BooleanSupplier cancelled) {
            this.e=e; this.method=method; this.config=config; this.listener=listener; this.cancelled=cancelled; random=new Random(config.seed());
            seen=e.data.searchSpace()<=100_000_000?new BitSet():null; sparseSeen=seen==null?new HashSet<>():null;
        }
        boolean running() {
            if (reason != null) return false;
            if (cancelled.getAsBoolean()) reason="Cancelled by user";
            else if (evaluations >= config.budget()) reason="Evaluation budget reached";
            return reason==null;
        }
        Evaluation check(Candidate candidate) {
            Evaluation evaluation=e.evaluate(candidate); evaluations++;
            long code=candidate.code(e.data.servers().size());
            if (seen!=null) { if (!seen.get((int)code)) { seen.set((int)code); unique++; } }
            else if (sparseSeen.add(code)) unique++;
            boolean improved=false;
            if (evaluation.feasible()) {
                feasible++;
                if (best==null || evaluation.score()<bestEvaluation.score()-1e-10) {
                    best=candidate; bestEvaluation=evaluation; improved=true;
                }
            }
            if (improved) record();
            if (evaluations%config.batchSize()==0) {
                double value=best==null?Double.POSITIVE_INFINITY:bestEvaluation.score();
                stagnant=value<checkpointBest-1e-10?0:stagnant+1;
                checkpointBest=value;
                if (best!=null && config.patience()>0 && stagnant>=config.patience()) reason="No global improvement in "+stagnant+" complete batches";
                record(); publish("Searching");
            }
            return evaluation;
        }
        void record() {
            if (best==null) return;
            var point=new SearchResult.Point(evaluations,bestEvaluation.score());
            if (!trace.isEmpty() && trace.get(trace.size()-1).evaluations()==evaluations) trace.set(trace.size()-1,point);
            else trace.add(point);
        }
        long elapsed() { return (System.nanoTime()-start)/1_000_000; }
        void publish(String phase) { if (listener!=null) listener.accept(new Progress(evaluations,feasible,unique,restarts,best,bestEvaluation,elapsed(),phase,List.copyOf(trace))); }
    }
}
