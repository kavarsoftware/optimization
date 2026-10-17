package optimizer;

import static optimizer.Visuals.*;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** All mutable Swing state stays on the event dispatch thread. Search runs in SwingWorkers. */
public final class Dashboard extends JPanel {
    final JComboBox<SearchMethod> method = new JComboBox<>(SearchMethod.values());
    final JComboBox<Evaluator.Objective> objective =
            new JComboBox<>(
                    new Evaluator.Objective[] {
                        Evaluator.Objective.WEIGHTED,
                        Evaluator.Objective.LATENCY,
                        Evaluator.Objective.COST,
                        Evaluator.Objective.BALANCED
                    });
    final JSpinner seed = spinner(2026L, Long.MIN_VALUE, Long.MAX_VALUE, 1L),
            budget = spinner(1_000_000, 1, 1_000_000, 10_000),
            batch = spinner(10_000, 1, 1_000_000, 1_000),
            patience = spinner(1, 0, 100, 1),
            minimum = spinner(2, 2, 2, 1),
            alpha = spinner(.75, 0., 1., .05),
            runs = spinner(20, 2, 50, 1),
            experimentBudget = spinner(100_000, 1000, 1_000_000, 10_000),
            target = spinner(18.0, 0., 10000., .5);
    final JButton run = button("Run optimizer", BLUE),
            stop = button("Stop", INK),
            export = button("Export final run", INK),
            compare = button("Run seeded study", BLUE),
            stopStudy = button("Stop study", INK),
            sweep = button("Run trade-off sweep", TEAL),
            load = button("Load CSV folder", INK),
            reset = button("Restore supplied data", INK),
            saveData = button("Save input CSVs", INK),
            exportTrials = button("Export experiments", INK);
    final JLabel delay = new JLabel("--"),
            checked = new JLabel("0"),
            unique = new JLabel("0.000%"),
            feasible = new JLabel("0"),
            elapsed = new JLabel("0 ms");
    final JLabel status = new JLabel("Ready. Select a method and run a reproducible search."),
            space = new JLabel();
    final JProgressBar progress = new JProgressBar(0, 100);
    final Placement placement = new Placement();
    final Convergence chart = new Convergence();
    final TradePlot tradePlot = new TradePlot();
    final ReplayPanel replay = new ReplayPanel();
    final JTabbedPane resultTabs = new JTabbedPane(), tabs = new JTabbedPane();
    final JLabel optimumBadge = new JLabel("Global optimum found? NO / run Exact");
    final JTextArea runRecord = new JTextArea();
    final DefaultTableModel
            qualityModel =
                    model(
                            false,
                            "Method",
                            "Best score",
                            "Gap to Exact (%)",
                            "Mean time (ms)",
                            "Mean evaluations",
                            "Confidence"),
            statisticsModel =
                    model(
                            false,
                            "Method",
                            "Runs",
                            "Best",
                            "Mean",
                            "Median",
                            "Worst",
                            "Sample SD",
                            "Target success (%)",
                            "Optimum hits (%)",
                            "Mean evaluations",
                            "Mean unique (%)"),
            fairModel =
                    model(
                            false,
                            "Protocol",
                            "Method",
                            "Runs",
                            "Mean score",
                            "Worst score",
                            "Mean evaluations",
                            "Mean time (ms)");
    final List<StudyCharts> studyCharts = new ArrayList<>();
    ExperimentRunner.Study study;
    Evaluator exactModel;
    SearchResult exactResult;
    Path lastSaved, studySaved, sweepSaved;
    final DefaultTableModel
            serverInput =
                    model(
                            true,
                            "Server",
                            "CPU cores",
                            "RAM GB",
                            "Storage GB",
                            "Latency ms",
                            "Demo cost / CPU"),
            serviceInput =
                    model(
                            true,
                            "Service",
                            "CPU cores",
                            "RAM GB",
                            "Storage GB",
                            "Service latency ms"),
            assignment =
                    model(
                            false,
                            "Service",
                            "Assigned server",
                            "CPU cores",
                            "RAM GB",
                            "Storage GB",
                            "Service ms",
                            "Server ms",
                            "Score contribution"),
            trialModel =
                    model(
                            false,
                            "Protocol",
                            "Seed",
                            "Method",
                            "Score",
                            "Unit",
                            "Evaluations",
                            "Unique",
                            "Time ms",
                            "Stop reason"),
            tradeModel =
                    model(
                            false,
                            "Delay priority alpha",
                            "Delay ms",
                            "Demo cost",
                            "Sampled nondominated?");
    final JTable serverTable = table(serverInput), serviceTable = table(serviceInput);
    final JTextArea explanation = new JTextArea(), experimentSummary = new JTextArea();
    ProblemData data = ProblemData.supplied();
    Evaluator lastEvaluator;
    SearchResult lastResult;
    List<ExperimentRunner.Trial> trials = List.of();
    Evaluator completedEvaluator;
    SearchResult completedResult;
    List<Trade> trades = new ArrayList<>();
    Evaluator experimentEvaluator;
    final AtomicBoolean cancel = new AtomicBoolean();
    boolean busy;

    public Dashboard() {
        super(new BorderLayout(0, 10));
        setBackground(BACK);
        setBorder(new EmptyBorder(12, 20, 12, 20));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel title = new JLabel("Service Placement Lab");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(INK);
        String displayScale = System.getProperty("sun.java2d.uiScale", "");
        JLabel subtitle =
                new JLabel(
                        "Optimization Methods  /  4 servers, 12 services  /  Java 17"
                                + (displayScale.equals("2") ? "  /  Display 200%" : ""));
        subtitle.setForeground(MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);
        heading.add(titles, BorderLayout.WEST);
        JLabel course = new JLabel("DR. GODAR  /  WEEKS 0 + 1");
        course.setForeground(TEAL);
        course.setFont(new Font("SansSerif", Font.BOLD, 12));
        JPanel badgePanel = new JPanel(new GridLayout(2, 1, 0, 4));
        badgePanel.setOpaque(false);
        badgePanel.add(course);
        badgePanel.add(optimumBadge);
        badgePanel.setPreferredSize(new Dimension(300, 56));
        optimumBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        optimumBadge.setForeground(new Color(156, 99, 20));
        heading.add(badgePanel, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Optimizer", optimizerTab());
        tabs.addTab("Model and data", dataTab());
        tabs.addTab("Experiments", experimentsTab());
        tabs.addTab("Lecture guide", guideTab());
        add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        status.setForeground(INK);
        footer.add(status, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(210, 20));
        progress.setStringPainted(true);
        progress.setString("Ready");
        footer.add(progress, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        loadData(data);
        stop.setEnabled(false);
        stopStudy.setEnabled(false);
        export.setEnabled(false);
        exportTrials.setEnabled(false);
        alpha.setToolTipText("Used only by the normalized delay / cost compromise objective");
        patience.setToolTipText(
                "Complete batches without global improvement. Zero disables early stopping.");
        minimum.setToolTipText(
                "Mandatory rule: at least two services on each server. This is not a reliability"
                        + " guarantee.");
        checked.setToolTipText("All evaluator calls, including repeated and infeasible candidates");
        replay.setListener(this::showReplayEvent);
        method.addActionListener(e -> applyMethodSettings());
        objective.addActionListener(
                e -> {
                    if (lastEvaluator != null)
                        status.setText(
                                "Displayed result uses "
                                        + lastEvaluator.objective
                                        + ". Run again to apply the selected objective.");
                });
        run.addActionListener(e -> start());
        stop.addActionListener(e -> cancel.set(true));
        export.addActionListener(e -> exportRun());
        compare.addActionListener(e -> compare());
        stopStudy.addActionListener(e -> cancel.set(true));
        sweep.addActionListener(e -> sweep());
        reset.addActionListener(
                e -> {
                    data = ProblemData.supplied();
                    loadData(data);
                    status.setText("Supplied inputs restored. Run again to apply them.");
                });
        load.addActionListener(e -> chooseLoad());
        saveData.addActionListener(e -> saveInputs());
        exportTrials.addActionListener(e -> exportExperiments());
    }

    private JPanel optimizerTab() {
        JPanel tab = tabPanel();
        JPanel controls = panel(new GridLayout(2, 1, 0, 6));
        controls.setBorder(null);
        JPanel first = flow();
        first.add(new JLabel("Method"));
        first.add(method);
        first.add(new JLabel("Objective"));
        first.add(objective);
        first.add(run);
        first.add(stop);
        first.add(export);
        JPanel second = flow();
        addField(second, "Seed", seed);
        addField(second, "Max evaluations", budget);
        addField(second, "Batch", batch);
        addField(second, "Patience", patience);
        addField(second, "Min / server", minimum);
        addField(second, "Delay priority", alpha);
        controls.add(first);
        controls.add(second);
        JPanel top = panel(new BorderLayout(0, 10));
        top.setBorder(null);
        top.add(controls, BorderLayout.NORTH);
        JPanel metrics = panel(new GridLayout(1, 5, 12, 0));
        metrics.add(metric("BEST LATENCY (ms)", delay));
        metrics.add(metric("EVALUATIONS", checked));
        metrics.add(metric("SEARCH SPACE VISITED", unique));
        metrics.add(metric("FEASIBLE EVALUATIONS", feasible));
        metrics.add(metric("SEARCH TIME AT STEP", elapsed));
        top.add(metrics, BorderLayout.CENTER);
        tab.add(top, BorderLayout.NORTH);
        JPanel center = panel(new BorderLayout(0, 10));
        center.setBorder(null);
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setFont(new Font("SansSerif", Font.PLAIN, 13));
        explanation.setBorder(new EmptyBorder(16, 18, 16, 18));
        explanation.setForeground(INK);
        explanation.setText(
                "Every candidate assigns all services exactly once.\n\n"
                    + "Mandatory rules: CPU, RAM and storage capacities, and the selected minimum"
                    + " service count.\n\n"
                    + "Run a search to inspect the result and stopping evidence.");
        JPanel evidence = panel(new GridLayout(1, 2, 12, 0));
        evidence.setBorder(null);
        evidence.add(chart);
        evidence.add(new JScrollPane(explanation));
        JPanel resources = panel(new BorderLayout(0, 10));
        resources.setBorder(null);
        resources.add(placement, BorderLayout.NORTH);
        resources.add(evidence, BorderLayout.CENTER);
        resultTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        resultTabs.addTab("Animated search", replay);
        resultTabs.addTab("Resources and history", resources);
        resultTabs.addTab("Final service assignments", scrollTable(table(assignment)));
        runRecord.setEditable(false);
        runRecord.setFont(new Font("Monospaced", Font.PLAIN, 13));
        runRecord.setBorder(new EmptyBorder(14, 18, 14, 18));
        runRecord.setText(
                "Each completed or stopped run is saved automatically under results/autosaved.\n"
                        + "Run a method to inspect its full experiment record here.");
        resultTabs.addTab("Run record", new JScrollPane(runRecord));
        center.add(resultTabs, BorderLayout.CENTER);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel dataTab() {
        JPanel tab = tabPanel();
        JPanel top = panel(new GridLayout(3, 1));
        JPanel buttons = flow();
        buttons.add(load);
        buttons.add(saveData);
        buttons.add(reset);
        top.add(buttons);
        top.add(
                new JLabel(
                        "Edit numeric cells, then run. CPU = cores, RAM/storage = GB, latency = ms."
                                + " Changes apply on the next run."));
        top.add(
                new JLabel(
                        "Cost values are illustrative inputs only. The default objective uses your"
                                + " confirmed service latency interpretation."));
        tab.add(top, BorderLayout.NORTH);
        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        section("Known server data", scrollTable(serverTable)),
                        section("Known service data", scrollTable(serviceTable)));
        split.setResizeWeight(.38);
        split.setBorder(null);
        tab.add(split, BorderLayout.CENTER);
        space.setForeground(TEAL);
        tab.add(space, BorderLayout.SOUTH);
        return tab;
    }

    private JPanel experimentsTab() {
        JPanel tab = tabPanel();
        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 5));
        controls.setOpaque(false);
        JPanel settings = flow();
        addField(settings, "Paired seeds", runs);
        addField(settings, "Equal budget", experimentBudget);
        addField(settings, "Target score", target);
        settings.add(compare);
        settings.add(stopStudy);
        JPanel actions = flow();
        actions.add(sweep);
        actions.add(exportTrials);
        actions.add(
                new JLabel(
                        "Main budgets: Random 100k / Target <= 500k / SRS batch stop / Exact"
                                + " once"));
        controls.add(settings);
        controls.add(actions);
        tab.add(controls, BorderLayout.NORTH);
        JTabbedPane views = new JTabbedPane();
        views.setFont(new Font("SansSerif", Font.BOLD, 12));
        JPanel quality = panel(new BorderLayout(8, 8));
        JTable qualityTable = table(qualityModel);
        int[] widths = {230, 120, 110, 125, 150, 360};
        for (int i = 0; i < widths.length; i++)
            qualityTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        quality.add(scrollTable(qualityTable), BorderLayout.CENTER);
        experimentSummary.setEditable(false);
        experimentSummary.setRows(6);
        experimentSummary.setFont(new Font("Monospaced", Font.PLAIN, 12));
        experimentSummary.setText(
                "Exact is deterministic: enumerate once and reuse its proof for every seed.\n"
                    + "Random, Target and SRS use paired seeds (default 2026..2045).\n"
                    + "Equal-budget and SRS-stop-budget comparisons are reported separately.\n"
                    + "All methods share the selected evaluator, inputs and capacity/count rules.");
        quality.add(new JScrollPane(experimentSummary), BorderLayout.SOUTH);
        views.addTab("Quality / time / confidence", quality);
        views.addTab("20-seed statistics", scrollTable(wideTable(statisticsModel)));
        JTabbedPane charts = new JTabbedPane();
        for (StudyCharts.Kind kind : StudyCharts.Kind.values()) {
            StudyCharts chart = new StudyCharts(kind);
            studyCharts.add(chart);
            charts.addTab(kind.toString(), chart);
        }
        views.addTab("Charts", charts);
        views.addTab("Fair budgets", scrollTable(table(fairModel)));
        views.addTab("Run records", scrollTable(wideTable(trialModel)));
        JPanel trade = panel(new BorderLayout(8, 8));
        trade.add(
                new JLabel(
                        "Illustrative prices / weighted latency in ms / 11 priorities / green ="
                                + " nondominated among sampled results"),
                BorderLayout.NORTH);
        trade.add(tradePlot, BorderLayout.CENTER);
        JScrollPane table = scrollTable(table(tradeModel));
        table.setPreferredSize(new Dimension(100, 170));
        trade.add(table, BorderLayout.SOUTH);
        views.addTab("Cost / delay", trade);
        tab.add(views, BorderLayout.CENTER);
        return tab;
    }

    private JComponent guideTab() {
        JEditorPane guide = new JEditorPane("text/html", LectureGuide.html());
        guide.setEditable(false);
        guide.setCaretPosition(0);
        guide.addHyperlinkListener(
                event -> {
                    if (event.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
                            && event.getDescription().startsWith("#"))
                        guide.scrollToReference(event.getDescription().substring(1));
                });
        return new JScrollPane(guide);
    }

    void loadDataForPreview(ProblemData d) {
        loadData(d);
    }

    private void loadData(ProblemData d) {
        serverInput.setRowCount(0);
        serviceInput.setRowCount(0);
        for (var s : d.servers())
            serverInput.addRow(
                    new Object[] {
                        s.name(), s.cpu(), s.ram(), s.storage(), s.latencyMs(), s.costPerCpu()
                    });
        for (var s : d.services())
            serviceInput.addRow(
                    new Object[] {s.name(), s.cpu(), s.ram(), s.storage(), s.latencyValue()});
        space.setText(
                String.format(
                        Locale.ROOT,
                        "Search space: %d^%d = %,d complete assignments. Feasible candidates form a"
                                + " subset.",
                        d.servers().size(),
                        d.services().size(),
                        d.searchSpace()));
    }

    private ProblemData readData() {
        if (serverTable.isEditing() && !serverTable.getCellEditor().stopCellEditing())
            throw new IllegalArgumentException("Finish editing the server cell");
        if (serviceTable.isEditing() && !serviceTable.getCellEditor().stopCellEditing())
            throw new IllegalArgumentException("Finish editing the service cell");
        List<ProblemData.Server> servers = new ArrayList<>();
        List<ProblemData.Service> services = new ArrayList<>();
        for (int r = 0; r < serverInput.getRowCount(); r++)
            servers.add(
                    new ProblemData.Server(
                            serverInput.getValueAt(r, 0).toString(),
                            value(serverInput, r, 1),
                            value(serverInput, r, 2),
                            value(serverInput, r, 3),
                            value(serverInput, r, 4),
                            value(serverInput, r, 5)));
        for (int r = 0; r < serviceInput.getRowCount(); r++)
            services.add(
                    new ProblemData.Service(
                            serviceInput.getValueAt(r, 0).toString(),
                            value(serviceInput, r, 1),
                            value(serviceInput, r, 2),
                            value(serviceInput, r, 3),
                            value(serviceInput, r, 4)));
        if (servers.size() != 4 || services.size() != 12)
            throw new IllegalArgumentException(
                    "This dashboard expects exactly 4 servers and 12 services");
        return new ProblemData("godar-4x12-edited", servers, services);
    }

    private Evaluator evaluator() {
        data = readData();
        return new Evaluator(
                data,
                ((Number) minimum.getValue()).intValue(),
                (Evaluator.Objective) objective.getSelectedItem(),
                ((Number) alpha.getValue()).doubleValue());
    }

    private SearchConfig config() {
        return new SearchConfig(
                ((Number) seed.getValue()).longValue(),
                ((Number) budget.getValue()).intValue(),
                ((Number) batch.getValue()).intValue(),
                ((Number) patience.getValue()).intValue(),
                .35,
                ((Number) target.getValue()).doubleValue());
    }

    private void start() {
        try {
            Evaluator evaluator = evaluator();
            SearchConfig settings = config();
            SearchMethod selected = (SearchMethod) method.getSelectedItem();
            cancel.set(false);
            setBusy(true);
            progress.setValue(0);
            status.setText(
                    selected == SearchMethod.EXACT
                            ? "Exact Search: enumerating every assignment..."
                            : "Searching with seed " + settings.seed() + "...");
            Evaluator previousModel = completedEvaluator;
            SearchResult previousRun = completedResult;
            placement.set(data, null, null);
            chart.set(List.of(), scoreLabel(evaluator));
            assignment.setRowCount(0);
            delay.setText("--");
            checked.setText("0");
            unique.setText("0.000%");
            feasible.setText("0");
            elapsed.setText("0 ms");
            resultTabs.setSelectedIndex(0);
            replay.clear(data);
            optimumBadge.setText("Global optimum found? NO / searching");
            List<SearchEvent> recording = new ArrayList<>();
            new SwingWorker<SearchResult, Optimizer.Progress>() {
                protected SearchResult doInBackground() throws Exception {
                    SearchResult result =
                            new Optimizer()
                                    .run(
                                            evaluator,
                                            selected,
                                            settings,
                                            this::publish,
                                            cancel::get,
                                            recording::add);
                    lastSaved =
                            Path.of(
                                    "results",
                                    "autosaved",
                                    "run-" + System.currentTimeMillis() + "-" + selected.name());
                    CsvFiles.export(lastSaved, evaluator, result);
                    CsvFiles.writeEvents(lastSaved, recording);
                    StudyCharts.exportRun(lastSaved, evaluator, result);
                    return result;
                }

                protected void process(List<Optimizer.Progress> updates) {
                    var p = updates.get(updates.size() - 1);
                    showProgress(
                            evaluator,
                            p,
                            selected == SearchMethod.EXACT
                                    ? (int) evaluator.data.searchSpace()
                                    : settings.budget());
                }

                protected void done() {
                    try {
                        lastResult = get();
                        lastEvaluator = evaluator;
                        if (lastResult.completeEnumeration(evaluator.data.searchSpace())) {
                            exactModel = evaluator;
                            exactResult = lastResult;
                        }
                        displayResult(evaluator, lastResult);
                        runRecord.setText(
                                "Autosaved: "
                                        + lastSaved.toAbsolutePath()
                                        + "\n\n"
                                        + String.join(
                                                "\n", CsvFiles.metadata(evaluator, lastResult)));
                        runRecord.setCaretPosition(0);
                        replay.setPreviousRun(previousModel, previousRun);
                        replay.load(evaluator, lastResult, recording, isShowing());
                        if (!lastResult.stopReason().contains("Cancelled")) {
                            completedEvaluator = evaluator;
                            completedResult = lastResult;
                        }
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void showProgress(Evaluator e, Optimizer.Progress p, int max) {
        delay.setText(p.evaluation() == null ? "--" : format(p.evaluation().delayMs()) + " ms");
        checked.setText(String.format(Locale.ROOT, "%,d", p.evaluations()));
        unique.setText(
                String.format(Locale.ROOT, "%.3f%%", 100. * p.unique() / e.data.searchSpace()));
        feasible.setText(String.format(Locale.ROOT, "%,d", p.feasible()));
        elapsed.setText(p.milliseconds() + " ms");
        placement.set(e.data, p.best(), p.evaluation());
        chart.setBound(e.optimisticBound());
        chart.set(p.trace(), scoreLabel(e));
        progress.setValue((int) (100L * p.evaluations() / max));
        progress.setString(String.format(Locale.ROOT, "%,d / %,d", p.evaluations(), max));
    }

    public void displayResult(Evaluator e, SearchResult r) {
        showProgress(
                e,
                new Optimizer.Progress(
                        r.evaluations(),
                        r.feasible(),
                        r.unique(),
                        r.restarts(),
                        r.best(),
                        r.evaluation(),
                        r.milliseconds(),
                        "Finished",
                        r.trace()),
                r.config().budget());
        assignment.setColumnIdentifiers(
                new String[] {
                    "Service",
                    "Server",
                    "CPU cores",
                    "RAM GB",
                    "Storage GB",
                    "Service ms",
                    "Server ms",
                    "Contribution (" + ResultComparison.unit(e) + ")"
                });
        assignment.setRowCount(0);
        String text = "Method: " + r.method() + "\nStop: " + r.stopReason() + "\n";
        if (r.best() != null) {
            for (int i = 0; i < e.data.services().size(); i++) {
                ProblemData.Service service = e.data.services().get(i);
                int hostIndex = r.best().server(i);
                ProblemData.Server host = e.data.servers().get(hostIndex);
                assignment.addRow(
                        new Object[] {
                            service.name(),
                            host.name(),
                            service.cpu(),
                            service.ram(),
                            service.storage(),
                            service.latencyValue(),
                            host.latencyMs(),
                            format(e.contribution(i, hostIndex))
                        });
            }
            text +=
                    "\nAll mandatory constraints satisfied.\nScore: "
                            + format(r.evaluation().score())
                            + "\nDelay: "
                            + format(r.evaluation().delayMs())
                            + " ms. Demo cost: "
                            + format(r.evaluation().cost())
                            + "\nOptimistic bound: "
                            + format(e.optimisticBound())
                            + " (ignores capacity/count rules).\n";
        } else
            text +=
                    "\n"
                            + "No feasible solution found. A search failure alone does not prove"
                            + " infeasibility.\n";
        text +=
                "\n"
                        + r.unique()
                        + " distinct candidates, "
                        + (r.evaluations() - r.unique())
                        + " repeated evaluations.\nConstruction attempts: "
                        + r.constructions()
                        + ", abandoned: "
                        + r.failedConstructions()
                        + ".\nResult quality is checked against Exact only when the model matches.";
        refreshProof(e, r);
        text +=
                "\nObjective: "
                        + e.objective
                        + ". Score unit: "
                        + ResultComparison.unit(e)
                        + ".\n"
                        + optimumBadge.getText();
        explanation.setText(text);
        explanation.setCaretPosition(0);
        status.setText(
                r.stopReason()
                        + ". "
                        + (r.method() == SearchMethod.EXACT
                                ? "Deterministic enumeration."
                                : "Seed " + r.config().seed() + "."));
        progress.setString("Stopped at " + r.evaluations() + " evaluations");
    }

    private void showReplayEvent(SearchEvent event) {
        if (lastEvaluator == null || lastResult == null) return;
        refreshProof(lastEvaluator, event.bestEvaluation());
        List<SearchResult.Point> trace = new ArrayList<>();
        for (SearchResult.Point point : lastResult.trace())
            if (point.evaluations() <= event.evaluations()) trace.add(point);
        showProgress(
                lastEvaluator,
                new Optimizer.Progress(
                        event.evaluations(),
                        event.feasible(),
                        event.unique(),
                        event.restarts(),
                        event.best(),
                        event.bestEvaluation(),
                        event.milliseconds(),
                        "Replay",
                        trace),
                lastResult.config().budget());
        progress.setValue(
                replay.events().size() < 2
                        ? 100
                        : 100 * replay.frameIndex() / (replay.events().size() - 1));
        progress.setString("Replay " + (replay.frameIndex() + 1) + " / " + replay.events().size());
        status.setText(
                "Recorded search replay / calculation finished in "
                        + lastResult.milliseconds()
                        + " ms / export saves the final run");
    }

    private void applyMethodSettings() {
        SearchMethod selected = (SearchMethod) method.getSelectedItem();
        if (selected == SearchMethod.RANDOM) {
            budget.setValue(100_000);
            patience.setValue(0);
        } else if (selected == SearchMethod.TARGET) {
            budget.setValue(500_000);
            patience.setValue(0);
        } else if (selected == SearchMethod.HYBRID) {
            budget.setValue(1_000_000);
            patience.setValue(1);
        }
        refreshMethodControls();
    }

    private void refreshMethodControls() {
        SearchMethod selected = (SearchMethod) method.getSelectedItem();
        budget.setEnabled(
                !busy
                        && selected != SearchMethod.EXACT
                        && selected != SearchMethod.RANDOM
                        && selected != SearchMethod.TARGET);
        patience.setEnabled(
                !busy
                        && (selected == SearchMethod.HYBRID
                                || selected == SearchMethod.CONSTRUCTIVE));
    }

    private void refreshProof(Evaluator e, SearchResult result) {
        refreshProof(e, result.evaluation());
    }

    private void refreshProof(Evaluator e, Evaluation evaluation) {
        boolean reference =
                exactResult != null
                        && exactResult.provenOptimal(e.data.searchSpace())
                        && ResultComparison.sameModel(e, exactModel);
        boolean matches =
                reference
                        && evaluation != null
                        && exactResult.evaluation() != null
                        && Math.abs(evaluation.score() - exactResult.evaluation().score()) < 1e-9;
        optimumBadge.setText(
                "Global optimum found? " + (matches ? "YES" : reference ? "NO" : "NO / run Exact"));
        optimumBadge.setForeground(matches ? TEAL : new Color(156, 99, 20));
        optimumBadge.setToolTipText(
                matches
                        ? "This score matches the complete Exact enumeration for the same model."
                        : reference
                                ? "Gap to Exact: "
                                        + format(
                                                ExperimentRunner.gap(
                                                        evaluation == null
                                                                ? Double.NaN
                                                                : evaluation.score(),
                                                        exactResult,
                                                        e.data.searchSpace()))
                                        + "%"
                                : "No matching complete Exact reference has been run in this"
                                        + " session.");
    }

    private void compare() {
        try {
            replay.pause();
            Evaluator e = evaluator();
            int count = ((Number) runs.getValue()).intValue(),
                    equal = ((Number) experimentBudget.getValue()).intValue();
            SearchConfig settings = config();
            cancel.set(false);
            setBusy(true);
            trialModel.setRowCount(0);
            status.setText("Running exact reference and seeded experiments...");
            progress.setIndeterminate(true);
            new SwingWorker<ExperimentRunner.Study, String>() {
                protected ExperimentRunner.Study doInBackground() throws Exception {
                    ExperimentRunner.Study result =
                            ExperimentRunner.study(
                                    e,
                                    settings,
                                    count,
                                    equal,
                                    message -> publish(message),
                                    cancel::get);
                    studySaved =
                            Path.of("results", "studies", "study-" + System.currentTimeMillis());
                    ExperimentRunner.write(studySaved, result);
                    return result;
                }

                protected void process(List<String> messages) {
                    status.setText(messages.get(messages.size() - 1));
                    progress.setString("Seeded study in progress");
                }

                protected void done() {
                    try {
                        displayStudy(get());
                        status.setText(
                                "Saved "
                                        + trials.size()
                                        + " run records and all charts to "
                                        + studySaved.toAbsolutePath());
                        progress.setString("Study saved");
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        progress.setIndeterminate(false);
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
        }
    }

    void displayStudy(ExperimentRunner.Study studyValue) {
        study = studyValue;
        trials = study.trials();
        Evaluator e = study.evaluator();
        experimentEvaluator = e;
        if (study.exact().completeEnumeration(e.data.searchSpace())) {
            exactResult = study.exact();
            exactModel = e;
        }
        showSummary();
        for (StudyCharts chart : studyCharts) chart.set(study);
        for (ExperimentRunner.Trial trial : trials) {
            SearchResult result = trial.result();
            Evaluation value = result.evaluation();
            trialModel.addRow(
                    new Object[] {
                        trial.protocol(),
                        trial.method() == SearchMethod.EXACT ? "N/A" : trial.seed(),
                        trial.method(),
                        value == null ? "NA" : format(value.score()),
                        ResultComparison.unit(e),
                        result.evaluations(),
                        result.unique(),
                        result.milliseconds(),
                        result.stopReason()
                    });
        }
        for (ExperimentRunner.Trial trial : trials)
            if (trial.method() == SearchMethod.HYBRID && trial.protocol().equals("main")) {
                lastResult = trial.result();
                lastEvaluator = e;
                displayResult(e, lastResult);
                replay.load(e, lastResult, List.of(), false);
                resultTabs.setSelectedIndex(1);
                runRecord.setText(
                        "Study autosaved: "
                                + studySaved.toAbsolutePath()
                                + "\n\n"
                                + String.join("\n", CsvFiles.metadata(e, lastResult)));
                break;
            }
    }

    private void showSummary() {
        qualityModel.setRowCount(0);
        statisticsModel.setRowCount(0);
        fairModel.setRowCount(0);
        String unit = ResultComparison.unit(study.evaluator());
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary s = ExperimentRunner.summarize(study, method, "main");
            qualityModel.addRow(
                    new Object[] {
                        method,
                        format(s.best()) + " " + unit,
                        format(
                                ExperimentRunner.gap(
                                        s.best(),
                                        study.exact(),
                                        study.evaluator().data.searchSpace())),
                        format(s.runtime()),
                        String.format(Locale.ROOT, "%,.0f", s.evaluations()),
                        ExperimentRunner.confidence(study, s)
                    });
            statisticsModel.addRow(
                    new Object[] {
                        method,
                        s.runs(),
                        format(s.best()) + " " + unit,
                        format(s.mean()) + " " + unit,
                        format(s.median()) + " " + unit,
                        format(s.worst()) + " " + unit,
                        format(s.sd()) + " " + unit,
                        format(s.successRate()),
                        format(s.optimumRate()),
                        s.evaluations(),
                        format(100 * s.unique() / study.evaluator().data.searchSpace())
                    });
        }
        for (String protocol : new String[] {"equal_budget", "srs_stop_budget"})
            for (SearchMethod method :
                    new SearchMethod[] {SearchMethod.RANDOM, SearchMethod.HYBRID}) {
                String source =
                        protocol.equals("srs_stop_budget") && method == SearchMethod.HYBRID
                                ? "main"
                                : protocol;
                ExperimentRunner.Summary s = ExperimentRunner.summarize(study, method, source);
                if (s.runs() == 0) continue;
                fairModel.addRow(
                        new Object[] {
                            protocol,
                            method,
                            s.runs(),
                            format(s.mean()) + " " + unit,
                            format(s.worst()) + " " + unit,
                            s.evaluations(),
                            format(s.runtime())
                        });
            }
        String proof =
                study.exact().completeEnumeration(study.evaluator().data.searchSpace())
                        ? "Complete"
                        : "Incomplete";
        experimentSummary.setText(
                proof
                        + " Exact scan: "
                        + study.exact().evaluations()
                        + " assignments / "
                        + study.exact().feasible()
                        + " feasible.\n"
                        + "Exact runs once (no seed); other main methods use "
                        + study.seeds()
                        + " paired seeds.\nTarget success means score <= "
                        + study.exact().config().target()
                        + " "
                        + unit
                        + "; optimum hits mean matching Exact.\n"
                        + "Means and SD describe feasible results; failed runs remain in"
                        + " success-rate denominators.\n"
                        + "Fair budgets: SRS vs Random at "
                        + study.equalBudget()
                        + " evaluations, and Random at each SRS stopping count.\n"
                        + "Runtime includes construction, excludes exports/playback, and varies"
                        + " with JVM/hardware. 0 ms means less than 1 ms.");
    }

    private void sweep() {
        try {
            replay.pause();
            ProblemData d = readData();
            int min = ((Number) minimum.getValue()).intValue();
            long base = ((Number) seed.getValue()).longValue();
            cancel.set(false);
            setBusy(true);
            tradeModel.setRowCount(0);
            trades = new ArrayList<>();
            status.setText("Sampling cost / delay trade-offs...");
            sweepSaved = Path.of("results", "sweeps", "sweep-" + System.currentTimeMillis());
            new SwingWorker<List<Trade>, Trade>() {
                protected List<Trade> doInBackground() throws Exception {
                    List<Trade> all = new ArrayList<>();
                    Path folder = sweepSaved;
                    for (int i = 0; i <= 10 && !cancel.get(); i++) {
                        double priority = i / 10.;
                        Evaluator e = new Evaluator(d, min, Evaluator.Objective.BALANCED, priority);
                        SearchResult r =
                                new Optimizer()
                                        .run(
                                                e,
                                                SearchMethod.HYBRID,
                                                new SearchConfig(base, 20_000, 10_000, 0, .35),
                                                null,
                                                cancel::get);
                        CsvFiles.export(folder.resolve("priority-" + i), e, r);
                        if (r.evaluation() != null) {
                            Trade point =
                                    new Trade(
                                            priority,
                                            r.evaluation().delayMs(),
                                            r.evaluation().cost());
                            all.add(point);
                            publish(point);
                        }
                    }
                    List<String> rows = new ArrayList<>();
                    rows.add(
                            "delay_priority,weighted_latency_ms,illustrative_cost_units,nondominated_within_sample");
                    for (Trade point : all)
                        rows.add(
                                CsvFiles.row(
                                        point.alpha(),
                                        point.delay(),
                                        point.cost(),
                                        !TradePlot.dominated(point, all)));
                    java.nio.file.Files.createDirectories(folder);
                    java.nio.file.Files.write(
                            folder.resolve("sweep.csv"),
                            rows,
                            java.nio.charset.StandardCharsets.UTF_8);
                    TradePlot plot = new TradePlot();
                    plot.set(all);
                    StudyCharts.png(folder.resolve("tradeoffs.png"), plot, 1400, 800);
                    return all;
                }

                protected void process(List<Trade> items) {
                    trades.addAll(items);
                    tradePlot.set(trades);
                    progress.setValue(100 * trades.size() / 11);
                    progress.setString(trades.size() + " / 11 priorities");
                }

                protected void done() {
                    try {
                        trades = new ArrayList<>(get());
                        for (var t : trades)
                            tradeModel.addRow(
                                    new Object[] {
                                        t.alpha(),
                                        format(t.delay()),
                                        format(t.cost()),
                                        !TradePlot.dominated(t, trades)
                                    });
                        tradePlot.set(trades);
                        status.setText(
                                "Saved illustrative cost/delay sweep and PNG: "
                                        + sweepSaved.toAbsolutePath());
                        status.setToolTipText(status.getText());
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        for (JComponent c :
                new JComponent[] {
                    run,
                    method,
                    objective,
                    seed,
                    budget,
                    batch,
                    patience,
                    minimum,
                    alpha,
                    compare,
                    sweep,
                    runs,
                    experimentBudget,
                    target,
                    load,
                    reset,
                    saveData,
                    serverTable,
                    serviceTable
                }) c.setEnabled(!value);
        stop.setEnabled(value);
        stopStudy.setEnabled(value);
        if (!value) refreshMethodControls();
        export.setEnabled(!value && lastResult != null);
        exportTrials.setEnabled(!value && !trials.isEmpty());
    }

    private Path directory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    private void exportRun() {
        Path p = directory("Select a folder; export creates a new run subfolder");
        if (p == null) return;
        try {
            Path target = p.resolve("run-" + System.currentTimeMillis());
            CsvFiles.export(target, lastEvaluator, lastResult);
            CsvFiles.writeEvents(target, replay.events());
            StudyCharts.exportRun(target, lastEvaluator, lastResult);
            status.setText("Saved final run, recorded steps, inputs and convergence to " + target);
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void exportExperiments() {
        Path p = directory("Select a folder for a new experiment subfolder");
        if (p == null) return;
        try {
            Path target = p.resolve("experiment-" + System.currentTimeMillis());
            ExperimentRunner.write(target, study);
            status.setText("Experiment exported to " + target);
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void chooseLoad() {
        Path p = directory("Load a folder containing servers.csv and services.csv");
        if (p == null) return;
        try {
            var next = CsvFiles.read(p);
            if (next.servers().size() != 4 || next.services().size() != 12)
                throw new IllegalArgumentException("Dashboard requires 4 servers and 12 services");
            data = next;
            loadData(data);
            status.setText("Loaded inputs. Run to evaluate the new data.");
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void saveInputs() {
        Path p = directory("Select a folder; save creates a new inputs subfolder");
        if (p == null) return;
        try {
            CsvFiles.writeData(p.resolve("inputs-" + System.currentTimeMillis()), readData());
            status.setText("Input CSVs saved.");
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void error(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        status.setText("Error: " + cause.getMessage());
        if (!GraphicsEnvironment.isHeadless())
            JOptionPane.showMessageDialog(
                    this, cause.getMessage(), "Please check inputs", JOptionPane.ERROR_MESSAGE);
    }

    static String scoreLabel(Evaluator e) {
        return switch (e.objective) {
            case LATENCY, WEIGHTED -> "Delay (ms)";
            case COST -> "Illustrative cost";
            case BALANCED -> "Normalized compromise score";
            case ASSESSMENT -> "Objective J (dimensionless)";
        };
    }

    static double value(DefaultTableModel model, int r, int c) {
        return Double.parseDouble(String.valueOf(model.getValueAt(r, c)).strip());
    }

    static String format(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "N/A";
    }

    static JSpinner spinner(Number value, Comparable<?> min, Comparable<?> max, Number step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        s.setPreferredSize(new Dimension(value instanceof Long ? 100 : 85, 29));
        return s;
    }

    static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 0, 0, 0));
        return p;
    }

    static JPanel tabPanel() {
        JPanel p = panel(new BorderLayout(10, 12));
        p.setOpaque(true);
        p.setBackground(BACK);
        return p;
    }

    static JScrollPane scrollTable(JTable t) {
        JScrollPane scroll = new JScrollPane(t);
        scroll.setColumnHeaderView(t.getTableHeader());
        return scroll;
    }

    static JPanel flow() {
        JPanel p = panel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        p.setBorder(null);
        return p;
    }

    static void addField(JPanel p, String label, JComponent component) {
        JLabel text = new JLabel(label);
        text.setLabelFor(component);
        p.add(text);
        p.add(component);
    }

    static JButton button(String label, Color color) {
        JButton b = new JButton(label);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBorder(new EmptyBorder(9, 13, 9, 13));
        return b;
    }

    static JPanel metric(String title, JLabel value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(10, 17, 10, 17));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setForeground(INK);
        value.setFont(new Font("SansSerif", Font.BOLD, 23));
        p.add(label);
        p.add(Box.createVerticalStrut(5));
        p.add(value);
        return p;
    }

    static JComponent section(String name, JComponent content) {
        JPanel p = panel(new BorderLayout(8, 8));
        p.add(new JLabel(name), BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    static DefaultTableModel model(boolean editable, String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return editable;
            }
        };
    }

    static JTable wideTable(DefaultTableModel model) {
        JTable t = table(model);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < t.getColumnCount(); i++)
            t.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 175 : 145);
        return t;
    }

    static JTable table(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(28);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setGridColor(LINE);
        t.setSelectionBackground(new Color(218, 232, 255));
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        return t;
    }
}
