package optimizer;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** One durable, self-contained record for every run, including cancelled runs and sweeps. */
public final class RunRecords {
    public static final String HEADER="instance,method,version,objective,score_unit,alpha,minimum_services,budget_evaluations,batch_evaluations,patience_batches,greediness,stopping_condition,seed,best_score,feasible,evaluations,unique_candidates,runtime_ms,java_version,os,proven_optimal";
    public static boolean proven(SearchResult r){return r.evaluation()!=null&&r.method()==SearchMethod.EXACT&&"Exact enumeration complete".equals(r.stopReason());}
    public static String unit(Evaluator e){return switch(e.objective){case LATENCY,WEIGHTED->"ms";case COST->"illustrative cost units / period";case BALANCED->"dimensionless";};}
    public static String row(Evaluator e,SearchResult r){return CsvFiles.row(e.data.name(),r.method().name(),Optimizer.VERSION,e.objective.name(),unit(e),e.alpha,e.minServices,r.method()==SearchMethod.EXACT?"not applicable":r.config().budget(),r.config().batchSize(),r.config().patience(),r.config().greediness(),r.stopReason(),r.method()==SearchMethod.EXACT?"NA":r.config().seed(),r.evaluation()==null?"NA":r.evaluation().score(),r.evaluation()!=null,r.evaluations(),r.unique(),r.milliseconds(),System.getProperty("java.version"),System.getProperty("os.name"),proven(r));}
    public static synchronized void save(Evaluator e,SearchResult r){
        try{Path p=Path.of("results","run-records.csv");Files.createDirectories(p.getParent());if(!Files.exists(p))Files.writeString(p,HEADER+"\n",StandardCharsets.UTF_8);Files.writeString(p,row(e,r)+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);}
        catch(IOException ex){throw new UncheckedIOException("Cannot save experiment record",ex);}
    }
}
