package optimizer;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Locale;
import javax.swing.*;

/** Shows a real evaluated assignment. Moving service chips animate between actual states. */
public final class AssignmentCanvas extends JPanel {
    private ProblemData data = ProblemData.assessmentDefaults();
    private SearchEvent event;
    private double fraction = 1;

    public AssignmentCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1100, 420));
    }
    public void setData(ProblemData data) { this.data = data; event = null; repaint(); }
    public void setEvent(SearchEvent event, double fraction) {
        this.event = event;
        this.fraction = Math.max(0, Math.min(1, fraction));
        repaint();
    }
    private int panelWidth() { return (getWidth() - 40) / 4; }
    private Point2D position(Candidate candidate, int service) {
        if (candidate == null) return new Point2D.Double(getWidth() / 2., 35);
        int host = candidate.server(service), rank = 0;
        for (int i = 0; i < service; i++) if (candidate.server(i) == host) rank++;
        return new Point2D.Double(20 + host * panelWidth() + panelWidth() / 2., 260 + rank * 24);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Visuals.quality(g);
        boolean feasible = event != null && event.evaluation() != null && event.evaluation().feasible();
        String label = event == null ? "Start a search to see service placement"
                : event.evaluation() == null ? "No feasible solution found"
                : feasible ? String.format(Locale.ROOT, "FEASIBLE / J = %.6f", event.evaluation().score())
                : "REJECTED / " + String.join("; ", event.evaluation().violations());
        Visuals.text(g, label, 20, 25, 13, event == null ? Visuals.MUTED : feasible ? Visuals.TEAL : Color.RED, true);
        for (int host = 0; host < 4; host++) {
            var server = data.servers().get(host);
            int x = 20 + host * panelWidth(), width = panelWidth() - 12;
            g.setColor(Visuals.LINE);
            g.drawRect(x, 55, width, Math.max(350, getHeight() - 70));
            Visuals.text(g, "Server " + server.name(), x + 12, 78, 17, Visuals.HOSTS[host], true);
            Visuals.text(g, "RTT " + server.latencyMs() + " ms", x + 12, 98, 11, Visuals.MUTED, false);
            if (event != null && event.evaluation() != null) {
                var u = event.evaluation().usage().get(host);
                meter(g, x + 12, 125, width - 24, "CPU", u.cpu(), server.cpu(), host);
                meter(g, x + 12, 149, width - 24, "RAM GB", u.ram(), server.ram(), host);
                meter(g, x + 12, 173, width - 24, "Disk GB", u.storage(), server.storage(), host);
                meter(g, x + 12, 197, width - 24, "GPU", u.gpu(), server.gpu(), host);
                meter(g, x + 12, 221, width - 24, "Service slots", u.count(), server.maxServices(), host);
            }
        }
        if (event != null && event.candidate() != null) {
            double t = fraction * fraction * (3 - 2 * fraction);
            for (int i = 0; i < 12; i++) {
                int host = event.candidate().server(i);
                Point2D from = position(event.before(), i), to = position(event.candidate(), i);
                int x = (int) (from.getX() + t * (to.getX() - from.getX()));
                int y = (int) (from.getY() + t * (to.getY() - from.getY()));
                int width = Math.min(180, panelWidth() - 32);
                var service = data.services().get(i);
                var server = data.servers().get(host);
                double latency = server.latencyMs() + 8000 * service.payload() / server.bandwidth();
                g.setColor(latency > service.maxLatency() + 1e-9 ? Color.RED : Visuals.HOSTS[host]);
                g.drawLine(20 + host * panelWidth() + panelWidth() / 2, 240, x, y);
                g.setColor(Color.WHITE);
                g.fillRect(x - width / 2, y - 9, width, 21);
                g.setColor(Visuals.HOSTS[host]);
                g.drawRect(x - width / 2, y - 9, width, 21);
                Visuals.text(g, String.format(Locale.ROOT, "%s / %.1f ms", service.name(), latency), x - width / 2 + 8, y + 6, 11, Visuals.INK, true);
            }
        }
        g.dispose();
    }
    private void meter(Graphics2D g, int x, int y, int width, String label, double used, double capacity, int host) {
        Color color = used > capacity + 1e-9 ? Color.RED : Visuals.HOSTS[host];
        Visuals.text(g, String.format(Locale.ROOT, "%s: %.1f / %.1f", label, used, capacity), x, y, 11, color, false);
        g.setColor(Visuals.LINE);
        g.fillRect(x, y + 4, width, 4);
        g.setColor(color);
        g.fillRect(x, y + 4, (int) (width * Math.min(1, used / Math.max(1, capacity))), 4);
    }
}
