package optimizer.ui;

import optimizer.brain.*;
import optimizer.experiments.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Locale;
import javax.swing.*;

/** Real evaluated assignments, drawn as resource cards with animated service links. */
public final class AssignmentCanvas extends JPanel implements Scrollable {
    private ProblemData data = ProblemData.assessmentDefaults();
    private SearchEvent event;
    private double fraction = 1;
    private static final Color RED = new Color(190,55,75);
    public AssignmentCanvas() {
        setBackground(Visuals.BACK);
        setPreferredSize(new Dimension(1100,450));
    }
    public void setData(ProblemData data) { this.data=data; event=null; repaint(); }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle area,int orientation,int direction) { return 24; }
    public int getScrollableBlockIncrement(Rectangle area,int orientation,int direction) { return Math.max(24,area.height-24); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return getParent()!=null && getParent().getHeight()>=400; }
    public void setEvent(SearchEvent event,double fraction) {
        this.event=event; this.fraction=Math.max(0,Math.min(1,fraction)); repaint();
    }
    private int column() { return (getWidth()+12)/4; }
    private int chipStep() { return Math.min(30,Math.max(20,(getHeight()-280)/5)); }
    private Point2D position(Candidate candidate,int service) {
        if(candidate==null) return new Point2D.Double(getWidth()/2.,28);
        int host=candidate.server(service),rank=0;
        for(int i=0;i<service;i++) if(candidate.server(i)==host)rank++;
        return new Point2D.Double(host*column()+(column()-12)/2.,267+rank*chipStep());
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create(); Visuals.quality(g);
        boolean feasible=event!=null && event.evaluation()!=null && event.evaluation().feasible();
        String state=event==null?"READY TO EXPLORE":event.evaluation()==null?"NO PLACEMENT":feasible?"FEASIBLE":"REJECTED";
        Color stateColor=event==null?Visuals.MUTED:feasible?Visuals.TEAL:RED;
        g.setColor(new Color(stateColor.getRed(),stateColor.getGreen(),stateColor.getBlue(),18));
        g.fillRoundRect(0,6,142,28,28,28);
        Visuals.text(g,state,12,24,10,stateColor,true);
        String detail=event==null?"Start a search or inspect the reference placement."
                :event.evaluation()==null?"No complete feasible candidate found."
                :feasible?String.format(Locale.ROOT,"Objective J  %.6f   ·   All hard constraints satisfied",event.evaluation().score())
                :String.join("; ",event.evaluation().violations());
        Visuals.text(g,detail,156,24,11,Visuals.MUTED,false);
        for(int host=0;host<4;host++) {
            var server=data.servers().get(host);
            int x=host*column(),w=column()-12;
            Color accent=Visuals.HOSTS[host];
            g.setColor(Color.WHITE); g.fillRoundRect(x,52,w,Math.max(330,getHeight()-64),16,16);
            g.setColor(Visuals.LINE); g.drawRoundRect(x,52,w-1,Math.max(330,getHeight()-64),16,16);
            g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),18));
            g.fillRoundRect(x+16,69,34,34,10,10);
            Visuals.text(g,server.name(),x+27,91,15,accent,true);
            Visuals.text(g,"Server "+server.name(),x+61,84,14,Visuals.INK,true);
            Visuals.text(g,String.format(Locale.ROOT,"%.0f ms RTT  ·  %.0f Mb/s",server.latencyMs(),server.bandwidth()),x+61,102,10,Visuals.MUTED,false);
            if(event!=null && event.evaluation()!=null) {
                var u=event.evaluation().usage().get(host);
                meter(g,x+16,128,w-32,"CPU",u.cpu(),server.cpu(),accent);
                meter(g,x+16,152,w-32,"Memory · GB",u.ram(),server.ram(),accent);
                meter(g,x+16,176,w-32,"Storage · GB",u.storage(),server.storage(),accent);
                meter(g,x+16,200,w-32,"GPU slots",u.gpu(),server.gpu(),accent);
                meter(g,x+16,224,w-32,"Service slots",u.count(),server.maxServices(),accent);
            } else {
                Visuals.text(g,"Awaiting an evaluated placement",x+16,144,11,Visuals.MUTED,false);
            }
            g.setColor(Visuals.LINE);g.drawLine(x+16,246,x+w-16,246);
        }
        if(event!=null && event.candidate()!=null) {
            double t=fraction*fraction*(3-2*fraction);
            for(int i=0;i<12;i++) {
                int host=event.candidate().server(i);
                var service=data.services().get(i); var server=data.servers().get(host);
                double latency=server.latencyMs()+8000*service.payload()/server.bandwidth();
                boolean pass=latency<=service.maxLatency()+1e-9;
                Color accent=pass?Visuals.HOSTS[host]:RED;
                Point2D from=position(event.before(),i),to=position(event.candidate(),i);
                int x=(int)(from.getX()+t*(to.getX()-from.getX()));
                int y=(int)(from.getY()+t*(to.getY()-from.getY()));
                int w=column()-44,h=Math.min(26,chipStep()-3);
                g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),45));
                g.drawLine(host*column()+16,246,x-w/2,y);
                g.setColor(pass?new Color(247,248,251):new Color(255,241,242));
                g.fillRoundRect(x-w/2,y-h/2,w,h,8,8);
                g.setColor(accent);g.fillRoundRect(x-w/2,y-h/2,3,h,3,3);
                Visuals.text(g,service.name(),x-w/2+12,y+4,11,Visuals.INK,true);
                String delay=String.format(Locale.ROOT,"%.1f ms",latency);
                g.setFont(Ui.font(Font.PLAIN,11));
                Visuals.text(g,delay,x+w/2-g.getFontMetrics().stringWidth(delay)-10,y+4,11,pass?Visuals.MUTED:RED,false);
            }
        }
        g.dispose();
    }
    private void meter(Graphics2D g,int x,int y,int width,String label,double used,double cap,Color accent) {
        Color color=used>cap+1e-9?RED:accent;
        Visuals.text(g,label,x,y,10,Visuals.MUTED,false);
        String amount=String.format(Locale.ROOT,"%.0f / %.0f",used,cap);
        g.setFont(Ui.font(Font.BOLD,10));
        Visuals.text(g,amount,x+width-g.getFontMetrics().stringWidth(amount),y,10,Visuals.INK,true);
        g.setColor(new Color(239,241,246));g.fillRoundRect(x,y+5,width,4,4,4);
        g.setColor(color);g.fillRoundRect(x,y+5,(int)(width*Math.min(1,used/Math.max(1,cap))),4,4,4);
    }
}
