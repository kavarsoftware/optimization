package optimizer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;

/** Native Swing visuals. No chart library or network connection is required. */
public final class Visuals {
    public static final Color INK=new Color(25,39,62), MUTED=new Color(103,117,137),
        BLUE=new Color(56,102,232), TEAL=new Color(0,153,134), BACK=new Color(242,245,249),
        LINE=new Color(224,231,239);
    public static final Color[] HOSTS={BLUE,TEAL,new Color(142,95,205),new Color(224,149,34)};
    static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);}
    static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){g.setColor(color);g.setFont(new Font("SansSerif",bold?Font.BOLD:Font.PLAIN,size));g.drawString(value,x,y);}
    public static class Placement extends JPanel {
        ProblemData data=ProblemData.demo(); Candidate candidate; Evaluation evaluation;
        public Placement(){setBackground(BACK);setPreferredSize(new Dimension(1100,235));}
        public void set(ProblemData data,Candidate candidate,Evaluation evaluation){
            this.data=data;this.candidate=candidate;this.evaluation=evaluation;
            int maxCount=evaluation==null?0:evaluation.usage().stream().mapToInt(Evaluation.Usage::count).max().orElse(0);
            int height=maxCount>6?290:235;
            if(getPreferredSize().height!=height){setPreferredSize(new Dimension(1100,height));revalidate();}
            repaint();
        }
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();quality(g);
            int m=data.servers().size(), gap=14, width=(getWidth()-gap*(m-1))/m;
            for(int s=0;s<m;s++){
                int x=s*(width+gap);var host=data.servers().get(s);Color color=HOSTS[s%HOSTS.length];
                g.setColor(Color.WHITE);g.fillRoundRect(x,0,width,getHeight()-4,18,18);
                g.setColor(LINE);g.drawRoundRect(x,0,width-1,getHeight()-5,18,18);
                g.setColor(color);g.fillRoundRect(x+16,18,7,24,5,5);
                text(g,host.name(),x+32,37,20,INK,true);
                String latency=String.format(Locale.ROOT,"%.0f ms",host.latencyMs());
                text(g,latency,x+width-82,36,15,color,true);
                var u=evaluation==null?new Evaluation.Usage(0,0,0,0):evaluation.usage().get(s);
                text(g,u.count()+" SERVICES",x+18,63,11,MUTED,true);
                bar(g,x+18,82,width-36,"CPU",u.cpu(),host.cpu(),color);
                bar(g,x+18,116,width-36,"RAM",u.ram(),host.ram(),color);
                bar(g,x+18,150,width-36,"Storage",u.storage(),host.storage(),color);
                int chipX=x+18,chipY=193;
                if(candidate==null)text(g,"Waiting for a feasible placement",x+18,207,12,MUTED,false);
                else for(int i=0;i<candidate.size();i++)if(candidate.server(i)==s){
                    String label=data.services().get(i).name();g.setFont(new Font("SansSerif",Font.BOLD,11));int chipW=g.getFontMetrics().stringWidth(label)+18;
                    if(chipX+chipW>x+width-12){chipX=x+18;chipY+=27;}
                    g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),18));g.fillRoundRect(chipX,chipY-15,chipW,23,9,9);
                    text(g,label,chipX+9,chipY+1,11,color,true);chipX+=chipW+5;
                }
            }
            g.dispose();
        }
        private void bar(Graphics2D g,int x,int y,int width,String name,double used,double capacity,Color color){
            text(g,name,x,y,11,MUTED,true);
            String value=String.format(Locale.ROOT,"%.0f / %.0f   %.0f%%",used,capacity,100*used/capacity);
            g.setFont(new Font("SansSerif",Font.PLAIN,11));text(g,value,x+width-g.getFontMetrics().stringWidth(value),y,11,INK,false);
            g.setColor(BACK);g.fillRoundRect(x,y+8,width,7,7,7);
            g.setColor(used>capacity?Color.RED:color);g.fillRoundRect(x,y+8,(int)(width*Math.min(1,used/capacity)),7,7,7);
        }
    }
    public static class Convergence extends JPanel {
        List<SearchResult.Point> points=List.of();String scoreLabel="Average delay (ms)";
        public Convergence(){setBackground(Color.WHITE);setPreferredSize(new Dimension(760,210));}
        public void set(List<SearchResult.Point> points,String label){this.points=List.copyOf(points);scoreLabel=label;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();quality(g);
            int left=72,right=getWidth()-24,top=40,bottom=getHeight()-36;
            text(g,"BEST FOUND SO FAR",18,23,11,MUTED,true);
            if(points.isEmpty()){text(g,"Run the optimizer to see its search history",left,110,14,MUTED,false);g.dispose();return;}
            double low=points.stream().mapToDouble(SearchResult.Point::score).min().orElse(0),high=points.stream().mapToDouble(SearchResult.Point::score).max().orElse(1);
            double pad=Math.max(.01,(high-low)*.18);low-=pad;high+=pad;
            int maxX=Math.max(1,points.get(points.size()-1).evaluations());
            for(int k=0;k<=3;k++){
                int y=top+(bottom-top)*k/3;g.setColor(LINE);g.drawLine(left,y,right,y);
                text(g,String.format(Locale.ROOT,"%.2f",high-(high-low)*k/3),8,y+4,11,MUTED,false);
            }
            Path2D path=new Path2D.Double();boolean first=true;double previousY=0;
            for(var p:points){double x=left+(right-left)*p.evaluations()/(double)maxX,y=bottom-(bottom-top)*(p.score()-low)/(high-low);
                if(first){path.moveTo(x,y);first=false;}else{path.lineTo(x,previousY);path.lineTo(x,y);}previousY=y;}
            g.setColor(BLUE);g.setStroke(new BasicStroke(2.5f));g.draw(path);
            text(g,"0",left,bottom+20,11,MUTED,false);
            String label=String.format(Locale.ROOT,"%,d evaluations",maxX);text(g,label,right-140,bottom+20,11,MUTED,false);
            text(g,scoreLabel,left+12,top-9,11,MUTED,false);g.dispose();
        }
    }
    public record Trade(double alpha,double delay,double cost) {}
    public static class TradePlot extends JPanel {
        List<Trade> points=List.of();
        public TradePlot(){setBackground(Color.WHITE);setPreferredSize(new Dimension(600,330));}
        public void set(List<Trade> points){this.points=List.copyOf(points);repaint();}
        static boolean dominated(Trade p,List<Trade> all){return all.stream().anyMatch(q->q.cost()<=p.cost()+1e-9&&q.delay()<=p.delay()+1e-9&&(q.cost()<p.cost()-1e-9||q.delay()<p.delay()-1e-9));}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();quality(g);
            text(g,"SAMPLED COST / DELAY TRADE-OFFS",20,28,13,INK,true);
            if(points.isEmpty()){text(g,"Run the sweep to compare stakeholder priorities",45,150,14,MUTED,false);g.dispose();return;}
            double xMin=points.stream().mapToDouble(Trade::cost).min().orElse(0)*.94,xMax=points.stream().mapToDouble(Trade::cost).max().orElse(1)*1.06;
            double yMin=points.stream().mapToDouble(Trade::delay).min().orElse(0)*.94,yMax=points.stream().mapToDouble(Trade::delay).max().orElse(1)*1.06;
            if(xMax-xMin<1e-6)xMax=xMin+1;if(yMax-yMin<1e-6)yMax=yMin+1;
            int left=64,right=getWidth()-30,top=54,bottom=getHeight()-58;
            for(int i=0;i<=4;i++){
                int x=left+(right-left)*i/4,y=top+(bottom-top)*i/4;g.setColor(LINE);g.drawLine(left,y,right,y);g.drawLine(x,top,x,bottom);
                text(g,String.format(Locale.ROOT,"%.2f",xMin+(xMax-xMin)*i/4),x-12,bottom+21,10,MUTED,false);
                text(g,String.format(Locale.ROOT,"%.1f",yMax-(yMax-yMin)*i/4),12,y+4,10,MUTED,false);
            }
            for(var p:points){int x=left+(int)((right-left)*(p.cost()-xMin)/(xMax-xMin)),y=bottom-(int)((bottom-top)*(p.delay()-yMin)/(yMax-yMin));
                g.setColor(dominated(p,points)?new Color(181,190,203):TEAL);g.fillOval(x-6,y-6,12,12);}
            text(g,"Illustrative cost (lower is better)",left+50,bottom+44,12,MUTED,false);
            text(g,"Delay in ms",left,top-8,11,MUTED,false);
            text(g,"Green = nondominated among sampled results",left+110,top-8,11,TEAL,false);g.dispose();
        }
    }
}
