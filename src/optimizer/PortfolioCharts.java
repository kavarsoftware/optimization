package optimizer;

import static optimizer.Visuals.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

/** Ten clearly labeled series; all marks are calculated from recorded trials. */
public final class PortfolioCharts extends JPanel {
    public enum Kind {
        RANKING,
        SPREAD,
        SPEED,
        CONVERGENCE,
        PAIRED
    }

    private static final Color[] COLORS = {
        new Color(112, 125, 146),
        new Color(183, 129, 26),
        new Color(67, 119, 176),
        TEAL,
        new Color(211, 94, 73),
        new Color(137, 86, 179),
        new Color(183, 76, 137),
        new Color(58, 137, 115),
        new Color(100, 121, 40),
        new Color(49, 136, 161)
    };
    final Kind kind;
    ExperimentRunner.Study study;
    SearchMethod selected = SearchMethod.ANNEALING;
    long selectedSeed = 2026;
    double pulse;

    public PortfolioCharts(Kind kind) {
        this.kind = kind;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1100, 430));
    }

    public void set(ExperimentRunner.Study study) {
        this.study = study;
        repaint();
    }

    static Color color(SearchMethod m) {
        return COLORS[AlgorithmComparison.METHODS.indexOf(m)];
    }

    private String unit() {
        return study.evaluator().data.assessment() ? "J" : ResultComparison.unit(study.evaluator());
    }

    private String f(double x) {
        return Double.isFinite(x) ? String.format(Locale.ROOT, "%.6f", x) : "--";
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        quality(g);
        String title =
                switch (kind) {
                    case RANKING -> "Quality ranking / mean and best-to-worst range";
                    case SPREAD -> "Consistency / every recorded seed is a dot";
                    case SPEED -> "Search time / mean milliseconds per run";
                    case CONVERGENCE -> "Learning curves / mean best feasible score so far";
                    case PAIRED -> "Paired seeds / selected algorithm versus SRS";
                };
        text(g, title, 20, 28, 17, INK, true);
        if (study == null || study.trials().isEmpty()) {
            text(
                    g,
                    "Run the comparison to collect actual results from ten algorithms.",
                    20,
                    62,
                    13,
                    MUTED,
                    false);
            g.dispose();
            return;
        }
        text(
                g,
                study.seeds()
                        + " paired seeds / "
                        + String.format(Locale.ROOT, "%,d", study.equalBudget())
                        + " evaluations each / lower "
                        + unit()
                        + " is better"
                        + (AlgorithmComparison.complete(study) ? "" : " / partial results"),
                20,
                49,
                12,
                MUTED,
                false);
        if (kind == Kind.CONVERGENCE) curves(g);
        else if (kind == Kind.PAIRED) paired(g);
        else rows(g);
        g.dispose();
    }

    private double exact() {
        return study.exact().provenOptimal(study.evaluator().data.searchSpace())
                ? study.exact().evaluation().score()
                : Double.NaN;
    }

    private void rows(Graphics2D g) {
        List<ExperimentRunner.Summary> rows = AlgorithmComparison.ranking(study);
        int left = 180,
                right = getWidth() - (kind == Kind.RANKING ? 222 : 145),
                top = 88,
                bottom = getHeight() - 45;
        int step = Math.max(13, (bottom - top) / 10);
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (var s : rows)
            if (s.feasibleRuns() > 0) {
                low = Math.min(low, s.best());
                high = Math.max(high, s.worst());
            }
        if (kind == Kind.SPEED) {
            low = 0;
            high = 1;
            for (var s : rows) high = Math.max(high, s.runtime() * 1.12);
        } else {
            if (Double.isFinite(exact())) low = Math.min(low, exact());
            if (!Double.isFinite(low)) {
                low = 0;
                high = 1;
            }
            double pad = Math.max(1e-6, (high - low) * .08);
            low -= pad;
            high += pad;
        }
        for (int i = 0; i <= 4; i++) {
            int x = left + (right - left) * i / 4;
            g.setColor(LINE);
            g.drawLine(x, top - 10, x, top + step * 10 - 5);
            double value = low + (high - low) * i / 4;
            text(
                    g,
                    kind == Kind.SPEED
                            ? String.format(Locale.ROOT, "%.1f", value)
                            : String.format(Locale.ROOT, "%.3f", value),
                    x - 18,
                    getHeight() - 22,
                    11,
                    MUTED,
                    false);
        }
        if (kind != Kind.SPEED && Double.isFinite(exact())) {
            int x = map(exact(), low, high, left, right);
            g.setColor(TEAL);
            g.setStroke(new BasicStroke(1, 0, 0, 1, new float[] {4, 4}, 0));
            g.drawLine(x, top - 10, x, top + step * 10 - 5);
            g.setStroke(new BasicStroke(1));
        }
        text(
                g,
                kind == Kind.SPEED
                        ? "Mean active ms"
                        : kind == Kind.SPREAD
                                ? "Feasible / planned"
                                : "Mean " + unit() + "      Exact hits",
                right + 16,
                top - 14,
                11,
                MUTED,
                true);
        int row = 0;
        for (var s : rows) {
            int y = top + row * step + step / 2;
            Color color = color(s.method());
            text(g, (++row) + "  " + s.method().shortName(), 20, y + 4, 12, color, true);
            if (kind == Kind.SPEED) {
                int w = Math.max(1, map(s.runtime(), low, high, left, right) - left);
                g.setColor(color);
                g.fillRoundRect(left, y - 7, w, 14, 7, 7);
                text(
                        g,
                        String.format(Locale.ROOT, "%.2f ms", s.runtime()),
                        right + 16,
                        y + 4,
                        12,
                        INK,
                        true);
            } else if (s.feasibleRuns() > 0) {
                if (kind == Kind.RANKING) {
                    g.setStroke(new BasicStroke(3));
                    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 85));
                    g.drawLine(
                            map(s.best(), low, high, left, right),
                            y,
                            map(s.worst(), low, high, left, right),
                            y);
                    g.setStroke(new BasicStroke(1));
                    g.setColor(color);
                    g.fillOval(map(s.mean(), low, high, left, right) - 5, y - 5, 10, 10);
                    text(
                            g,
                            f(s.mean())
                                    + String.format(Locale.ROOT, "       %.0f%%", s.optimumRate()),
                            right + 16,
                            y + 4,
                            12,
                            INK,
                            true);
                } else {
                    int k = 0;
                    for (var t : study.trials())
                        if (t.method() == s.method() && t.result().evaluation() != null) {
                            int x = map(t.result().evaluation().score(), low, high, left, right);
                            int dy = (k++ % 5 - 2) * 2;
                            g.setColor(color);
                            g.fillOval(x - 3, y + dy - 3, 6, 6);
                        }
                    text(
                            g,
                            s.feasibleRuns() + " / " + study.seeds(),
                            right + 16,
                            y + 4,
                            12,
                            INK,
                            true);
                }
            } else text(g, "No feasible result", right + 16, y + 4, 11, MUTED, false);
        }
        text(
                g,
                kind == Kind.SPEED
                        ? "Same evaluation cap; construction and algorithm overhead included. Local"
                              + " JVM timing."
                        : "Dashed line = exact optimum. Means exclude failed runs; coverage"
                              + " determines rank first.",
                20,
                getHeight() - 5,
                11,
                MUTED,
                false);
    }

    private static int map(double value, double low, double high, int left, int right) {
        return left + (int) ((right - left) * (value - low) / Math.max(1e-12, high - low));
    }

    static double at(SearchResult r, int iteration) {
        double value = Double.NaN;
        for (var p : r.trace()) {
            if (p.evaluations() > iteration) break;
            value = p.score();
        }
        return value;
    }

    private double meanAt(SearchMethod method, int iteration) {
        int n = 0;
        double sum = 0;
        for (var t : study.trials())
            if (t.method() == method) {
                double v = at(t.result(), iteration);
                if (!Double.isFinite(v)) return Double.NaN;
                n++;
                sum += v;
            }
        return n == 0 ? Double.NaN : sum / n;
    }

    private void curves(Graphics2D g) {
        int left = 80, right = getWidth() - 30, top = 108, bottom = getHeight() - 47;
        for (int i = 0; i < AlgorithmComparison.METHODS.size(); i++) {
            SearchMethod m = AlgorithmComparison.METHODS.get(i);
            int x = 22 + (i % 5) * ((getWidth() - 30) / 5), y = 72 + (i / 5) * 20;
            g.setColor(color(m));
            g.fillRect(x, y - 8, 12, 3);
            text(g, m.shortName(), x + 18, y - 3, 11, color(m), true);
        }
        double low = exact(), high = Double.NEGATIVE_INFINITY;
        for (var m : AlgorithmComparison.METHODS)
            for (int i = 1; i <= 100; i++) {
                double v = meanAt(m, Math.max(1, study.equalBudget() * i / 100));
                if (Double.isFinite(v)) {
                    low = Double.isFinite(low) ? Math.min(low, v) : v;
                    high = Math.max(high, v);
                }
            }
        if (!Double.isFinite(high)) return;
        double pad = Math.max(1e-5, (high - low) * .08);
        low -= pad;
        high += pad;
        axes(g, left, right, top, bottom, low, high);
        for (var m : AlgorithmComparison.METHODS) {
            g.setColor(color(m));
            g.setStroke(new BasicStroke(m == SearchMethod.HYBRID ? 3 : 1.7f));
            int px = 0, py = 0;
            boolean connected = false;
            for (int i = 1; i <= 100; i++) {
                int iter = Math.max(1, study.equalBudget() * i / 100);
                double value = meanAt(m, iter);
                if (!Double.isFinite(value)) {
                    connected = false;
                    continue;
                }
                int x = map(iter, 0, study.equalBudget(), left, right),
                        y = map(value, low, high, bottom, top);
                if (connected) {
                    g.drawLine(px, py, x, py);
                    g.drawLine(x, py, x, y);
                }
                px = x;
                py = y;
                connected = true;
            }
        }
        g.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 4; i++)
            text(
                    g,
                    String.format(Locale.ROOT, "%,d", study.equalBudget() * i / 4),
                    left + (right - left) * i / 4 - 15,
                    bottom + 20,
                    11,
                    MUTED,
                    false);
        text(
                g,
                "Evaluations / 100 sampled checkpoints; missing feasible incumbents leave gaps.",
                left,
                getHeight() - 5,
                11,
                MUTED,
                false);
    }

    private void axes(
            Graphics2D g, int left, int right, int top, int bottom, double low, double high) {
        for (int i = 0; i <= 4; i++) {
            int y = bottom - (bottom - top) * i / 4;
            g.setColor(LINE);
            g.drawLine(left, y, right, y);
            text(
                    g,
                    String.format(Locale.ROOT, "%.3f", low + (high - low) * i / 4),
                    12,
                    y + 4,
                    11,
                    MUTED,
                    false);
        }
        if (Double.isFinite(exact())) {
            int y = map(exact(), low, high, bottom, top);
            g.setColor(TEAL);
            g.setStroke(new BasicStroke(1, 0, 0, 1, new float[] {4, 4}, 0));
            g.drawLine(left, y, right, y);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void paired(Graphics2D g) {
        int left = 80, right = getWidth() - 30, top = 102, bottom = getHeight() - 65;
        long first =
                study.trials().stream().mapToLong(ExperimentRunner.Trial::seed).min().orElse(2026);
        double low = exact(), high = Double.NEGATIVE_INFINITY;
        for (var t : study.trials())
            if ((t.method() == selected || t.method() == SearchMethod.HYBRID)
                    && t.result().evaluation() != null) {
                double v = t.result().evaluation().score();
                low = Double.isFinite(low) ? Math.min(low, v) : v;
                high = Math.max(high, v);
            }
        if (!Double.isFinite(high)) return;
        double pad = Math.max(1e-5, (high - low) * .15);
        low -= pad;
        high += pad;
        text(
                g,
                selected.shortName() + " (colored)  /  SRS (teal)  /  dashed optimum",
                20,
                73,
                12,
                color(selected),
                true);
        axes(g, left, right, top, bottom, low, high);
        for (SearchMethod m : new SearchMethod[] {SearchMethod.HYBRID, selected}) {
            int px = 0, py = 0;
            boolean connected = false;
            for (int i = 0; i < study.seeds(); i++) {
                var t = AlgorithmComparison.trial(study, m, first + i);
                if (t == null || t.result().evaluation() == null) {
                    connected = false;
                    continue;
                }
                int x = map(i, 0, Math.max(1, study.seeds() - 1), left, right);
                int y = map(t.result().evaluation().score(), low, high, bottom, top);
                g.setColor(color(m));
                g.setStroke(new BasicStroke(2));
                if (connected) g.drawLine(px, py, x, y);
                g.fillOval(x - 4, y - 4, 8, 8);
                if (first + i == selectedSeed) {
                    int r = 7 + (int) (10 * pulse);
                    g.setStroke(new BasicStroke(2));
                    g.drawOval(x - r, y - r, r * 2, r * 2);
                }
                px = x;
                py = y;
                connected = true;
                if (m == selected
                        && (i % Math.max(1, study.seeds() / 10) == 0 || i == study.seeds() - 1))
                    text(g, Long.toString(first + i), x - 15, bottom + 20, 10, MUTED, false);
            }
        }
        var a = AlgorithmComparison.trial(study, selected, selectedSeed);
        var b = AlgorithmComparison.trial(study, SearchMethod.HYBRID, selectedSeed);
        if (a != null
                && b != null
                && a.result().evaluation() != null
                && b.result().evaluation() != null) {
            double difference = a.result().evaluation().score() - b.result().evaluation().score();
            text(
                    g,
                    "Seed "
                            + selectedSeed
                            + " / "
                            + selected.shortName()
                            + " "
                            + f(a.result().evaluation().score())
                            + " / SRS "
                            + f(b.result().evaluation().score())
                            + String.format(Locale.ROOT, " / delta %+.6f", difference),
                    20,
                    getHeight() - 22,
                    13,
                    INK,
                    true);
        }
        text(
                g,
                "Independent seed runs; lines aid comparison. Delta = selected score - SRS"
                    + " (negative favors selected).",
                20,
                getHeight() - 4,
                11,
                MUTED,
                false);
    }

    public static void export(Path folder, ExperimentRunner.Study study) throws Exception {
        Files.createDirectories(folder);
        for (Kind kind : Kind.values()) {
            PortfolioCharts chart = new PortfolioCharts(kind);
            chart.set(study);
            chart.setSize(1200, 650);
            if (!study.trials().isEmpty()) chart.selectedSeed = study.trials().get(0).seed();
            BufferedImage image = new BufferedImage(1200, 650, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            chart.paint(g);
            g.dispose();
            ImageIO.write(
                    image,
                    "png",
                    folder.resolve(kind.name().toLowerCase(Locale.ROOT) + ".png").toFile());
        }
    }
}
