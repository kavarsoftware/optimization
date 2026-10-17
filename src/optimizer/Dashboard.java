package optimizer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static optimizer.Visuals.*;

/** All mutable Swing state stays on the event dispatch thread. Search runs in SwingWorkers. */
public final class Dashboard extends JPanel {
    final JComboBox<SearchMethod> method=new JComboBox<>(SearchMethod.values());
    final JComboBox<Evaluator.Objective> objective=new JComboBox<>(Evaluator.Objective.values());
    final JSpinner seed=spinner(2026L,Long.MIN_VALUE,Long.MAX_VALUE,1L),budget=spinner(1_000_000,1,1_000_000,10_000),
        batch=spinner(10_000,1,1_000_000,1_000),patience=spinner(3,0,100,1),minimum=spinner(2,0,3,1),alpha=spinner(.75,0.,1.,.05),
        runs=spinner(10,2,50,1),experimentBudget=spinner(50_000,1000,1_000_000,10_000);
    final JButton run=button("Run optimizer",BLUE),stop=button("Stop",INK),export=button("Export run",INK),
        compare=button("Compare methods",BLUE),sweep=button("Run trade-off sweep",TEAL),load=button("Load CSV folder",INK),reset=button("Restore supplied data",INK),saveData=button("Save input CSVs",INK),exportTrials=button("Export experiments",INK);
    final JLabel delay=new JLabel("--"),checked=new JLabel("0"),unique=new JLabel("0.000%"),feasible=new JLabel("0"),elapsed=new JLabel("0 ms");
    final JLabel status=new JLabel("Ready. Select a method and run a reproducible search."),space=new JLabel();
    final JProgressBar progress=new JProgressBar(0,100);
    final Placement placement=new Placement();final Convergence chart=new Convergence();final TradePlot tradePlot=new TradePlot();
    final DefaultTableModel serverInput=model(true,"Server","CPU cores","RAM GB","Storage GB","Latency ms","Demo cost / CPU"),
        serviceInput=model(true,"Service","CPU cores","RAM GB","Storage GB","Service latency ms"),
        assignment=model(false,"Service","Assigned server","CPU cores","RAM GB","Storage GB","Service ms","Total delay ms"),
        trialModel=model(false,"Seed","Method","Score","Delay ms","Unique","Feasible","Time ms"),
        tradeModel=model(false,"Delay priority alpha","Delay ms","Demo cost","Sampled nondominated?");
    final JTable serverTable=table(serverInput),serviceTable=table(serviceInput);
    final JTextArea explanation=new JTextArea(),experimentSummary=new JTextArea();
    ProblemData data=ProblemData.demo();Evaluator lastEvaluator;SearchResult lastResult;List<ExperimentRunner.Trial> trials=List.of();
    List<Trade> trades=new ArrayList<>();Evaluator experimentEvaluator;final AtomicBoolean cancel=new AtomicBoolean();boolean busy;

    public Dashboard(){
        super(new BorderLayout(0,10));setBackground(BACK);setBorder(new EmptyBorder(12,20,12,20));
        JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);
        JPanel titles=new JPanel();titles.setLayout(new BoxLayout(titles,BoxLayout.Y_AXIS));titles.setOpaque(false);
        JLabel title=new JLabel("Service Placement Lab");title.setFont(new Font("SansSerif",Font.BOLD,28));title.setForeground(INK);
        String displayScale=System.getProperty("sun.java2d.uiScale","");
        JLabel subtitle=new JLabel("Optimization Methods  /  4 servers, 12 services  /  Java 17"+(displayScale.equals("2")?"  /  Display 200%":""));subtitle.setForeground(MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);titles.add(Box.createVerticalStrut(4));titles.add(subtitle);heading.add(titles,BorderLayout.WEST);
        JLabel course=new JLabel("DR. GODAR  /  WEEKS 0 + 1");course.setForeground(TEAL);course.setFont(new Font("SansSerif",Font.BOLD,12));heading.add(course,BorderLayout.EAST);
        add(heading,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.setFont(new Font("SansSerif",Font.BOLD,13));
        tabs.addTab("Optimizer",optimizerTab());tabs.addTab("Model and data",dataTab());tabs.addTab("Experiments",experimentsTab());tabs.addTab("Lecture guide",guideTab());
        add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(12,0));footer.setOpaque(false);status.setForeground(INK);footer.add(status,BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(210,20));progress.setStringPainted(true);progress.setString("Ready");footer.add(progress,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        loadData(data);stop.setEnabled(false);export.setEnabled(false);exportTrials.setEnabled(false);
        alpha.setToolTipText("Used only by the normalized delay / cost compromise objective");
        patience.setToolTipText("Complete batches without global improvement. Zero disables early stopping.");
        minimum.setToolTipText("Placement rule from the supplied code. This is not a reliability guarantee.");
        checked.setToolTipText("All evaluator calls, including repeated and infeasible candidates");
        run.addActionListener(e->start());stop.addActionListener(e->cancel.set(true));export.addActionListener(e->exportRun());
        compare.addActionListener(e->compare());sweep.addActionListener(e->sweep());
        reset.addActionListener(e->{data=ProblemData.demo();loadData(data);status.setText("Supplied inputs restored. Run again to apply them.");});
        load.addActionListener(e->chooseLoad());saveData.addActionListener(e->saveInputs());exportTrials.addActionListener(e->exportExperiments());
    }
    private JPanel optimizerTab(){
        JPanel tab=tabPanel();
        JPanel controls=panel(new GridLayout(2,1,0,6));
        controls.setBorder(null);
        JPanel first=flow();first.add(new JLabel("Method"));first.add(method);first.add(new JLabel("Objective"));first.add(objective);first.add(run);first.add(stop);first.add(export);
        JPanel second=flow();addField(second,"Seed",seed);addField(second,"Max evaluations",budget);addField(second,"Batch",batch);addField(second,"Patience",patience);addField(second,"Min / server",minimum);addField(second,"Delay priority",alpha);
        controls.add(first);controls.add(second);
        JPanel top=panel(new BorderLayout(0,10));top.setBorder(null);top.add(controls,BorderLayout.NORTH);
        JPanel metrics=panel(new GridLayout(1,5,12,0));metrics.add(metric("AVERAGE DELAY",delay));metrics.add(metric("EVALUATIONS",checked));metrics.add(metric("SEARCH SPACE VISITED",unique));metrics.add(metric("FEASIBLE EVALUATIONS",feasible));metrics.add(metric("ELAPSED",elapsed));top.add(metrics,BorderLayout.CENTER);tab.add(top,BorderLayout.NORTH);
        JPanel center=panel(new BorderLayout(0,10));center.setBorder(null);center.add(placement,BorderLayout.NORTH);
        explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setFont(new Font("SansSerif",Font.PLAIN,13));explanation.setBorder(new EmptyBorder(16,18,16,18));explanation.setForeground(INK);explanation.setText("Every candidate assigns all services exactly once.\n\nMandatory rules: CPU, RAM and storage capacities, and the selected minimum service count.\n\nRun a search to inspect the result and stopping evidence.");
        JPanel evidence=panel(new GridLayout(1,2,12,0));evidence.setBorder(null);evidence.add(chart);evidence.add(new JScrollPane(explanation));
        JTabbedPane results=new JTabbedPane();results.setFont(new Font("SansSerif",Font.BOLD,13));
        results.addTab("Search history and details",evidence);
        results.addTab("Service assignments",scrollTable(table(assignment)));
        center.add(results,BorderLayout.CENTER);tab.add(center,BorderLayout.CENTER);return tab;
    }
    private JPanel dataTab(){
        JPanel tab=tabPanel();
        JPanel top=panel(new GridLayout(3,1));JPanel buttons=flow();buttons.add(load);buttons.add(saveData);buttons.add(reset);top.add(buttons);
        top.add(new JLabel("Edit numeric cells, then run. CPU = cores, RAM/storage = GB, latency = ms. Changes apply on the next run."));
        top.add(new JLabel("Cost values are illustrative inputs only. The default objective uses your confirmed service latency interpretation."));tab.add(top,BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,section("Known server data",scrollTable(serverTable)),section("Known service data",scrollTable(serviceTable)));split.setResizeWeight(.38);split.setBorder(null);tab.add(split,BorderLayout.CENTER);
        space.setForeground(TEAL);tab.add(space,BorderLayout.SOUTH);return tab;
    }
    private JPanel experimentsTab(){
        JPanel tab=tabPanel();JPanel controls=flow();addField(controls,"Paired seeds",runs);addField(controls,"Evaluations / method",experimentBudget);controls.add(compare);controls.add(sweep);controls.add(exportTrials);tab.add(controls,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();JPanel comparison=panel(new BorderLayout(8,8));comparison.add(scrollTable(table(trialModel)),BorderLayout.CENTER);
        experimentSummary.setEditable(false);experimentSummary.setRows(7);experimentSummary.setFont(new Font("Monospaced",Font.PLAIN,12));experimentSummary.setText("Comparison uses identical inputs, paired seeds and the same full evaluation budget.\nEarly stopping is disabled. All three methods use the same evaluator.\nReport several runs, feasibility, solution quality and runtime. A single win proves little.");comparison.add(new JScrollPane(experimentSummary),BorderLayout.SOUTH);tabs.addTab("Repeated-seed comparison",comparison);
        JPanel trade=panel(new BorderLayout(8,8));trade.add(new JLabel("11 priority settings, 20,000 evaluations each. Lower cost and delay are preferred. This is an approximate sampled frontier."),BorderLayout.NORTH);
        trade.add(tradePlot,BorderLayout.CENTER);JScrollPane table=scrollTable(table(tradeModel));table.setPreferredSize(new Dimension(100,200));trade.add(table,BorderLayout.SOUTH);tabs.addTab("Cost and delay",trade);tab.add(tabs,BorderLayout.CENTER);return tab;
    }
    private JComponent guideTab(){
        JEditorPane guide=new JEditorPane("text/html",LectureGuide.html());guide.setEditable(false);guide.setCaretPosition(0);return new JScrollPane(guide);
    }
    void loadDataForPreview(ProblemData d){loadData(d);}
    private void loadData(ProblemData d){
        serverInput.setRowCount(0);serviceInput.setRowCount(0);
        for(var s:d.servers())serverInput.addRow(new Object[]{s.name(),s.cpu(),s.ram(),s.storage(),s.latencyMs(),s.costPerCpu()});
        for(var s:d.services())serviceInput.addRow(new Object[]{s.name(),s.cpu(),s.ram(),s.storage(),s.latencyValue()});
        space.setText(String.format(Locale.ROOT,"Search space: %d^%d = %,d complete assignments. Feasible candidates form a subset.",d.servers().size(),d.services().size(),d.searchSpace()));
    }
    private ProblemData readData(){
        if(serverTable.isEditing()&&!serverTable.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish editing the server cell");
        if(serviceTable.isEditing()&&!serviceTable.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish editing the service cell");
        List<ProblemData.Server> servers=new ArrayList<>();List<ProblemData.Service> services=new ArrayList<>();
        for(int r=0;r<serverInput.getRowCount();r++)servers.add(new ProblemData.Server(serverInput.getValueAt(r,0).toString(),value(serverInput,r,1),value(serverInput,r,2),value(serverInput,r,3),value(serverInput,r,4),value(serverInput,r,5)));
        for(int r=0;r<serviceInput.getRowCount();r++)services.add(new ProblemData.Service(serviceInput.getValueAt(r,0).toString(),value(serviceInput,r,1),value(serviceInput,r,2),value(serviceInput,r,3),value(serviceInput,r,4)));
        if(servers.size()!=4||services.size()!=12)throw new IllegalArgumentException("This dashboard expects exactly 4 servers and 12 services");
        return new ProblemData("godar-4x12-edited",servers,services);
    }
    private Evaluator evaluator(){data=readData();return new Evaluator(data,((Number)minimum.getValue()).intValue(),(Evaluator.Objective)objective.getSelectedItem(),((Number)alpha.getValue()).doubleValue());}
    private SearchConfig config(){return new SearchConfig(((Number)seed.getValue()).longValue(),((Number)budget.getValue()).intValue(),((Number)batch.getValue()).intValue(),((Number)patience.getValue()).intValue(),.35);}
    private void start(){
        try{
            Evaluator evaluator=evaluator();SearchConfig settings=config();SearchMethod selected=(SearchMethod)method.getSelectedItem();cancel.set(false);setBusy(true);progress.setValue(0);status.setText("Searching with seed "+settings.seed()+"...");
            placement.set(data,null,null);chart.set(List.of(),scoreLabel(evaluator));assignment.setRowCount(0);delay.setText("--");checked.setText("0");unique.setText("0.000%");feasible.setText("0");elapsed.setText("0 ms");
            new SwingWorker<SearchResult,Optimizer.Progress>(){
                protected SearchResult doInBackground(){return new Optimizer().run(evaluator,selected,settings,this::publish,cancel::get);}
                protected void process(List<Optimizer.Progress> updates){var p=updates.get(updates.size()-1);showProgress(evaluator,p,settings.budget());}
                protected void done(){try{lastResult=get();lastEvaluator=evaluator;displayResult(evaluator,lastResult);}catch(Exception ex){error(ex);}finally{setBusy(false);}}
            }.execute();
        }catch(Exception ex){error(ex);}
    }
    private void showProgress(Evaluator e,Optimizer.Progress p,int max){
        delay.setText(p.evaluation()==null?"--":format(p.evaluation().delayMs())+" ms");checked.setText(String.format(Locale.ROOT,"%,d",p.evaluations()));
        unique.setText(String.format(Locale.ROOT,"%.3f%%",100.*p.unique()/e.data.searchSpace()));feasible.setText(String.format(Locale.ROOT,"%,d",p.feasible()));elapsed.setText(p.milliseconds()+" ms");
        placement.set(e.data,p.best(),p.evaluation());chart.set(p.trace(),scoreLabel(e));progress.setValue((int)(100L*p.evaluations()/max));progress.setString(String.format(Locale.ROOT,"%,d / %,d",p.evaluations(),max));
    }
    public void displayResult(Evaluator e,SearchResult r){
        showProgress(e,new Optimizer.Progress(r.evaluations(),r.feasible(),r.unique(),r.restarts(),r.best(),r.evaluation(),r.milliseconds(),"Finished",r.trace()),r.config().budget());
        assignment.setRowCount(0);String text="Method: "+r.method()+"\nStop: "+r.stopReason()+"\n";
        if(r.best()!=null){
            for(int i=0;i<e.data.services().size();i++){var s=e.data.services().get(i);var host=e.data.servers().get(r.best().server(i));assignment.addRow(new Object[]{s.name(),host.name(),s.cpu(),s.ram(),s.storage(),s.latencyValue(),e.objective==Evaluator.Objective.WEIGHTED?"weight mode":s.latencyValue()+host.latencyMs()});}
            text+="\nAll mandatory constraints satisfied.\nScore: "+format(r.evaluation().score())+"\nDelay: "+format(r.evaluation().delayMs())+" ms. Demo cost: "+format(r.evaluation().cost())+"\nOptimistic bound: "+format(e.optimisticBound())+" (ignores capacity/count rules).\n";
        }else text+="\nNo feasible solution found. A search failure alone does not prove infeasibility.\n";
        text+="\n"+r.unique()+" distinct candidates, "+(r.evaluations()-r.unique())+" repeated evaluations.\nConstruction attempts: "+r.constructions()+", abandoned: "+r.failedConstructions()+".\nBest found under this budget. Global optimality is not certified.";
        explanation.setText(text);explanation.setCaretPosition(0);status.setText(r.stopReason()+". Seed "+r.config().seed()+". Best found, no global optimality certificate.");progress.setString("Stopped at "+r.evaluations()+" evaluations");
    }
    private void compare(){
        try{
            Evaluator e=evaluator();int count=((Number)runs.getValue()).intValue(),max=((Number)experimentBudget.getValue()).intValue();long first=((Number)seed.getValue()).longValue();
            cancel.set(false);setBusy(true);trialModel.setRowCount(0);trials=List.of();status.setText("Running equal-budget comparisons...");progress.setValue(0);
            new SwingWorker<List<ExperimentRunner.Trial>,ExperimentRunner.Trial>(){
                int finished;
                protected List<ExperimentRunner.Trial> doInBackground(){return ExperimentRunner.compare(e,first,count,max,this::publish,cancel::get);}
                protected void process(List<ExperimentRunner.Trial> items){for(var t:items){var r=t.result();var v=r.evaluation();trialModel.addRow(new Object[]{t.seed(),t.method(),v==null?"NA":format(v.score()),v==null?"NA":format(v.delayMs()),r.unique(),r.feasible(),r.milliseconds()});finished++;}progress.setValue(100*finished/(count*3));progress.setString(finished+" / "+(count*3)+" runs");}
                protected void done(){try{trials=get();experimentEvaluator=e;showSummary();status.setText("Experiment complete: "+trials.size()+" runs. Compare quality and runtime together.");}catch(Exception ex){error(ex);}finally{setBusy(false);}}
            }.execute();
        }catch(Exception ex){error(ex);}
    }
    private void showSummary(){
        StringBuilder b=new StringBuilder("METHOD                              FEASIBLE      MEAN SCORE       BEST        SAMPLE SD\n");
        for(var m:SearchMethod.values()){
            var list=trials.stream().filter(t->t.method()==m).toList();double[] values=list.stream().filter(t->t.result().evaluation()!=null).mapToDouble(t->t.result().evaluation().score()).toArray();double mean=Arrays.stream(values).average().orElse(Double.NaN),ss=0;for(double v:values)ss+=(v-mean)*(v-mean);
            b.append(String.format(Locale.ROOT,"%-35s %2d / %-3d %12.4f %12.4f %12.4f%n",m.name(),values.length,list.size(),mean,Arrays.stream(values).min().orElse(Double.NaN),values.length<2?Double.NaN:Math.sqrt(ss/(values.length-1))));
        }
        b.append("\nPatience disabled for equal budgets. Runtime depends on construction overhead, JVM warmup and hardware.\nThis experiment supports comparison on this instance; it does not prove a universally best method.");experimentSummary.setText(b.toString());
    }
    private void sweep(){
        try{
            ProblemData d=readData();int min=((Number)minimum.getValue()).intValue();long base=((Number)seed.getValue()).longValue();cancel.set(false);setBusy(true);tradeModel.setRowCount(0);trades=new ArrayList<>();status.setText("Sampling cost / delay trade-offs...");
            new SwingWorker<List<Trade>,Trade>(){
                protected List<Trade> doInBackground(){List<Trade> all=new ArrayList<>();for(int i=0;i<=10&&!cancel.get();i++){double a=i/10.;var e=new Evaluator(d,min,Evaluator.Objective.BALANCED,a);var r=new Optimizer().run(e,SearchMethod.HYBRID,new SearchConfig(base,20_000,10_000,0,.35),null,cancel::get);if(r.evaluation()!=null){var t=new Trade(a,r.evaluation().delayMs(),r.evaluation().cost());all.add(t);publish(t);}}return all;}
                protected void process(List<Trade> items){trades.addAll(items);tradePlot.set(trades);progress.setValue(100*trades.size()/11);progress.setString(trades.size()+" / 11 priorities");}
                protected void done(){try{trades=new ArrayList<>(get());for(var t:trades)tradeModel.addRow(new Object[]{t.alpha(),format(t.delay()),format(t.cost()),!TradePlot.dominated(t,trades)});tradePlot.set(trades);status.setText("Sampled frontier complete. Prices are illustrative; Pareto optimality is not certified.");}catch(Exception ex){error(ex);}finally{setBusy(false);}}
            }.execute();
        }catch(Exception ex){error(ex);}
    }
    private void setBusy(boolean value){busy=value;for(JComponent c:new JComponent[]{run,method,objective,seed,budget,batch,patience,minimum,alpha,compare,sweep,runs,experimentBudget,load,reset,saveData,serverTable,serviceTable})c.setEnabled(!value);stop.setEnabled(value);export.setEnabled(!value&&lastResult!=null);exportTrials.setEnabled(!value&&!trials.isEmpty());}
    private Path directory(String title){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(title);chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);return chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile().toPath():null;}
    private void exportRun(){Path p=directory("Select a folder; export creates a new run subfolder");if(p==null)return;try{Path target=p.resolve("run-"+System.currentTimeMillis());CsvFiles.export(target,lastEvaluator,lastResult);status.setText("Saved run, input snapshot and convergence to "+target);}catch(Exception ex){error(ex);}}
    private void exportExperiments(){Path p=directory("Select a folder for a new experiment subfolder");if(p==null)return;try{Path target=p.resolve("experiment-"+System.currentTimeMillis());ExperimentRunner.write(target,experimentEvaluator,trials);status.setText("Experiment exported to "+target);}catch(Exception ex){error(ex);}}
    private void chooseLoad(){Path p=directory("Load a folder containing servers.csv and services.csv");if(p==null)return;try{var next=CsvFiles.read(p);if(next.servers().size()!=4||next.services().size()!=12)throw new IllegalArgumentException("Dashboard requires 4 servers and 12 services");data=next;loadData(data);status.setText("Loaded inputs. Run to evaluate the new data.");}catch(Exception ex){error(ex);}}
    private void saveInputs(){Path p=directory("Select a folder; save creates a new inputs subfolder");if(p==null)return;try{CsvFiles.writeData(p.resolve("inputs-"+System.currentTimeMillis()),readData());status.setText("Input CSVs saved.");}catch(Exception ex){error(ex);}}
    private void error(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();status.setText("Error: "+cause.getMessage());if(!GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(this,cause.getMessage(),"Please check inputs",JOptionPane.ERROR_MESSAGE);}
    static String scoreLabel(Evaluator e){return switch(e.objective){case LATENCY,WEIGHTED->"Delay (ms)";case COST->"Illustrative cost";case BALANCED->"Normalized compromise score";};}
    static double value(DefaultTableModel model,int r,int c){return Double.parseDouble(String.valueOf(model.getValueAt(r,c)).strip());}
    static String format(double value){return String.format(Locale.ROOT,"%.3f",value);}
    static JSpinner spinner(Number value,Comparable<?> min,Comparable<?> max,Number step){JSpinner s=new JSpinner(new SpinnerNumberModel(value,min,max,step));s.setPreferredSize(new Dimension(value instanceof Long?100:85,29));return s;}
    static JPanel panel(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);p.setBorder(new EmptyBorder(8,0,0,0));return p;}
    static JPanel tabPanel(){JPanel p=panel(new BorderLayout(10,12));p.setOpaque(true);p.setBackground(BACK);return p;}
    static JScrollPane scrollTable(JTable t){JScrollPane scroll=new JScrollPane(t);scroll.setColumnHeaderView(t.getTableHeader());return scroll;}
    static JPanel flow(){JPanel p=panel(new FlowLayout(FlowLayout.LEFT,9,0));p.setBorder(null);return p;}
    static void addField(JPanel p,String label,JComponent component){JLabel text=new JLabel(label);text.setLabelFor(component);p.add(text);p.add(component);}
    static JButton button(String label,Color color){JButton b=new JButton(label);b.setBackground(color);b.setForeground(Color.WHITE);b.setFocusPainted(false);b.setFont(new Font("SansSerif",Font.BOLD,12));b.setBorder(new EmptyBorder(9,13,9,13));return b;}
    static JPanel metric(String title,JLabel value){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(Color.WHITE);p.setBorder(new EmptyBorder(10,17,10,17));JLabel label=new JLabel(title);label.setFont(new Font("SansSerif",Font.BOLD,10));label.setForeground(MUTED);label.setAlignmentX(Component.LEFT_ALIGNMENT);value.setAlignmentX(Component.LEFT_ALIGNMENT);value.setForeground(INK);value.setFont(new Font("SansSerif",Font.BOLD,23));p.add(label);p.add(Box.createVerticalStrut(5));p.add(value);return p;}
    static JComponent section(String name,JComponent content){JPanel p=panel(new BorderLayout(8,8));p.add(new JLabel(name),BorderLayout.NORTH);p.add(content,BorderLayout.CENTER);return p;}
    static DefaultTableModel model(boolean editable,String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return editable;}};}
    static JTable table(DefaultTableModel model){JTable t=new JTable(model);t.setRowHeight(28);t.setFont(new Font("SansSerif",Font.PLAIN,12));t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,11));t.setGridColor(LINE);t.setSelectionBackground(new Color(218,232,255));t.setFillsViewportHeight(true);t.setAutoCreateRowSorter(true);return t;}
}
