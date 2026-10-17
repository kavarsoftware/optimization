package optimizer;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** Same evaluator, paired seeds and full evaluation budgets. Timings include construction work. */
public final class ExperimentRunner {
    public record Trial(long seed, SearchMethod method, SearchResult result) {}
    public static List<Trial> compare(Evaluator e,long firstSeed,int runs,int budget,Consumer<Trial> listener,BooleanSupplier cancel) {
        List<Trial> trials=new ArrayList<>(); SearchMethod[] methods=SearchMethod.values();
        for(int i=0;i<runs && !cancel.getAsBoolean();i++) {
            // Rotate execution order so one method is not always the first to encounter JVM warmup.
            for(int j=0;j<methods.length && !cancel.getAsBoolean();j++) {
                SearchMethod method=methods[(i+j)%methods.length];
                var result=new Optimizer().run(e,method,new SearchConfig(firstSeed+i,budget,10_000,0,.35),null,cancel);
                Trial trial=new Trial(firstSeed+i,method,result);trials.add(trial);if(listener!=null)listener.accept(trial);
            }
        }
        return List.copyOf(trials);
    }
    public static void write(Path directory,Evaluator evaluator,List<Trial> trials)throws Exception {
        Files.createDirectories(directory);
        List<String> lines=new ArrayList<>();lines.add("seed,method,best_score,delay_ms,cost,evaluations,unique,feasible,runtime_ms,stop_reason");
        for(var t:trials) {
            var r=t.result();var e=r.evaluation();
            lines.add(CsvFiles.row(t.seed(),t.method().name(),e==null?"NA":e.score(),e==null?"NA":e.delayMs(),e==null?"NA":e.cost(),
                r.evaluations(),r.unique(),r.feasible(),r.milliseconds(),r.stopReason()));
        }
        Files.write(directory.resolve("trials.csv"),lines,StandardCharsets.UTF_8);
        lines=new ArrayList<>();lines.add("method,runs,feasible_runs,best,mean,median,sample_sd,worst,mean_evaluations,mean_runtime_ms");
        for(var method:SearchMethod.values()) {
            var selected=trials.stream().filter(t->t.method()==method).toList();
            double[] values=selected.stream().filter(t->t.result().evaluation()!=null).mapToDouble(t->t.result().evaluation().score()).sorted().toArray();
            double mean=Arrays.stream(values).average().orElse(Double.NaN), variance=0;
            for(double v:values)variance+=(v-mean)*(v-mean);
            double median=values.length==0?Double.NaN:values.length%2==1?values[values.length/2]:(values[values.length/2-1]+values[values.length/2])/2;
            lines.add(CsvFiles.row(method.name(),selected.size(),values.length,values.length==0?"NA":values[0],mean,median,
                values.length<2?Double.NaN:Math.sqrt(variance/(values.length-1)),values.length==0?"NA":values[values.length-1],
                selected.stream().mapToInt(t->t.result().evaluations()).average().orElse(0),
                selected.stream().mapToLong(t->t.result().milliseconds()).average().orElse(0)));
        }
        Files.write(directory.resolve("summary.csv"),lines,StandardCharsets.UTF_8);
        if(!trials.isEmpty()) {
            var reference=trials.get(0).result();
            CsvFiles.export(directory.resolve("experiment_settings"),evaluator,reference);
        }
    }
}
