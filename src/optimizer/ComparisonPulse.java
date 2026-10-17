package optimizer;

import static optimizer.ReplayCanvas.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** A pulsing before/after readout and a sparkline made only from recorded scores. */
public final class ComparisonPulse {
    private ResultComparison comparison;
    private List<Double> history = List.of();
    private String historyLabel = "BEST SCORE HISTORY";
    private double pulseMs = 900;

    public void clear() {
        comparison = null;
        history = List.of();
        pulseMs = 900;
    }

    public void set(ResultComparison value, List<Double> history, String historyLabel) {
        if (!Objects.equals(comparison, value))
            pulseMs =
                    switch (value.outcome()) {
                        case BETTER, WORSE, INFEASIBLE -> 0;
                        default -> 900;
                    };
        comparison = value;
        this.history = new ArrayList<>();
        for (double score : history) if (Double.isFinite(score)) this.history.add(score);
        this.historyLabel = historyLabel;
    }

    public boolean isPulsing() {
        return pulseMs < 900;
    }

    public void advance(double ms) {
        pulseMs = Math.min(900, pulseMs + Math.max(0, ms));
    }

    public ResultComparison value() {
        return comparison;
    }

    private double strength() {
        double t = pulseMs / 900.;
        return t >= 1 ? 0 : (1 - t) * (.65 + .35 * Math.cos(t * 4 * Math.PI));
    }

    public void paint(Graphics2D g, int width) {
        if (comparison == null) {
            text(g, "PREVIOUS / CURRENT COMPARISON", 24, 20, 10, MUTED, true);
            text(g, "Run optimizer to compare placements", 24, 44, 13, MUTED, false);
            return;
        }
        Color color =
                switch (comparison.outcome()) {
                    case BETTER -> GREEN;
                    case WORSE -> AMBER;
                    case INFEASIBLE -> RED;
                    case FIRST -> CYAN;
                    default -> MUTED;
                };
        int left = 24, right = 215;
        double pulse = strength();
        if (pulse > 0) {
            g.setColor(alpha(color, (int) (45 * pulse)));
            g.fillRoundRect(right - 8, 1, 195, 45, 12, 12);
            g.setStroke(new BasicStroke((float) (1 + 2 * pulse)));
            g.setColor(alpha(color, (int) (180 * pulse)));
            g.drawRoundRect(
                    right - 8 - (int) (5 * pulse),
                    1 - (int) (3 * pulse),
                    195 + (int) (10 * pulse),
                    45 + (int) (6 * pulse),
                    12,
                    12);
        }
        text(g, comparison.previousLabel(), left, 13, 9, MUTED, true);
        text(g, comparison.currentLabel(), right, 13, 9, color, true);
        reading(g, comparison.previous(), left, 40, MUTED);
        reading(g, comparison.current(), right, 40, color);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(MUTED);
        g.drawLine(175, 31, 194, 31);
        g.drawLine(188, 26, 194, 31);
        g.drawLine(188, 36, 194, 31);
        text(g, changeText(comparison), left, 59, 11, color, true);
        int plotWidth = Math.min(350, Math.max(230, width / 3 - 35)),
                plotX = width - plotWidth - 24;
        text(g, historyLabel, plotX, 13, 9, MUTED, true);
        if (history.isEmpty()) {
            text(g, "Waiting for comparable scores", plotX, 40, 11, MUTED, false);
            return;
        }
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (double score : history) {
            low = Math.min(low, score);
            high = Math.max(high, score);
        }
        double pad = Math.max(.01, (high - low) * .15);
        low -= pad;
        high += pad;
        int top = 23, bottom = 49;
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(52, 80, 103));
        g.drawLine(plotX, top, plotX + plotWidth, top);
        g.drawLine(plotX, bottom, plotX + plotWidth, bottom);
        Path2D line = new Path2D.Double();
        double lastX = plotX, lastY = bottom;
        for (int i = 0; i < history.size(); i++) {
            double x = plotX + 6 + (plotWidth - 16) * i / (double) Math.max(1, history.size() - 1),
                    y = bottom - (bottom - top) * (history.get(i) - low) / (high - low);
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
            lastX = x;
            lastY = y;
        }
        g.setStroke(new BasicStroke(5));
        g.setColor(alpha(CYAN, 30));
        g.draw(line);
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(CYAN);
        g.draw(line);
        if (pulse > 0) {
            int radius = 5 + (int) (9 * pulse);
            g.setColor(alpha(color, (int) (70 * pulse)));
            g.fillOval((int) lastX - radius, (int) lastY - radius, 2 * radius, 2 * radius);
        }
        g.setColor(color);
        g.fillOval((int) lastX - 3, (int) lastY - 3, 6, 6);
        String ends =
                history.size() == 1
                        ? String.format(Locale.ROOT, "One recorded score: %.3f", history.get(0))
                        : String.format(
                                Locale.ROOT,
                                "%.3f   to   %.3f",
                                history.get(0),
                                history.get(history.size() - 1));
        text(g, ends, plotX, 60, 9, MUTED, false);
    }

    static String changeText(ResultComparison c) {
        return switch (c.outcome()) {
            case FIRST -> "First feasible result / no previous score";
            case SAME -> "NO CHANGE / 0.000 difference";
            case INFEASIBLE -> "REJECTED / constraints violated";
            case UNAVAILABLE -> "No current result available";
            case INCOMPARABLE -> "MODEL CHANGED / comparison unavailable";
            case BETTER, WORSE -> {
                String percent =
                        Double.isFinite(c.percent())
                                ? String.format(Locale.ROOT, " (%.2f%%)", Math.abs(c.percent()))
                                : "";
                yield String.format(
                        Locale.ROOT,
                        "%s / %.3f %s %s%s",
                        c.outcome() == ResultComparison.Outcome.BETTER ? "IMPROVED" : "WORSE",
                        Math.abs(c.improvement()),
                        c.current().unit(),
                        c.outcome() == ResultComparison.Outcome.BETTER ? "lower" : "higher",
                        percent);
            }
        };
    }

    private static void reading(
            Graphics2D g, ResultComparison.Reading reading, int x, int y, Color color) {
        String value =
                !reading.available()
                        ? "--"
                        : !reading.valid()
                                ? "INVALID"
                                : String.format(Locale.ROOT, "%.3f", reading.value());
        text(g, value, x, y, 22, color, true);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        if (reading.valid())
            text(
                    g,
                    reading.unit(),
                    x + g.getFontMetrics().stringWidth(value) + 6,
                    y,
                    11,
                    color,
                    false);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private static void text(
            Graphics2D g, String s, int x, int y, int size, Color c, boolean bold) {
        Visuals.text(g, s, x, y, size, c, bold);
    }
}
