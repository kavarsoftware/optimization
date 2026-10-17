package optimizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** UTF-8, quoted CSV, decimal points independent of the computer's locale. */
public final class CsvFiles {
    private CsvFiles() {}
    public static String row(Object... values) {
        StringJoiner joiner=new StringJoiner(",");
        for (Object value:values) {
            String s=String.valueOf(value);
            joiner.add("\""+s.replace("\"","\"\"")+"\"");
        }
        return joiner.toString();
    }
    static List<String> parse(String line) {
        List<String> cells=new ArrayList<>(); StringBuilder cell=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(ch=='"') { if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){cell.append('"');i++;} else quoted=!quoted; }
            else if(ch==',' && !quoted){cells.add(cell.toString());cell.setLength(0);} else cell.append(ch);
        }
        if(quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        cells.add(cell.toString()); return cells;
    }
    public static ProblemData read(Path directory) throws IOException {
        List<ProblemData.Server> servers=new ArrayList<>(); List<ProblemData.Service> services=new ArrayList<>();
        for(String line:Files.readAllLines(directory.resolve("servers.csv"),StandardCharsets.UTF_8).stream().skip(1).toList()) {
            if(line.isBlank())continue; var c=parse(line);
            if(c.size()!=6)throw new IllegalArgumentException("servers.csv needs 6 columns");
            servers.add(new ProblemData.Server(c.get(0),number(c,1),number(c,2),number(c,3),number(c,4),number(c,5)));
        }
        for(String line:Files.readAllLines(directory.resolve("services.csv"),StandardCharsets.UTF_8).stream().skip(1).toList()) {
            if(line.isBlank())continue; var c=parse(line);
            if(c.size()!=5)throw new IllegalArgumentException("services.csv needs 5 columns");
            services.add(new ProblemData.Service(c.get(0),number(c,1),number(c,2),number(c,3),number(c,4)));
        }
        return new ProblemData(directory.getFileName().toString(),servers,services);
    }
    private static double number(List<String> c,int i){return Double.parseDouble(c.get(i).strip());}
    public static void writeData(Path directory,ProblemData data)throws IOException {
        Files.createDirectories(directory);
        try(var out=Files.newBufferedWriter(directory.resolve("servers.csv"),StandardCharsets.UTF_8)) {
            out.write("server,cpu_cores,ram_gb,storage_gb,latency_ms,demo_cost_per_cpu\n");
            for(var s:data.servers())out.write(row(s.name(),s.cpu(),s.ram(),s.storage(),s.latencyMs(),s.costPerCpu())+"\n");
        }
        try(var out=Files.newBufferedWriter(directory.resolve("services.csv"),StandardCharsets.UTF_8)) {
            out.write("service,cpu_cores,ram_gb,storage_gb,service_latency_ms\n");
            for(var s:data.services())out.write(row(s.name(),s.cpu(),s.ram(),s.storage(),s.latencyValue())+"\n");
        }
    }
    public static void export(Path directory,Evaluator evaluator,SearchResult result)throws IOException {
        Files.createDirectories(directory); writeData(directory.resolve("inputs"),evaluator.data);
        var e=result.evaluation();
        Files.writeString(directory.resolve("experiment_record.csv"),RunRecords.HEADER+"\n"+RunRecords.row(evaluator,result)+"\n",StandardCharsets.UTF_8);
        try(var out=Files.newBufferedWriter(directory.resolve("assignment.csv"),StandardCharsets.UTF_8)) {
            out.write("service,server,cpu_cores,ram_gb,storage_gb,service_latency_or_weight,server_latency_ms\n");
            if(result.best()!=null)for(int i=0;i<evaluator.data.services().size();i++) {
                var s=evaluator.data.services().get(i);var host=evaluator.data.servers().get(result.best().server(i));
                out.write(row(s.name(),host.name(),s.cpu(),s.ram(),s.storage(),s.latencyValue(),host.latencyMs())+"\n");
            }
        }
        try(var out=Files.newBufferedWriter(directory.resolve("convergence.csv"),StandardCharsets.UTF_8)) {
            out.write("evaluations,best_score\n");for(var p:result.trace())out.write(row(p.evaluations(),p.score())+"\n");
        }
        List<String> metadata=new ArrayList<>();metadata.add("field,value");
        Object[][] fields={{"instance",evaluator.data.name()},{"version",Optimizer.VERSION},{"method",result.method().name()},
            {"objective",evaluator.objective.name()},{"alpha",evaluator.alpha},{"minimum_services",evaluator.minServices},
            {"seed",result.method()==SearchMethod.EXACT?"NA":result.config().seed()},{"budget",result.config().budget()},{"batch_size",result.config().batchSize()},
            {"patience_batches",result.config().patience()},{"greediness",result.config().greediness()},
            {"evaluations",result.evaluations()},{"unique_candidates",result.unique()},{"feasible_evaluations",result.feasible()},
            {"construction_attempts",result.constructions()},{"failed_constructions",result.failedConstructions()},
            {"restarts",result.restarts()},{"runtime_ms",result.milliseconds()},{"stop_reason",result.stopReason()},
            {"search_space",evaluator.data.searchSpace()},{"best_score",e==null?"NA":e.score()},
            {"average_delay_ms",e==null?"NA":e.delayMs()},{"illustrative_cost",e==null?"NA":e.cost()},
            {"optimistic_bound",evaluator.optimisticBound()},{"java",System.getProperty("java.version")},
            {"os",System.getProperty("os.name")},{"arch",System.getProperty("os.arch")},
            {"processors",Runtime.getRuntime().availableProcessors()},
            {"cost_note","Illustrative prices, cost units per observation period; replace with measured tariffs"},
            {"feasible",e!=null},{"quality_claim",RunRecords.proven(result)?"Proven by complete enumeration":"Best found; global optimality not certified"}};
        for(Object[] field:fields)metadata.add(row(field));
        Files.write(directory.resolve("run.csv"),metadata,StandardCharsets.UTF_8);
        if(e!=null)try(var out=Files.newBufferedWriter(directory.resolve("server_usage.csv"),StandardCharsets.UTF_8)) {
            out.write("server,count,cpu_used_cores,cpu_capacity_cores,ram_used_gb,ram_capacity_gb,storage_used_gb,storage_capacity_gb\n");
            for(int s=0;s<e.usage().size();s++) {
                var u=e.usage().get(s);var host=evaluator.data.servers().get(s);
                out.write(row(host.name(),u.count(),u.cpu(),host.cpu(),u.ram(),host.ram(),u.storage(),host.storage())+"\n");
            }
        }
    }
}
