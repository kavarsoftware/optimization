package optimizer;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** Same evaluator, paired seeds and full evaluation budgets. Timings include construction work. */
public final class ExperimentRunner {
    public record Trial(long seed, SearchMethod method, SearchResult result, String protocol) {
        public Trial(long seed, SearchMethod method, SearchResult result){this(seed,method,result,method==SearchMethod.EXACT?"exact":"equal budget");}
    }
    public static List<Trial> compare(Evaluator e,long firstSeed,int runs,int budget,Consumer<Trial> listener,BooleanSupplier cancel) {
        List<Trial> trials=new ArrayList<>();
        var exact=new Optimizer().run(e,SearchMethod.EXACT,new SearchConfig(0,budget,10_000,0,.35),null,cancel);
        Trial reference=new Trial(0,SearchMethod.EXACT,exact);trials.add(reference);if(listener!=null)listener.accept(reference);
        SearchMethod[] methods={SearchMethod.HYBRID,SearchMethod.CONSTRUCTIVE,SearchMethod.RANDOM};
        for(int i=0;i<runs && !cancel.getAsBoolean();i++) {
            // Rotate execution order so one method is not always the first to encounter JVM warmup.
            for(int j=0;j<methods.length && !cancel.getAsBoolean();j++) {
                SearchMethod method=methods[(i+j)%methods.length];
                var result=new Optimizer().run(e,method,new SearchConfig(firstSeed+i,budget,10_000,0,.35),null,cancel);
                Trial trial=new Trial(firstSeed+i,method,result);trials.add(trial);if(listener!=null)listener.accept(trial);
            }
        }
        // A separate protocol retains the hybrid's natural stopping point and pairs Random at that budget.
        for(int i=0;i<runs && !cancel.getAsBoolean();i++) {
            var own=new Optimizer().run(e,SearchMethod.HYBRID,new SearchConfig(firstSeed+i,budget,10_000,3,.35),null,cancel);
            if(own.evaluations()==0) break;
            var random=new Optimizer().run(e,SearchMethod.RANDOM,new SearchConfig(firstSeed+i,own.evaluations(),10_000,0,.35),null,cancel);
            for(var t:List.of(new Trial(firstSeed+i,SearchMethod.HYBRID,own,"own stopping"),new Trial(firstSeed+i,SearchMethod.RANDOM,random,"matched stopping budget"))) {trials.add(t);if(listener!=null)listener.accept(t);}
        }
        return List.copyOf(trials);
    }
    public static void write(Path directory,Evaluator evaluator,List<Trial> trials)throws Exception {
        Files.createDirectories(directory);
        List<String> lines=new ArrayList<>();lines.add("protocol,"+RunRecords.HEADER);
        for(var t:trials) lines.add(CsvFiles.row(t.protocol())+","+RunRecords.row(evaluator,t.result()));
        Files.write(directory.resolve("trials.csv"),lines,StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("summary.csv"),summary(evaluator,trials),StandardCharsets.UTF_8);
        if(!trials.isEmpty()) {
            var reference=trials.get(0).result();
            CsvFiles.export(directory.resolve("experiment_settings"),evaluator,reference);
        }
    }
    public static String summary(Evaluator e,List<Trial> trials) {
        double optimum=trials.stream().filter(t->RunRecords.proven(t.result())&&t.result().evaluation()!=null).mapToDouble(t->t.result().evaluation().score()).min().orElse(Double.NaN);
        StringBuilder b=new StringBuilder(CsvFiles.row("protocol","method","score_unit","best","mean","worst","success_rate_percent (feasible runs)","mean_evaluations","mean_unique_space_percent","mean_runtime_ms","gap_to_exact_percent (best)","proven_optimal?","confidence")+"\n");
        for(String protocol:List.of("exact","equal budget","own stopping","matched stopping budget")) for(var method:SearchMethod.values()) {
            var list=trials.stream().filter(t->t.method()==method&&t.protocol().equals(protocol)).toList();if(list.isEmpty())continue;
            double[] values=list.stream().filter(t->t.result().evaluation()!=null).mapToDouble(t->t.result().evaluation().score()).toArray();
            double best=Arrays.stream(values).min().orElse(Double.NaN);
            Object gap=!Double.isFinite(optimum)||!Double.isFinite(best)?"NA":Math.abs(optimum)<1e-12?(Math.abs(best)<1e-12?0:"NA (zero optimum)"):100*(best-optimum)/Math.abs(optimum);
            boolean proven=list.stream().allMatch(t->RunRecords.proven(t.result()));
            b.append(CsvFiles.row(protocol,method.name(),RunRecords.unit(e),best,Arrays.stream(values).average().orElse(Double.NaN),Arrays.stream(values).max().orElse(Double.NaN),100.*values.length/list.size(),list.stream().mapToInt(t->t.result().evaluations()).average().orElse(0),list.stream().mapToDouble(t->100.*t.result().unique()/e.data.searchSpace()).average().orElse(0),list.stream().mapToLong(t->t.result().milliseconds()).average().orElse(0),gap,proven,proven?"proven":"best found")).append('\n');
        }
        return b.toString();
    }

}
