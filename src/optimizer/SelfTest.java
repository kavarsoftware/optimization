package optimizer;

import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small manual cases and behavioral tests, runnable without JUnit or external dependencies. */
public final class SelfTest {
    private static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void close(double a,double b,String message){check(Math.abs(a-b)<1e-8,message+": "+a+" != "+b);}
    static ProblemData toy(){return new ProblemData("manual-2x3",List.of(
        new ProblemData.Server("S1",10,10,100,5,.2),new ProblemData.Server("S2",10,10,100,20,.1)),List.of(
        new ProblemData.Service("A",2,1,10,1),new ProblemData.Service("B",4,2,10,2),new ProblemData.Service("C",1,1,5,3)));}
    static void throwsInvalid(Runnable task){boolean caught=false;try{task.run();}catch(IllegalArgumentException ex){caught=true;}check(caught,"Invalid input rejected");}
    public static void run()throws Exception {
        checks=0;var d=toy();var e=new Evaluator(d,1,Evaluator.Objective.LATENCY,.75);
        var a=e.evaluate(new Candidate(new int[]{0,1,0}));var b=e.evaluate(new Candidate(new int[]{1,0,1}));
        check(a.feasible()&&b.feasible(),"Manual candidates feasible");close(a.delayMs(),12,"Manual A delay");close(a.cost(),1,"Manual A cost");close(b.delayMs(),17,"Manual B delay");close(b.cost(),1.1,"Manual B cost");
        check(!e.evaluate(new Candidate(new int[]{0,0,0})).feasible(),"Minimum service rule");
        check(!e.evaluate(new Candidate(new int[]{0,-1,1})).feasible(),"Negative index");check(!e.evaluate(new Candidate(new int[]{0,2,1})).feasible(),"High index");
        check(!e.evaluate(new Candidate(new int[]{0})).feasible(),"Short candidate");check(!e.evaluate(null).feasible(),"Null candidate");
        int[] raw={0,1,0};Candidate immutable=new Candidate(raw);raw[0]=1;immutable.copy()[1]=0;check(immutable.server(0)==0&&immutable.server(1)==1,"Defensive copies");
        var weighted=new Evaluator(d,1,Evaluator.Objective.WEIGHTED,.75).evaluate(new Candidate(new int[]{0,1,0}));close(weighted.delayMs(),10,"Weighted legacy evaluator");
        check(ProblemData.demo().searchSpace()==16_777_216,"Search-space count");throwsInvalid(()->new SearchConfig(1,0,10,3,.3));throwsInvalid(()->new SearchConfig(1,1_000_001,10,3,.3));
        throwsInvalid(()->new Evaluator(d,2,Evaluator.Objective.LATENCY,.5));throwsInvalid(()->new Evaluator(d,1,Evaluator.Objective.LATENCY,Double.NaN));
        throwsInvalid(()->new ProblemData("bad",List.of(new ProblemData.Server("s",0,2,3,4,1)),d.services()));
        var equality=new ProblemData("equality",List.of(new ProblemData.Server("s",7,4,25,1,1)),d.services());check(new Evaluator(equality,0,Evaluator.Objective.LATENCY,.5).evaluate(new Candidate(new int[]{0,0,0})).feasible(),"Capacity equality accepted");
        var over=new ProblemData("overload",List.of(new ProblemData.Server("s",6,3,24,1,1)),d.services());var bad=new Evaluator(over,0,Evaluator.Objective.LATENCY,.5).evaluate(new Candidate(new int[]{0,0,0}));check(!bad.feasible()&&bad.violations().size()==3,"Three resource constraints independently checked");
        var opt=new Optimizer();
        for(var method:SearchMethod.values()){
            var c=new SearchConfig(2026,2000,100,0,.35);var one=opt.run(e,method,c,null,()->false);var two=opt.run(e,method,c,null,()->false);
            check(one.evaluations()==2000,"Full evaluation budget "+method);check(one.best()!=null,"Feasible result "+method);
            check(Arrays.equals(one.best().copy(),two.best().copy())&&one.unique()==two.unique(),"Seed reproducibility "+method);
            close(one.evaluation().score(),two.evaluation().score(),"Same score");check(one.unique()<=d.searchSpace()&&one.unique()<=one.evaluations(),"Distinct candidate count");
            check(one.trace().get(one.trace().size()-1).evaluations()==2000,"Final trace checkpoint");double prev=Double.POSITIVE_INFINITY;
            for(var point:one.trace()){check(point.score()<=prev+1e-10,"Monotone incumbent trace");prev=point.score();}
            var oneEval=opt.run(e,method,new SearchConfig(1,1,100,3,.35),null,()->false);check(oneEval.evaluations()==1,"Budget of one respected");
        }
        var stop=opt.run(e,SearchMethod.HYBRID,new SearchConfig(2026,10_000,100,2,.35),null,()->false);
        check(stop.evaluations()<10_000&&stop.evaluations()%100==0&&stop.stopReason().contains("batches"),"Complete-batch early stopping");
        var cancel=opt.run(e,SearchMethod.RANDOM,SearchConfig.defaults(),null,()->true);check(cancel.evaluations()==0&&cancel.stopReason().contains("Cancelled"),"Cancellation");
        var impossible=new ProblemData("impossible",List.of(new ProblemData.Server("tiny",1,1,1,1,1)),d.services());
        check(opt.run(new Evaluator(impossible,0,Evaluator.Objective.LATENCY,.5),SearchMethod.HYBRID,SearchConfig.defaults(),null,()->false).stopReason().startsWith("Infeasible:"),"Necessary infeasibility detection");
        // Enumerate only the 8-candidate teaching instance to validate a known target, never the 4^12 project.
        double known=Double.POSITIVE_INFINITY;for(int code=0;code<8;code++)known=Math.min(known,e.evaluate(new Candidate(new int[]{code&1,(code>>1)&1,(code>>2)&1})).score());
        close(opt.run(e,SearchMethod.HYBRID,new SearchConfig(7,1000,100,0,.35),null,()->false).evaluation().score(),known,"Known small-instance optimum");
        for(var objective:Evaluator.Objective.values()){
            var evaluator=new Evaluator(ProblemData.demo(),2,objective,.75);var r=opt.run(evaluator,SearchMethod.HYBRID,new SearchConfig(2026,5000,1000,0,.35),null,()->false);
            check(r.best()!=null&&evaluator.evaluate(r.best()).feasible(),"Original data feasible "+objective);check(evaluator.optimisticBound()<=r.evaluation().score()+1e-9,"Optimistic bound "+objective);
        }
        for(var method:SearchMethod.values()) {
            var evaluator=new Evaluator(ProblemData.demo(),2,Evaluator.Objective.LATENCY,.75);
            var r=opt.run(evaluator,method,new SearchConfig(2026,10000,1000,0,.35),null,()->false);
            check(r.evaluations()==10000,"Original instance uses full comparison budget "+method);
        }
        Path temp=Files.createTempDirectory("placement-test-");try{
            CsvFiles.writeData(temp,d);var loaded=CsvFiles.read(temp);check(loaded.services().equals(d.services())&&loaded.servers().equals(d.servers()),"CSV roundtrip");
        }finally{Files.deleteIfExists(temp.resolve("servers.csv"));Files.deleteIfExists(temp.resolve("services.csv"));Files.deleteIfExists(temp);}
        AtomicReference<Dashboard> view=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{Main.style();Dashboard dashboard=new Dashboard();dashboard.budget.setValue(2000);dashboard.batch.setValue(1000);dashboard.patience.setValue(0);view.set(dashboard);dashboard.run.doClick();check(dashboard.busy&&!dashboard.run.isEnabled()&&dashboard.stop.isEnabled(),"GUI enters running state");});
        waitForGui(view.get());
        SwingUtilities.invokeAndWait(()->{var dashboard=view.get();check(dashboard.lastResult!=null&&dashboard.lastResult.evaluations()==2000,"GUI background run completes");check(dashboard.assignment.getRowCount()==12&&dashboard.export.isEnabled(),"GUI displays assignment and enables export");dashboard.budget.setValue(1000000);dashboard.run.doClick();dashboard.stop.doClick();});
        waitForGui(view.get());
        SwingUtilities.invokeAndWait(()->check(view.get().lastResult.stopReason().contains("Cancelled"),"GUI stop button cancels search"));
        System.out.println("PASS: "+checks+" checks (manual evaluator, constraints, budgets, seeds, stopping, bound, CSV, GUI worker/stop)");
    }
    private static void waitForGui(Dashboard view)throws Exception {
        long end=System.nanoTime()+10_000_000_000L;AtomicBoolean running=new AtomicBoolean(true);
        while(running.get()&&System.nanoTime()<end){SwingUtilities.invokeAndWait(()->running.set(view.busy));if(running.get())Thread.sleep(10);}
        check(!running.get(),"GUI worker terminates promptly");
    }
}
