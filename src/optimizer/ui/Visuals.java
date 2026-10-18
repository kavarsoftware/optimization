package optimizer.ui;

import optimizer.brain.*;
import optimizer.experiments.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

/** Native Swing visuals. No chart library or network connection is required. */
public final class Visuals {
    /** Lay out chart components before offscreen rendering. */
    static void layout(Container container) {
        container.invalidate();
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container nested) layout(nested);
    }

    public static final Color INK = new Color(24, 32, 47),
            MUTED = new Color(116, 124, 139),
            BLUE = new Color(79, 70, 229),
            TEAL = new Color(13, 148, 136),
            BACK = new Color(246, 247, 250),
            LINE = new Color(230, 233, 240);
    public static final Color[] HOSTS = {
        BLUE, TEAL, new Color(111, 126, 154), new Color(184, 137, 76)
    };

    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public static void text(
            Graphics2D g, String value, int x, int y, int size, Color color, boolean bold) {
        g.setColor(color);
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        g.drawString(value, x, y);
    }

    public static class Placement extends JPanel {
        ProblemData data = ProblemData.assessmentDefaults();
        Candidate candidate;
        Evaluation evaluation;

        public Placement() {
            setBackground(BACK);
            setPreferredSize(new Dimension(1100, 235));
        }

        public void set(ProblemData data, Candidate candidate, Evaluation evaluation) {
            this.data = data;
            this.candidate = candidate;
            this.evaluation = evaluation;
            int maxCount = 0;
            if (evaluation != null)
                for (Evaluation.Usage usage : evaluation.usage())
                    maxCount = Math.max(maxCount, usage.count());
            int height = maxCount > 6 ? 290 : 235;
            if (getPreferredSize().height != height) {
                setPreferredSize(new Dimension(1100, height));
                revalidate();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            quality(g);
            int m = data.servers().size(), gap = 14, width = (getWidth() - gap * (m - 1)) / m;
            for (int s = 0; s < m; s++) {
                int x = s * (width + gap);
                var host = data.servers().get(s);
                Color color = HOSTS[s % HOSTS.length];
                g.setColor(Color.WHITE);
                g.fillRoundRect(x, 0, width, getHeight() - 4, 18, 18);
                g.setColor(LINE);
                g.drawRoundRect(x, 0, width - 1, getHeight() - 5, 18, 18);
                g.setColor(color);
                g.fillRoundRect(x + 16, 18, 7, 24, 5, 5);
                text(g, host.name(), x + 32, 37, 20, INK, true);
                String latency = String.format(Locale.ROOT, "%.0f ms", host.latencyMs());
                text(g, latency, x + width - 82, 36, 15, color, true);
                var u =
                        evaluation == null
                                ? new Evaluation.Usage(0, 0, 0, 0)
                                : evaluation.usage().get(s);
                text(g, u.count() + " SERVICES", x + 18, 63, 11, MUTED, true);
                bar(g, x + 18, 82, width - 36, "CPU (cores)", u.cpu(), host.cpu(), color);
                bar(g, x + 18, 116, width - 36, "RAM (GB)", u.ram(), host.ram(), color);
                bar(g, x + 18, 150, width - 36, "Storage (GB)", u.storage(), host.storage(), color);
                int chipX = x + 18, chipY = 193;
                if (candidate == null)
                    text(g, "Waiting for a feasible placement", x + 18, 207, 12, MUTED, false);
                else
                    for (int i = 0; i < candidate.size(); i++)
                        if (candidate.server(i) == s) {
                            String label = data.services().get(i).name();
                            g.setFont(new Font("SansSerif", Font.BOLD, 11));
                            int chipW = g.getFontMetrics().stringWidth(label) + 18;
                            if (chipX + chipW > x + width - 12) {
                                chipX = x + 18;
                                chipY += 27;
                            }
                            g.setColor(
                                    new Color(
                                            color.getRed(), color.getGreen(), color.getBlue(), 18));
                            g.fillRoundRect(chipX, chipY - 15, chipW, 23, 9, 9);
                            text(g, label, chipX + 9, chipY + 1, 11, color, true);
                            chipX += chipW + 5;
                        }
            }
            g.dispose();
        }

        private void bar(
                Graphics2D g,
                int x,
                int y,
                int width,
                String name,
                double used,
                double capacity,
                Color color) {
            text(g, name, x, y, 11, MUTED, true);
            String value =
                    String.format(
                            Locale.ROOT,
                            "%.0f / %.0f   %.0f%%",
                            used,
                            capacity,
                            100 * used / capacity);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            text(g, value, x + width - g.getFontMetrics().stringWidth(value), y, 11, INK, false);
            g.setColor(BACK);
            g.fillRoundRect(x, y + 8, width, 7, 7, 7);
            g.setColor(used > capacity ? Color.RED : color);
            g.fillRoundRect(x, y + 8, (int) (width * Math.min(1, used / capacity)), 7, 7, 7);
        }
    }

    public static class Convergence extends JPanel {
        List<SearchResult.Point> points = List.of();
        String scoreLabel = "Weighted latency (ms)";
        double bound = Double.NaN;

        public void setBound(double bound) {
            this.bound = bound;
            repaint();
        }

        public Convergence() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(760, 210));
        }

        public void set(List<SearchResult.Point> points, String label) {
            this.points = List.copyOf(points);
            scoreLabel = label;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            quality(g);
            int left = 72, right = getWidth() - 24, top = 40, bottom = getHeight() - 36;
            text(g, "BEST FOUND SO FAR", 18, 23, 11, MUTED, true);
            if (points.isEmpty()) {
                text(g, "Run the optimizer to see its search history", left, 110, 14, MUTED, false);
                g.dispose();
                return;
            }
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (SearchResult.Point p : points) {
                low = Math.min(low, p.score());
                high = Math.max(high, p.score());
            }
            if (Double.isFinite(bound)) low = Math.min(low, bound);
            double pad = Math.max(.01, (high - low) * .18);
            low -= pad;
            high += pad;
            int maxX = Math.max(1, points.get(points.size() - 1).evaluations());
            for (int k = 0; k <= 3; k++) {
                int y = top + (bottom - top) * k / 3;
                g.setColor(LINE);
                g.drawLine(left, y, right, y);
                text(
                        g,
                        String.format(Locale.ROOT, "%.2f", high - (high - low) * k / 3),
                        8,
                        y + 4,
                        11,
                        MUTED,
                        false);
            }
            Path2D path = new Path2D.Double();
            boolean first = true;
            double previousY = 0;
            for (var p : points) {
                double x = left + (right - left) * p.evaluations() / (double) maxX,
                        y = bottom - (bottom - top) * (p.score() - low) / (high - low);
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, previousY);
                    path.lineTo(x, y);
                }
                previousY = y;
            }
            g.setColor(BLUE);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(path);
            if (Double.isFinite(bound)) {
                int yy = bottom - (int) ((bottom - top) * (bound - low) / (high - low));
                g.setColor(MUTED);
                g.setStroke(
                        new BasicStroke(
                                1.2f,
                                BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_MITER,
                                10,
                                new float[] {6, 5},
                                0));
                g.drawLine(left, yy, right, yy);
                text(
                        g,
                        String.format(Locale.ROOT, "Optimistic bound %.3f", bound),
                        left + 8,
                        yy - 7,
                        10,
                        MUTED,
                        false);
            }
            text(g, "0", left, bottom + 20, 11, MUTED, false);
            String label = String.format(Locale.ROOT, "%,d evaluations", maxX);
            text(g, label, right - 140, bottom + 20, 11, MUTED, false);
            text(g, scoreLabel, left + 12, top - 9, 11, MUTED, false);
            g.dispose();
        }
    }


}
