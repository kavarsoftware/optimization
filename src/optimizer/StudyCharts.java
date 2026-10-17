package optimizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Small Java2D charts: no chart library is needed. All plotted values come from run records. */
public final class StudyCharts extends JPanel {
    public enum Kind {
        CONVERGENCE("Convergence"),
        EVALUATIONS("Evaluations"),
        QUALITY("Mean and range"),
        DOTS("Seed results"),
        SPACE("Search space"),
        GROWTH("4^n growth");
        final String title;

        Kind(String title) {
            this.title = title;
        }

        public String toString() {
            return title;
        }
    }

    private ExperimentRunner.Study study;
    private final Kind kind;
    private int left, right, top, bottom;
    private double low, high;

    public StudyCharts(Kind kind) {
        this.kind = kind;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 480));
    }

    public void set(ExperimentRunner.Study study) {
        this.study = study;
        repaint();
    }

    public static String name(SearchMethod method) {
        switch (method) {
            case HYBRID:
                return "SRS (my method)";
            case EXACT:
                return "Exact";
            case RANDOM:
                return "Random";
            case TARGET:
                return "Target";
            default:
                return method.shortName();
        }
    }

    private static Color color(SearchMethod method) {
        switch (method) {
            case HYBRID:
                return new Color(0, 153, 134);
            case EXACT:
                return Visuals.BLUE;
            case TARGET:
                return new Color(205, 133, 30);
            default:
                return new Color(126, 139, 158);
        }
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Visuals.quality(g);
        left = 92;
        right = getWidth() - 35;
        top = 84;
        bottom = getHeight() - 90;
        text(g, kind.title, 24, 30, 20, Visuals.INK, true);
        if (kind == Kind.GROWTH) {
            growth(g);
            g.dispose();
            return;
        }
        if (study == null) {
            text(g, "Run the study to compare measured results.", 24, 66, 15, Visuals.MUTED, false);
            g.dispose();
            return;
        }
        text(
                g,
                study.evaluator().objective
                        + " / score unit: "
                        + ResultComparison.unit(study.evaluator())
                        + " / main protocol",
                24,
                54,
                12,
                Visuals.MUTED,
                false);
        switch (kind) {
            case CONVERGENCE:
                convergence(g);
                break;
            case EVALUATIONS:
                bars(g, false);
                break;
            case QUALITY:
                bars(g, true);
                break;
            case DOTS:
                dots(g);
                break;
            case SPACE:
                space(g);
                break;
            default:
                break;
        }
        g.dispose();
    }

    private void axes(Graphics2D g, double minimum, double maximum, String unit) {
        low = minimum;
        high = maximum;
        if (high - low < 1e-10) high = low + 1;
        for (int k = 0; k <= 4; k++) {
            int y = bottom - (bottom - top) * k / 4;
            g.setColor(Visuals.LINE);
            g.setStroke(new BasicStroke(1));
            g.drawLine(left, y, right, y);
            text(
                    g,
                    String.format(Locale.ROOT, "%.2f", low + (high - low) * k / 4),
                    18,
                    y + 5,
                    12,
                    Visuals.MUTED,
                    false);
        }
        text(g, unit, left, top - 15, 12, Visuals.MUTED, false);
    }

    private int y(double value) {
        return bottom - (int) ((bottom - top) * (value - low) / (high - low));
    }

    private int center(int index, int count) {
        return left + (right - left) * (2 * index + 1) / (2 * count);
    }

    private void legend(Graphics2D g) {
        int x = left;
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            g.setColor(color(method));
            g.fillRoundRect(x, getHeight() - 34, 14, 5, 3, 3);
            String label = name(method);
            text(
                    g,
                    label,
                    x + 21,
                    getHeight() - 27,
                    12,
                    color(method),
                    method == SearchMethod.HYBRID);
            x += method == SearchMethod.HYBRID ? 190 : 135;
        }
    }

    private List<ExperimentRunner.Trial> representatives() {
        List<ExperimentRunner.Trial> result = new ArrayList<>();
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            List<ExperimentRunner.Trial> selected =
                    ExperimentRunner.select(study.trials(), method, "main");
            if (!selected.isEmpty()) result.add(selected.get(0));
        }
        return result;
    }

    private void convergence(Graphics2D g) {
        double minimum = study.evaluator().optimisticBound(), maximum = minimum + 1;
        for (ExperimentRunner.Trial trial : representatives())
            for (SearchResult.Point point : trial.result().trace())
                maximum = Math.max(maximum, point.score());
        axes(
                g,
                Math.max(0, minimum - .5),
                maximum + .5,
                ResultComparison.unit(study.evaluator()) + " (lower is better)");
        double maxX = Math.log10(1 + study.evaluator().data.searchSpace());
        text(g, "0", left, bottom + 21, 11, Visuals.MUTED, false);
        for (int k = 1; k <= Math.log10(study.evaluator().data.searchSpace()); k++) {
            int x = left + (int) ((right - left) * Math.log10(1 + Math.pow(10, k)) / maxX);
            text(g, "10^" + k, x - 12, bottom + 21, 11, Visuals.MUTED, false);
        }
        for (ExperimentRunner.Trial trial : representatives()) {
            SearchMethod method = trial.method();
            int oldX = 0, oldY = 0;
            boolean first = true;
            g.setColor(color(method));
            if (method == SearchMethod.RANDOM) g.setStroke(new BasicStroke(5f));
            else if (method == SearchMethod.TARGET)
                g.setStroke(
                        new BasicStroke(
                                2f,
                                BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_MITER,
                                10,
                                new float[] {6, 5},
                                0));
            else g.setStroke(new BasicStroke(method == SearchMethod.HYBRID ? 3.8f : 1.8f));
            for (SearchResult.Point point : trial.result().trace()) {
                int x = left + (int) ((right - left) * Math.log10(1 + point.evaluations()) / maxX),
                        yy = y(point.score());
                if (!first) {
                    g.drawLine(oldX, oldY, x, oldY);
                    g.drawLine(x, oldY, x, yy);
                }
                oldX = x;
                oldY = yy;
                first = false;
            }
        }
        g.setColor(Visuals.MUTED);
        g.setStroke(
                new BasicStroke(
                        1.5f,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10,
                        new float[] {7, 5},
                        0));
        int bound = y(minimum);
        g.drawLine(left, bound, right, bound);
        text(
                g,
                String.format(
                        Locale.ROOT,
                        "Optimistic lower bound %.3f %s (ignores capacity/count)",
                        minimum,
                        ResultComparison.unit(study.evaluator())),
                left + 8,
                bound - 8,
                11,
                Visuals.MUTED,
                false);
        text(
                g,
                "Evaluations (log10 scale); first-seed runs / dashed Target overlaps Random where"
                        + " samples match",
                left,
                bottom + 43,
                12,
                Visuals.MUTED,
                false);
        legend(g);
    }

    private void bars(Graphics2D g, boolean quality) {
        double max = 1;
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary s = ExperimentRunner.summarize(study, method, "main");
            if (quality && Double.isFinite(s.worst())) max = Math.max(max, s.worst());
            if (!quality) max = Math.max(max, Math.log10(1 + s.evaluations()));
        }
        axes(
                g,
                0,
                max * 1.18,
                quality
                        ? "Score (" + ResultComparison.unit(study.evaluator()) + ")"
                        : "Evaluations, log10(1 + count)");
        int index = 0, width = Math.min(125, (right - left) / 7);
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary s = ExperimentRunner.summarize(study, method, "main");
            int x = center(index++, 4);
            if (s.runs() == 0 || quality && !Double.isFinite(s.mean())) continue;
            double value = quality ? s.mean() : Math.log10(1 + s.evaluations());
            int yy = y(value);
            g.setColor(color(method));
            g.fillRoundRect(x - width / 2, yy, width, bottom - yy, 7, 7);
            if (quality) {
                g.setColor(Visuals.INK);
                g.setStroke(new BasicStroke(2));
                g.drawLine(x, y(s.best()), x, y(s.worst()));
                g.drawLine(x - 12, y(s.best()), x + 12, y(s.best()));
                g.drawLine(x - 12, y(s.worst()), x + 12, y(s.worst()));
            }
            String label =
                    quality
                            ? String.format(Locale.ROOT, "%.3f", s.mean())
                            : String.format(Locale.ROOT, "%,.0f", s.evaluations());
            text(
                    g,
                    label,
                    x - width / 2,
                    Math.min(yy, quality ? y(s.worst()) : yy) - 10,
                    14,
                    color(method),
                    true);
            text(
                    g,
                    name(method),
                    x - 65,
                    bottom + 24,
                    12,
                    color(method),
                    method == SearchMethod.HYBRID);
        }
        text(
                g,
                quality
                        ? "Bars = mean; whiskers = min/max. Exact is one deterministic run; other"
                                + " methods use paired seeds."
                        : "Mean evaluations per run. Random = 100,000; Target <= 500,000; SRS uses"
                                + " its batch stop rule.",
                left,
                getHeight() - 30,
                12,
                Visuals.MUTED,
                false);
    }

    private void dots(Graphics2D g) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (ExperimentRunner.Trial trial : study.trials())
            if (trial.protocol().equals("main") && trial.result().evaluation() != null) {
                double score = trial.result().evaluation().score();
                min = Math.min(min, score);
                max = Math.max(max, score);
            }
        if (!Double.isFinite(min)) return;
        axes(
                g,
                Math.max(0, min - .4),
                max + .4,
                "Score (" + ResultComparison.unit(study.evaluator()) + ") / one dot per run");
        int group = 0;
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            List<ExperimentRunner.Trial> selected =
                    ExperimentRunner.select(study.trials(), method, "main");
            int x = center(group++, 4), i = 0;
            for (ExperimentRunner.Trial trial : selected) {
                if (trial.result().evaluation() == null) continue;
                // Spread equal values sideways so repeated successes remain visible.
                int offset =
                        (int)
                                ((i - (selected.size() - 1) / 2.0)
                                        * Math.min(10, 120.0 / Math.max(1, selected.size() - 1)));
                int yy = y(trial.result().evaluation().score());
                g.setColor(color(method));
                g.fillOval(x + offset - 3, yy - 3, 6, 6);
                i++;
            }
            text(
                    g,
                    name(method),
                    x - 65,
                    bottom + 24,
                    12,
                    color(method),
                    method == SearchMethod.HYBRID);
            text(g, selected.size() + " runs", x - 25, bottom + 45, 11, Visuals.MUTED, false);
        }
        text(
                g,
                "Horizontal offsets only separate overlapping dots; vertical position is the"
                        + " measured score.",
                left,
                getHeight() - 20,
                12,
                Visuals.MUTED,
                false);
    }

    private void space(Graphics2D g) {
        long total = study.evaluator().data.searchSpace();
        boolean complete = study.exact().completeEnumeration(total);
        String[] names = {
            "Total "
                    + study.evaluator().data.servers().size()
                    + "^"
                    + study.evaluator().data.services().size(),
            "Feasible (Exact)",
            "Exact unique",
            "Random unique",
            "Target unique",
            "SRS unique"
        };
        double[] values = {
            total,
            complete ? study.exact().feasible() : Double.NaN,
            study.exact().unique(),
            ExperimentRunner.summarize(study, SearchMethod.RANDOM, "main").unique(),
            ExperimentRunner.summarize(study, SearchMethod.TARGET, "main").unique(),
            ExperimentRunner.summarize(study, SearchMethod.HYBRID, "main").unique()
        };
        axes(g, 0, Math.log10(1 + total) * 1.18, "Assignments, log10(1 + count)");
        for (int i = 0; i < values.length; i++) {
            int x = center(i, values.length), width = Math.min(95, (right - left) / 9);
            if (!Double.isFinite(values[i])) {
                text(g, "Unknown", x - 30, top + 30, 12, Visuals.MUTED, false);
                continue;
            }
            int yy = y(Math.log10(1 + values[i]));
            g.setColor(i == 5 ? Visuals.TEAL : i == 1 ? new Color(142, 95, 205) : Visuals.BLUE);
            g.fillRoundRect(x - width / 2, yy, width, bottom - yy, 6, 6);
            text(
                    g,
                    String.format(Locale.ROOT, "%,.0f", values[i]),
                    x - width / 2,
                    yy - 10,
                    12,
                    Visuals.INK,
                    true);
            text(g, names[i], x - 51, bottom + 24, 10, Visuals.MUTED, false);
        }
        text(
                g,
                "Separate, overlapping sets: these bars are not stacked. Stochastic unique counts"
                        + " are means across seeds.",
                left,
                getHeight() - 30,
                12,
                Visuals.MUTED,
                false);
    }

    private void growth(Graphics2D g) {
        text(
                g,
                "Week 1, slide 15 / four host choices for each service",
                24,
                54,
                12,
                Visuals.MUTED,
                false);
        axes(g, 0, 8, "log10(number of assignments)");
        int oldX = 0, oldY = 0;
        for (int n = 1; n <= 12; n++) {
            int x = left + (right - left) * (n - 1) / 11, yy = y(n * Math.log10(4));
            g.setColor(n == 12 ? Visuals.TEAL : Visuals.BLUE);
            g.setStroke(new BasicStroke(3));
            if (n > 1) g.drawLine(oldX, oldY, x, yy);
            g.fillOval(x - 5, yy - 5, 10, 10);
            text(g, String.valueOf(n), x - 4, bottom + 23, 12, Visuals.MUTED, n == 12);
            oldX = x;
            oldY = yy;
        }
        text(
                g,
                "n = 12: 4^12 = 16,777,216 assignments",
                Math.max(left, right - 350),
                top + 15,
                15,
                Visuals.TEAL,
                true);
        text(
                g,
                "Number of services n (1 to 12); each added service multiplies the space by four",
                left,
                getHeight() - 32,
                12,
                Visuals.MUTED,
                false);
    }

    private static void text(
            Graphics2D g, String text, int x, int y, int size, Color color, boolean bold) {
        Visuals.text(g, text, x, y, size, color, bold);
    }

    public static void png(Path file, JComponent component, int width, int height)
            throws Exception {
        Files.createDirectories(file.getParent());
        component.setSize(width, height);
        Main.layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        component.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    public static void export(Path directory, ExperimentRunner.Study study) throws Exception {
        Files.createDirectories(directory);
        for (Kind kind : Kind.values()) {
            StudyCharts chart = new StudyCharts(kind);
            chart.set(study);
            png(directory.resolve(kind.name().toLowerCase(Locale.ROOT) + ".png"), chart, 1400, 800);
        }
        List<String> qualityRows = new ArrayList<>(),
                effortRows = new ArrayList<>(),
                dotRows = new ArrayList<>();
        qualityRows.add("method,mean,min,max,score_unit,runs");
        effortRows.add("method,mean_evaluations,mean_runtime_ms");
        dotRows.add("method,seed,score,score_unit");
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary summary = ExperimentRunner.summarize(study, method, "main");
            qualityRows.add(
                    CsvFiles.row(
                            method,
                            summary.mean(),
                            summary.best(),
                            summary.worst(),
                            ResultComparison.unit(study.evaluator()),
                            summary.runs()));
            effortRows.add(CsvFiles.row(method, summary.evaluations(), summary.runtime()));
            for (ExperimentRunner.Trial trial :
                    ExperimentRunner.select(study.trials(), method, "main"))
                if (trial.result().evaluation() != null)
                    dotRows.add(
                            CsvFiles.row(
                                    method,
                                    method == SearchMethod.EXACT ? "N/A" : trial.seed(),
                                    trial.result().evaluation().score(),
                                    ResultComparison.unit(study.evaluator())));
        }
        Files.write(directory.resolve("quality.csv"), qualityRows, StandardCharsets.UTF_8);
        Files.write(directory.resolve("evaluations.csv"), effortRows, StandardCharsets.UTF_8);
        Files.write(directory.resolve("dots.csv"), dotRows, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        rows.add("services,assignments,log10_assignments");
        long count = 1;
        for (int n = 1; n <= 12; n++) {
            count *= 4;
            rows.add(CsvFiles.row(n, count, Math.log10(count)));
        }
        Files.write(directory.resolve("growth.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add("method,evaluations,best_score,score_unit,protocol,seed");
        StudyCharts chart = new StudyCharts(Kind.CONVERGENCE);
        chart.set(study);
        for (ExperimentRunner.Trial trial : chart.representatives())
            for (SearchResult.Point point : trial.result().trace())
                rows.add(
                        CsvFiles.row(
                                trial.method(),
                                point.evaluations(),
                                point.score(),
                                ResultComparison.unit(study.evaluator()),
                                "main",
                                trial.method() == SearchMethod.EXACT ? "N/A" : trial.seed()));
        Files.write(directory.resolve("convergence.csv"), rows, StandardCharsets.UTF_8);
        rows = new ArrayList<>();
        rows.add("set,count,unit,note");
        long total = study.evaluator().data.searchSpace();
        rows.add(CsvFiles.row("total", total, "assignments", "unconstrained search space"));
        rows.add(
                CsvFiles.row(
                        "feasible",
                        study.exact().completeEnumeration(total)
                                ? study.exact().feasible()
                                : "unknown",
                        "assignments",
                        "counted by complete enumeration"));
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS)
            rows.add(
                    CsvFiles.row(
                            method,
                            ExperimentRunner.summarize(study, method, "main").unique(),
                            "mean unique assignments",
                            "main protocol"));
        Files.write(directory.resolve("space.csv"), rows, StandardCharsets.UTF_8);
    }

    public static void exportRun(Path directory, Evaluator e, SearchResult result)
            throws Exception {
        Visuals.Placement placement = new Visuals.Placement();
        placement.set(e.data, result.best(), result.evaluation());
        png(directory.resolve("placement.png"), placement, 1400, 270);
        Visuals.Convergence convergence = new Visuals.Convergence();
        convergence.setBound(e.optimisticBound());
        convergence.set(result.trace(), Dashboard.scoreLabel(e));
        png(directory.resolve("convergence.png"), convergence, 1400, 650);
    }
}
