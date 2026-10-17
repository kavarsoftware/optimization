package optimizer;

import static optimizer.Visuals.HOSTS;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.*;

import javax.swing.*;

/** Animated view of real recorded proposals, including the rollback of unaccepted trials. */
public final class ReplayCanvas extends JPanel {
    static final Color BG = new Color(9, 18, 34),
            PANEL = new Color(16, 31, 51),
            TEXT = new Color(227, 240, 255),
            MUTED = new Color(131, 155, 184),
            CYAN = new Color(64, 213, 246),
            GREEN = new Color(70, 232, 166),
            RED = new Color(255, 111, 133),
            AMBER = new Color(255, 199, 97);
    private boolean live;

    public void setLive(boolean live) {
        this.live = live;
    }

    private ProblemData data = ProblemData.demo();
    private SearchEvent event;
    private int minimumServices = 2;
    private double fraction = 1;
    private final java.util.List<Rectangle2D> hitboxes = new ArrayList<>();
    final ComparisonPulse comparison = new ComparisonPulse();

    public ReplayCanvas() {
        setBackground(BG);
        setPreferredSize(new Dimension(1100, 300));
        setMinimumSize(new Dimension(600, 220));
        setToolTipText("Select a recorded step to inspect the service placement");
    }

    public void setData(ProblemData data) {
        this.data = data;
        event = null;
        comparison.clear();
        repaint();
    }

    public void setMinimumServices(int minimumServices) {
        this.minimumServices = minimumServices;
    }

    public void setComparison(
            ResultComparison value, java.util.List<Double> history, String label) {
        comparison.set(value, history, label);
        repaint();
    }

    public void advanceComparison(double milliseconds) {
        comparison.advance(milliseconds);
        repaint();
    }

    public boolean isComparisonPulsing() {
        return comparison.isPulsing();
    }

    public void setEvent(SearchEvent event, double fraction) {
        this.event = event;
        this.fraction = Math.max(0, Math.min(1, fraction));
        repaint();
    }

    private boolean trial() {
        return event != null
                && (event.type() == SearchEvent.Type.MOVE || event.type() == SearchEvent.Type.SWAP);
    }

    private static double ease(double value) {
        return value * value * (3 - 2 * value);
    }

    private double movement() {
        if (trial())
            return fraction < .62 ? ease(fraction / .62) : 1 - ease((fraction - .62) / .38);
        return ease(fraction);
    }

    private Rectangle2D.Double rack(int server) {
        double gap = 16,
                w = (getWidth() - 48 - gap * (data.servers().size() - 1)) / data.servers().size();
        double y = Math.min(94, Math.max(64, getHeight() - 200));
        return new Rectangle2D.Double(
                24 + server * (w + gap),
                y,
                w,
                Math.max(data.assessment() ? 240 : 145, getHeight() - y - 17));
    }

    private Point2D.Double[] positions(Candidate candidate) {
        int n = data.services().size();
        Point2D.Double[] points = new Point2D.Double[n];
        if (candidate == null) {
            for (int i = 0; i < n; i++) {
                double angle = i * 2 * Math.PI / n;
                points[i] =
                        new Point2D.Double(
                                getWidth() / 2. + 32 * Math.cos(angle), 48 + 18 * Math.sin(angle));
            }
            return points;
        }
        int[] counts = new int[data.servers().size()], rank = new int[counts.length];
        for (int i = 0; i < n; i++) counts[candidate.server(i)]++;
        for (int i = 0; i < n; i++) {
            int s = candidate.server(i), columns = counts[s] > 4 ? 3 : 2, index = rank[s]++;
            var r = rack(s);
            double step = (r.width - 22) / columns;
            double top =
                    data.assessment()
                            ? r.y + 159
                            : counts[s] > 6 ? r.y + 94 : r.y + (r.height > 175 ? 119 : 107);
            double row = counts[s] > 6 ? 17 : 25;
            points[i] =
                    new Point2D.Double(
                            r.x + 11 + step * (index % columns + .5),
                            top + 10 + (index / columns) * row);
        }
        return points;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Visuals.quality(g);
        g.setPaint(new GradientPaint(0, 0, BG, getWidth(), getHeight(), new Color(10, 30, 43)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(56, 97, 122, 42));
        for (int x = 20; x < getWidth(); x += 30)
            for (int y = 16; y < getHeight(); y += 30) g.fillRect(x, y, 1, 1);
        int center = getWidth() / 2;
        comparison.paint(g, getWidth());
        label(g, live ? "LIVE SEARCH" : "RECORDED REPLAY", center - 41, 11, 8, MUTED, true);
        g.setColor(new Color(27, 74, 96));
        g.fillOval(center - 26, 20, 52, 52);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(CYAN);
        g.drawOval(center - 26, 20, 52, 52);
        label(g, "SEARCH", center - 21, 50, 10, CYAN, true);
        double motion = movement();
        for (int s = 0; s < data.servers().size(); s++) {
            var host = data.servers().get(s);
            var r = rack(s);
            Color color = HOSTS[s % HOSTS.length];
            Path2D wire = new Path2D.Double();
            wire.moveTo(center, 72);
            wire.curveTo(center, 88, r.getCenterX(), r.y - 18, r.getCenterX(), r.y);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(alpha(color, 110));
            g.draw(wire);
            if (event != null && fraction < 1) {
                double t = fraction,
                        px = (1 - t) * center + t * r.getCenterX(),
                        py = 72 + t * (r.y - 72);
                g.setColor(alpha(color, 45));
                g.fillOval((int) px - 7, (int) py - 7, 14, 14);
                g.setColor(color);
                g.fillOval((int) px - 3, (int) py - 3, 6, 6);
            }
            Evaluation.Usage from = usage(event == null ? null : event.beforeEvaluation(), s),
                    to = usage(event == null ? null : event.evaluation(), s);
            double resourceMotion = data.assessment() ? 1 : motion;
            double cpu = lerp(from.cpu(), to.cpu(), resourceMotion),
                    ram = lerp(from.ram(), to.ram(), resourceMotion),
                    storage = lerp(from.storage(), to.storage(), resourceMotion);
            boolean overloaded =
                    cpu > host.cpu() + 1e-6
                            || ram > host.ram() + 1e-6
                            || storage > host.storage() + 1e-6;
            g.setColor(PANEL);
            g.fillRoundRect((int) r.x, (int) r.y, (int) r.width, (int) r.height, 14, 14);
            g.setStroke(new BasicStroke(overloaded ? 2f : 1.2f));
            g.setColor(overloaded ? RED : alpha(color, 170));
            g.drawRoundRect((int) r.x, (int) r.y, (int) r.width, (int) r.height, 14, 14);
            g.setColor(color);
            g.fillRoundRect((int) r.x + 12, (int) r.y + 13, 5, 20, 4, 4);
            label(g, host.name(), (int) r.x + 26, (int) r.y + 29, 17, TEXT, true);
            label(
                    g,
                    String.format(Locale.ROOT, "%.0f ms", host.latencyMs()),
                    (int) (r.x + r.width) - 65,
                    (int) r.y + 28,
                    12,
                    color,
                    true);
            int count = resourceMotion > .5 ? to.count() : from.count();
            label(
                    g,
                    data.assessment()
                            ? count + " services / max " + host.maxServices()
                            : count + " services / minimum " + minimumServices,
                    (int) r.x + 26,
                    (int) r.y + 42,
                    9,
                    event != null
                                    && (data.assessment()
                                            ? count > host.maxServices()
                                            : count < minimumServices)
                            ? RED
                            : MUTED,
                    false);
            meter(g, "CPU cores", cpu, host.cpu(), r.x + 13, r.y + 51, r.width - 26, color);
            meter(g, "RAM GB", ram, host.ram(), r.x + 13, r.y + 71, r.width - 26, color);
            meter(g, "DISK GB", storage, host.storage(), r.x + 13, r.y + 91, r.width - 26, color);
            if (data.assessment()) {
                meter(
                        g,
                        "GPU slots",
                        lerp(from.gpu(), to.gpu(), resourceMotion),
                        host.gpu(),
                        r.x + 13,
                        r.y + 111,
                        r.width - 26,
                        color);
                meter(
                        g,
                        "Service slots",
                        lerp(from.count(), to.count(), resourceMotion),
                        host.maxServices(),
                        r.x + 13,
                        r.y + 131,
                        r.width - 26,
                        color);
            }
        }
        hitboxes.clear();
        if (event == null || event.candidate() == null) {
            label(
                    g,
                    event == null
                            ? "Run the optimizer to watch services move between servers"
                            : "No complete placement found. See the stop reason and violations.",
                    Math.max(24, center - 215),
                    getHeight() - 30,
                    14,
                    MUTED,
                    false);
            g.dispose();
            return;
        }
        Point2D.Double[] before = positions(event.before()), after = positions(event.candidate());
        int[] counts = new int[data.servers().size()];
        if (event.candidate() != null)
            for (int i = 0; i < event.candidate().size(); i++)
                counts[event.candidate().server(i)]++;
        java.util.List<Integer> drawOrder = new ArrayList<>();
        for (int i = 0; i < data.services().size(); i++) {
            hitboxes.add(null);
            if (event.before() != null
                    && event.candidate() != null
                    && event.before().server(i) == event.candidate().server(i)) drawOrder.add(i);
        }
        for (int i = 0; i < data.services().size(); i++)
            if (event.before() == null
                    || event.candidate() == null
                    || event.before().server(i) != event.candidate().server(i)) drawOrder.add(i);
        for (int i : drawOrder) {
            double x = lerp(before[i].x, after[i].x, motion),
                    y = lerp(before[i].y, after[i].y, motion);
            int server = event.candidate() == null ? 0 : event.candidate().server(i);
            Color color = HOSTS[server % HOSTS.length];
            boolean changed =
                    event.before() == null
                            || event.candidate() == null
                            || event.before().server(i) != event.candidate().server(i);
            if (changed && fraction < 1) {
                g.setColor(alpha(color, 95));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Line2D.Double(before[i], after[i]));
            }
            Color accent =
                    trial()
                                    && changed
                                    && event.evaluation() != null
                                    && !event.evaluation().feasible()
                            ? RED
                            : event.newBest() && changed ? GREEN : color;
            int columns = counts[server] > 4 ? 3 : 2;
            double width = Math.min(150, (rack(server).width - 22) / columns - 6),
                    height = counts[server] > 6 ? 16 : 22;
            Rectangle2D.Double box =
                    new Rectangle2D.Double(x - width / 2, y - height / 2, width, height);
            hitboxes.set(i, box);
            if (changed && fraction < 1) {
                g.setColor(alpha(accent, 30));
                g.fillRoundRect(
                        (int) box.x - 4,
                        (int) box.y - 4,
                        (int) width + 8,
                        (int) height + 8,
                        12,
                        12);
            }
            g.setColor(new Color(22, 47, 68));
            g.fillRoundRect((int) box.x, (int) box.y, (int) width, (int) height, 7, 7);
            g.setColor(alpha(accent, changed ? 230 : 110));
            g.setStroke(new BasicStroke(changed ? 1.5f : 1f));
            g.drawRoundRect((int) box.x, (int) box.y, (int) width, (int) height, 7, 7);
            String name = data.services().get(i).name();
            if (data.assessment() && event.candidate() != null) {
                ProblemData.Server placed = data.servers().get(event.candidate().server(i));
                double latency =
                        placed.latencyMs()
                                + 8000. * data.services().get(i).payload() / placed.bandwidth();
                name += String.format(java.util.Locale.ROOT, " %.1f ms", latency);
            }
            g.setFont(
                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            data.assessment() ? 10 : counts[server] > 6 ? 10 : 12));
            int textWidth = g.getFontMetrics().stringWidth(name);
            g.setColor(TEXT);
            g.drawString(name, (int) (x - textWidth / 2.), (int) (y + 4));
        }
        g.dispose();
    }

    private static Evaluation.Usage usage(Evaluation evaluation, int server) {
        return evaluation == null || evaluation.usage().size() <= server
                ? new Evaluation.Usage(0, 0, 0, 0)
                : evaluation.usage().get(server);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static void label(
            Graphics2D g, String text, int x, int y, int size, Color color, boolean bold) {
        Visuals.text(g, text, x, y, size, color, bold);
    }

    private void meter(
            Graphics2D g,
            String name,
            double used,
            double cap,
            double x,
            double y,
            double width,
            Color color) {
        Color c = used > cap + 1e-6 ? RED : color;
        label(g, name, (int) x, (int) y, 9, MUTED, true);
        String amount =
                String.format(
                        Locale.ROOT,
                        "%.0f / %.0f  (%.0f%%)",
                        used,
                        cap,
                        100 * used / Math.max(1, cap));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(used > cap + 1e-6 ? RED : TEXT);
        g.drawString(amount, (int) (x + width - g.getFontMetrics().stringWidth(amount)), (int) y);
        g.setColor(new Color(29, 49, 70));
        g.fillRoundRect((int) x, (int) y + 4, (int) width, 4, 4, 4);
        g.setColor(c);
        g.fillRoundRect(
                (int) x,
                (int) y + 4,
                (int) (width * Math.max(0, Math.min(1, used / Math.max(1, cap)))),
                4,
                4,
                4);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (e.getY() < 65 && comparison.value() != null) return comparison.value().note();
        for (int i = 0; i < hitboxes.size(); i++)
            if (hitboxes.get(i) != null && hitboxes.get(i).contains(e.getPoint())) {
                var s = data.services().get(i);
                return s.name()
                        + ": CPU "
                        + s.cpu()
                        + " cores, RAM "
                        + s.ram()
                        + " GB, storage "
                        + s.storage()
                        + " GB, service latency / legacy weight input "
                        + s.latencyValue();
            }
        return "Recorded simulation. Trial moves return to their starting placement until an"
                + " improvement is accepted.";
    }
}
