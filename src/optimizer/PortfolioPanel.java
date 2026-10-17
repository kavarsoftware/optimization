package optimizer;

import static optimizer.Visuals.*;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Results dashboard for a real experiment, including pulse highlights for paired seed selection.
 */
public final class PortfolioPanel extends JPanel {
    final JButton run = Dashboard.button("Compare 10 algorithms", TEAL);
    final JSpinner firstSeed = Dashboard.spinner(2026L, Long.MIN_VALUE, Long.MAX_VALUE, 1L),
            seeds = Dashboard.spinner(20, 1, 100, 1),
            evaluations = Dashboard.spinner(20000, 100, 1000000, 1000);
    final JLabel reference = new JLabel("--"),
            leader = new JLabel("--"),
            count = new JLabel("0 / 200"),
            location =
                    new JLabel("Inputs, trials, rankings and five PNG charts save automatically.");
    final JProgressBar progress = new JProgressBar(0, 200);
    final JTabbedPane views = new JTabbedPane();
    final List<PortfolioCharts> charts = new ArrayList<>();
    final PortfolioCharts paired = new PortfolioCharts(PortfolioCharts.Kind.PAIRED);
    final JComboBox<SearchMethod> selected =
            new JComboBox<>(AlgorithmComparison.METHODS.toArray(SearchMethod[]::new));
    final JComboBox<Long> selectedSeed = new JComboBox<>();
    final JTextArea log = new JTextArea();
    final DefaultTableModel statistics =
            Dashboard.model(
                    false,
                    "Rank",
                    "Algorithm",
                    "Feasible / planned",
                    "Mean",
                    "Median",
                    "SD",
                    "Best",
                    "Worst",
                    "Gap %",
                    "Exact hit %",
                    "Mean ms",
                    "Mean unique");
    ExperimentRunner.Study study;
    final javax.swing.Timer pulseTimer;

    public PortfolioPanel(Runnable onStart) {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(BACK);
        JPanel top = Dashboard.panel(new BorderLayout(5, 6));
        top.setBorder(null);
        JPanel controls = Dashboard.flow();
        controls.add(run);
        controls.add(new JLabel("First seed"));
        controls.add(firstSeed);
        controls.add(new JLabel("Seeds"));
        controls.add(seeds);
        controls.add(new JLabel("Budget"));
        controls.add(evaluations);
        progress.setStringPainted(true);
        progress.setString("Ready / same data, same budget");
        progress.setPreferredSize(new Dimension(280, 25));
        controls.add(progress);
        top.add(controls, BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(1, 3, 8, 0));
        cards.setOpaque(false);
        cards.add(card("EXACT REFERENCE / score", reference));
        cards.add(card("MEAN QUALITY LEADER / current results", leader));
        cards.add(card("RECORDED RUNS / ten algorithms", count));
        top.add(cards, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        for (PortfolioCharts.Kind kind :
                new PortfolioCharts.Kind[] {
                    PortfolioCharts.Kind.RANKING,
                    PortfolioCharts.Kind.SPREAD,
                    PortfolioCharts.Kind.SPEED,
                    PortfolioCharts.Kind.CONVERGENCE
                }) {
            PortfolioCharts chart = new PortfolioCharts(kind);
            charts.add(chart);
            views.addTab(
                    switch (kind) {
                        case RANKING -> "Ranking";
                        case SPREAD -> "Seed spread";
                        case SPEED -> "Speed";
                        default -> "Search curves";
                    },
                    chart);
        }
        JPanel pair = new JPanel(new BorderLayout(4, 4));
        JPanel pick = Dashboard.flow();
        pick.add(new JLabel("Algorithm"));
        pick.add(selected);
        pick.add(new JLabel("Seed"));
        pick.add(selectedSeed);
        pair.add(pick, BorderLayout.NORTH);
        pair.add(paired, BorderLayout.CENTER);
        views.addTab("Paired vs SRS", pair);
        views.addTab("Statistics", Dashboard.scrollTable(Dashboard.wideTable(statistics)));
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 12));
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setBorder(new EmptyBorder(10, 12, 10, 12));
        views.addTab("Protocol / log", new JScrollPane(log));
        JTextArea methods = new JTextArea();
        methods.setEditable(false);
        methods.setLineWrap(true);
        methods.setWrapStyleWord(true);
        methods.setFont(new Font("SansSerif", Font.PLAIN, 13));
        methods.setBorder(new EmptyBorder(14, 18, 14, 18));
        StringBuilder help =
                new StringBuilder(
                        "Ten algorithms on ONE model. Twenty seeds measure consistency. Exact is a"
                                + " separate reference.\n\n");
        for (SearchMethod m : AlgorithmComparison.METHODS)
            help.append(m).append("\n").append(AlgorithmComparison.description(m)).append("\n\n");
        help.append(
                        "Ranking: feasible-run coverage first, then mean feasible score. A single"
                            + " lucky best does not decide the winner. Means exclude failed runs;"
                            + " incomplete studies are provisional. Matching Exact measures success"
                            + " on this instance, not a universal algorithm guarantee.\n\n")
                .append(
                        "All complete trials, including invalid and repeated ones, count. Partial"
                            + " construction work also takes time and is recorded separately. Same"
                            + " evaluation budget does not mean same runtime. Target Search remains"
                            + " in the original study because it is Random with a threshold stop."
                            + " No method receives the Exact solution. Full parameters and raw"
                            + " records are saved in COMPARISON.md and per-run CSVs.");
        methods.setText(help.toString());
        methods.setCaretPosition(0);
        views.addTab("Methods explained", new JScrollPane(methods));
        add(views, BorderLayout.CENTER);
        location.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(location, BorderLayout.SOUTH);
        selected.setSelectedItem(SearchMethod.ANNEALING);
        pulseTimer =
                new javax.swing.Timer(
                        30,
                        e -> {
                            paired.pulse = Math.max(0, paired.pulse - .035);
                            paired.repaint();
                            if (paired.pulse == 0) ((javax.swing.Timer) e.getSource()).stop();
                        });
        selected.addActionListener(e -> selectPair());
        selectedSeed.addActionListener(e -> selectPair());
        run.addActionListener(e -> onStart.run());
    }

    private JPanel card(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(2, 4));
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(7, 12, 7, 12));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(MUTED);
        value.setFont(new Font("SansSerif", Font.BOLD, 17));
        value.setForeground(INK);
        p.add(label, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    void setBusy(boolean busy) {
        run.setEnabled(!busy);
        seeds.setEnabled(!busy);
        firstSeed.setEnabled(!busy);
        evaluations.setEnabled(!busy);
    }

    void begin() {
        study = null;
        statistics.setRowCount(0);
        log.setText("Equal-budget experiment. Target and batch early stopping disabled.\n");
        for (var chart : charts) chart.set(null);
        paired.set(null);
        reference.setText("Computing...");
        leader.setText("Waiting for measured results");
        progress.setMaximum(((Number) seeds.getValue()).intValue() * 10);
        progress.setValue(0);
        progress.setString("Computing exact reference...");
        count.setText("0 / " + progress.getMaximum());
        location.setText("Comparison running. Use Stop in the main controls to cancel.");
    }

    void message(String message) {
        log.append(message + "\n");
        log.setCaretPosition(log.getDocument().getLength());
        progress.setString(message);
    }

    void show(ExperimentRunner.Study value, Path folder) {
        study = value;
        for (var chart : charts) chart.set(value);
        paired.set(value);
        var rows = AlgorithmComparison.ranking(value);
        statistics.setRowCount(0);
        int rank = 0;
        for (var s : rows)
            statistics.addRow(
                    new Object[] {
                        ++rank,
                        s.method(),
                        s.feasibleRuns() + " / " + value.seeds(),
                        s.mean(),
                        s.median(),
                        s.sd(),
                        s.best(),
                        s.worst(),
                        ExperimentRunner.gap(
                                s.mean(), value.exact(), value.evaluator().data.searchSpace()),
                        s.optimumRate(),
                        s.runtime(),
                        s.unique()
                    });
        reference.setText(
                value.exact().evaluation() == null
                        ? "No proven feasible optimum"
                        : String.format(Locale.ROOT, "%.9f", value.exact().evaluation().score())
                                + (value.exact().provenOptimal(value.evaluator().data.searchSpace())
                                        ? " / proven"
                                        : " / incomplete"));
        var best = rows.get(0);
        leader.setText(
                best.feasibleRuns() == 0
                        ? "No feasible trial yet"
                        : best.method().shortName()
                                + String.format(Locale.ROOT, " / %.6f", best.mean()));
        int total = value.seeds() * 10;
        progress.setMaximum(total);
        progress.setValue(value.trials().size());
        count.setText(
                value.trials().size()
                        + " / "
                        + total
                        + (AlgorithmComparison.complete(value) ? " / complete" : " / partial"));
        progress.setString(
                AlgorithmComparison.complete(value)
                        ? "Complete / 10 algorithms × " + value.seeds() + " seeds"
                        : "Partial / " + value.trials().size() + " runs recorded");
        if (!value.trials().isEmpty()) {
            long first =
                    value.trials().stream()
                            .mapToLong(ExperimentRunner.Trial::seed)
                            .min()
                            .orElse(2026);
            if (selectedSeed.getItemCount() != value.seeds()
                    || !Objects.equals(selectedSeed.getItemAt(0), first)) {
                selectedSeed.removeAllItems();
                for (int i = 0; i < value.seeds(); i++) selectedSeed.addItem(first + i);
            }
        }
        if (folder != null) location.setText("Saved: " + folder.toAbsolutePath());
        selectPair();
    }

    private void selectPair() {
        paired.selected = (SearchMethod) selected.getSelectedItem();
        if (selectedSeed.getSelectedItem() instanceof Long s) paired.selectedSeed = s;
        paired.pulse = 1;
        paired.repaint();
        pulseTimer.restart();
    }
}
