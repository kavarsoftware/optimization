package optimizer;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import javax.swing.JPanel;

/** Separate units in separate panels; compute time and network delay are never added. */
public final class AssessmentPlots extends JPanel {
    public record Point(int iteration, Evaluation value) {}

    private final boolean timeline;
    private List<Point> points = List.of();
    private ProblemData data = ProblemData.assessmentDefaults();
    private Candidate candidate;
    private Evaluation evaluation;

    public AssessmentPlots(boolean timeline) {
        this.timeline = timeline;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1100, 500));
    }

    public void set(List<Point> points) {
        this.points = List.copyOf(points);
        repaint();
    }

    public void set(ProblemData data, Candidate candidate, Evaluation evaluation) {
        this.data = data;
        this.candidate = candidate;
        this.evaluation = evaluation;
        repaint();
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Visuals.quality(g);
        if (timeline) drawTimeline(g);
        else drawProgress(g);
        g.dispose();
    }

    private void drawProgress(Graphics2D g) {
        String[] labels = {
            "Best objective J (dimensionless)",
            "Mean network latency L (ms)",
            "Makespan M (s)",
            "Energy E (Wh)",
            "Cost C (USD)",
            "Worst network latency Lmax (ms)"
        };
        for (int k = 0; k < 6; k++) {
            int cellWidth = getWidth() / 2,
                    cellHeight = getHeight() / 3,
                    x = (k % 2) * cellWidth,
                    y = (k / 2) * cellHeight;
            Visuals.text(g, labels[k], x + 18, y + 24, 13, Visuals.INK, true);
            if (points.isEmpty()) {
                Visuals.text(g, "No feasible state yet", x + 18, y + 53, 12, Visuals.MUTED, false);
                continue;
            }
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (Point point : points) {
                double value = value(point.value(), k);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            double pad = Math.max(1e-5, (max - min) * .15);
            min -= pad;
            max += pad;
            int left = x + 65,
                    right = x + cellWidth - 20,
                    top = y + 43,
                    bottom = y + cellHeight - 38;
            g.setColor(Visuals.LINE);
            for (int j = 0; j <= 2; j++)
                g.drawLine(left, top + (bottom - top) * j / 2, right, top + (bottom - top) * j / 2);
            Visuals.text(
                    g,
                    String.format(Locale.ROOT, "%.3f", max),
                    x + 4,
                    top + 4,
                    10,
                    Visuals.MUTED,
                    false);
            Visuals.text(
                    g,
                    String.format(Locale.ROOT, "%.3f", min),
                    x + 4,
                    bottom + 4,
                    10,
                    Visuals.MUTED,
                    false);
            int maxIteration = Math.max(1, points.get(points.size() - 1).iteration()),
                    oldX = 0,
                    oldY = 0;
            boolean first = true;
            g.setColor(Visuals.HOSTS[k % 4]);
            g.setStroke(new BasicStroke(2));
            for (Point point : points) {
                int px = left + (right - left) * point.iteration() / maxIteration,
                        py =
                                bottom
                                        - (int)
                                                ((bottom - top)
                                                        * (value(point.value(), k) - min)
                                                        / (max - min));
                if (!first) {
                    g.drawLine(oldX, oldY, px, oldY);
                    g.drawLine(px, oldY, px, py);
                }
                oldX = px;
                oldY = py;
                first = false;
            }
            Visuals.text(g, "0", left, bottom + 18, 10, Visuals.MUTED, false);
            Visuals.text(
                    g,
                    maxIteration + " evaluations / iteration",
                    Math.max(left, right - 160),
                    bottom + 18,
                    10,
                    Visuals.MUTED,
                    false);
        }
    }

    private static double value(Evaluation e, int metric) {
        switch (metric) {
            case 0:
                return e.score();
            case 1:
                return e.delayMs();
            case 2:
                return e.makespanSeconds();
            case 3:
                return e.energyWh();
            case 4:
                return e.cost();
            default:
                return e.worstLatencyMs();
        }
    }

    private void drawTimeline(Graphics2D g) {
        Visuals.text(
                g,
                "COMPUTE TIMELINE / services in ascending ID order",
                24,
                30,
                18,
                Visuals.INK,
                true);
        if (candidate == null || evaluation == null) {
            Visuals.text(
                    g,
                    "Run a search or load the reference to see sequential work / speed durations.",
                    24,
                    80,
                    14,
                    Visuals.MUTED,
                    false);
            return;
        }
        int left = 150, right = getWidth() - 35, top = 85, row = (getHeight() - 150) / 4;
        double max = Math.max(.01, evaluation.makespanSeconds());
        for (int s = 0; s < data.servers().size(); s++) {
            int y = top + s * row;
            Visuals.text(g, data.servers().get(s).name(), 24, y + 20, 18, Visuals.INK, true);
            Visuals.text(
                    g,
                    String.format(Locale.ROOT, "%.3f s", evaluation.usage().get(s).busySeconds()),
                    24,
                    y + 44,
                    12,
                    Visuals.MUTED,
                    false);
            double cursor = 0;
            List<Integer> order = order(data, candidate, s);
            for (int i : order) {
                double duration = data.services().get(i).work() / data.servers().get(s).speed();
                int start = left + (int) ((right - left) * cursor / max),
                        end = left + (int) ((right - left) * (cursor + duration) / max);
                g.setColor(Visuals.HOSTS[s % 4]);
                g.fillRoundRect(start, y, Math.max(1, end - start - 2), 44, 6, 6);
                Visuals.text(
                        g, data.services().get(i).name(), start + 6, y + 27, 12, Color.WHITE, true);
                cursor += duration;
            }
        }
        for (int k = 0; k <= 5; k++) {
            int x = left + (right - left) * k / 5;
            g.setColor(Visuals.LINE);
            g.drawLine(x, top - 10, x, getHeight() - 60);
            Visuals.text(
                    g,
                    String.format(Locale.ROOT, "%.2f s", max * k / 5),
                    x - 12,
                    getHeight() - 38,
                    11,
                    Visuals.MUTED,
                    false);
        }
        Visuals.text(
                g,
                String.format(
                        Locale.ROOT, "Makespan %.6f s; network delay is assessed separately", max),
                left,
                58,
                13,
                Visuals.TEAL,
                true);
    }

    static List<Integer> order(ProblemData data, Candidate candidate, int server) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < data.services().size(); i++)
            if (candidate.server(i) == server) order.add(i);
        for (int a = 0; a < order.size(); a++)
            for (int b = a + 1; b < order.size(); b++)
                if (data.services()
                                .get(order.get(a))
                                .name()
                                .compareTo(data.services().get(order.get(b)).name())
                        > 0) {
                    int value = order.get(a);
                    order.set(a, order.get(b));
                    order.set(b, value);
                }
        return order;
    }

    public static void export(
            Path folder, ProblemData data, SearchResult result, List<Point> points)
            throws Exception {
        AssessmentPlots timeline = new AssessmentPlots(true);
        timeline.set(data, result.best(), result.evaluation());
        StudyCharts.png(folder.resolve("compute-timeline.png"), timeline, 1500, 750);
        AssessmentPlots progress = new AssessmentPlots(false);
        progress.set(points);
        StudyCharts.png(folder.resolve("performance.png"), progress, 1500, 1000);
        List<String> rows = new ArrayList<>();
        rows.add(
                "iteration,best_J,makespan_s,cost_usd,energy_wh,mean_network_ms,worst_network_ms,sla_violations");
        for (Point point : points) {
            Evaluation e = point.value();
            rows.add(
                    CsvFiles.row(
                            point.iteration(),
                            e.score(),
                            e.makespanSeconds(),
                            e.cost(),
                            e.energyWh(),
                            e.delayMs(),
                            e.worstLatencyMs(),
                            e.slaViolations()));
        }
        Files.write(folder.resolve("performance.csv"), rows, StandardCharsets.UTF_8);
    }
}
