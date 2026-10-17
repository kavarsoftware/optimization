package optimizer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Launch the dashboard, or run headless experiments with the same model and search code. */
public final class Main {
    public static void main(String[] args)throws Exception{
        Map<String,String> flags=new HashMap<>();
        for(int i=0;i<args.length;i++){if(!args[i].startsWith("--"))throw new IllegalArgumentException("Expected --option: "+args[i]);String key=args[i].substring(2);flags.put(key,i+1<args.length&&!args[i+1].startsWith("--")?args[++i]:"true");}
        if(flags.containsKey("help")){System.out.println("""
            java -jar ServerPlacementLab.jar                 Open dashboard
            java -jar ServerPlacementLab.jar --self-test     Verify model and search
            java -jar ServerPlacementLab.jar --cli --out results/my-run
            java -jar ServerPlacementLab.jar --compare 20 --budget 50000 --out results/comparison
            java -jar ServerPlacementLab.jar --preview preview.png
            Options: --seed 2026 --budget 1000000 --batch 10000 --patience 3 --min 2
                     --method HYBRID|CONSTRUCTIVE|RANDOM --objective LATENCY|WEIGHTED|COST|BALANCED
                     --alpha 0.75 --input data --out results/my-run
            Display: --ui-scale 1|2 (100% or 200%). High-DPI Linux displays scale automatically.
            Service latency is in ms by default. WEIGHTED reproduces the old weight interpretation.
            Comparison runs disable patience. Cost values are illustrative only.
            """);return;}
        if(flags.containsKey("self-test")){SelfTest.run();return;}
        if(flags.containsKey("cli")||flags.containsKey("compare")||flags.containsKey("preview")){
            var data=flags.containsKey("input")?CsvFiles.read(Path.of(flags.get("input"))):ProblemData.demo();
            var e=new Evaluator(data,Integer.parseInt(flags.getOrDefault("min","2")),Evaluator.Objective.valueOf(flags.getOrDefault("objective","LATENCY")),Double.parseDouble(flags.getOrDefault("alpha",".75")));
            var config=new SearchConfig(Long.parseLong(flags.getOrDefault("seed","2026")),Integer.parseInt(flags.getOrDefault("budget","1000000")),Integer.parseInt(flags.getOrDefault("batch","10000")),Integer.parseInt(flags.getOrDefault("patience","3")),.35);
            Path output=Path.of(flags.getOrDefault("out","results/latest"));
            if(flags.containsKey("compare")){
                var trials=ExperimentRunner.compare(e,config.seed(),Integer.parseInt(flags.get("compare")),config.budget(),t->{var v=t.result().evaluation();System.out.println(t.seed()+" "+t.method().name()+" score="+(v==null?"NA":v.score())+" evaluations="+t.result().evaluations());},()->false);
                ExperimentRunner.write(output,e,trials);System.out.println("Saved comparison: "+output.toAbsolutePath());return;
            }
            var result=new Optimizer().run(e,SearchMethod.valueOf(flags.getOrDefault("method","HYBRID")),config,null,()->false);
            System.out.println("Stop: "+result.stopReason()+"; evaluations="+result.evaluations()+"; unique="+result.unique()+"; feasible="+result.feasible());
            if(result.evaluation()!=null){System.out.printf(Locale.ROOT,"Best found: %.6f ms; score=%.6f; cost=%.4f; runtime=%d ms%n",result.evaluation().delayMs(),result.evaluation().score(),result.evaluation().cost(),result.milliseconds());for(int i=0;i<data.services().size();i++)System.out.println(data.services().get(i).name()+" -> "+data.servers().get(result.best().server(i)).name());}
            if(flags.containsKey("preview")){
                SwingUtilities.invokeAndWait(()->{try{style();Dashboard dashboard=new Dashboard();dashboard.data=data;dashboard.loadDataForPreview(data);dashboard.displayResult(e,result);dashboard.setSize(1440,1050);layout(dashboard);BufferedImage image=new BufferedImage(1440,1050,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();Visuals.quality(g);dashboard.printAll(g);g.dispose();ImageIO.write(image,"png",Path.of(flags.get("preview")).toFile());}catch(Exception ex){throw new RuntimeException(ex);}});
            }else CsvFiles.export(output,e,result);
            return;
        }
        DisplaySettings.configure(flags.get("ui-scale"));
        if(GraphicsEnvironment.isHeadless()){System.err.println("No graphical display. Use --cli, --compare or --self-test. Run normally on your desktop for the dashboard.");System.exit(2);}
        SwingUtilities.invokeLater(()->{
            style();JFrame frame=new JFrame("Service Placement Lab - Optimization Methods");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);Dashboard dashboard=new Dashboard();frame.setContentPane(dashboard);
            Rectangle available=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setMinimumSize(new Dimension(Math.min(1180,available.width),Math.min(740,available.height)));
            frame.setSize(Math.min(1440,available.width),Math.min(940,available.height));
            frame.setLocationRelativeTo(null);frame.setExtendedState(JFrame.MAXIMIZED_BOTH);frame.setVisible(true);
            if(flags.containsKey("demo"))dashboard.run.doClick();
        });
    }
    static void style(){try{UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());}catch(Exception ignored){}UIManager.put("Label.font",new Font("SansSerif",Font.PLAIN,12));UIManager.put("Panel.background",Visuals.BACK);UIManager.put("TabbedPane.selected",Color.WHITE);}
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container container)layout(container);}
}
