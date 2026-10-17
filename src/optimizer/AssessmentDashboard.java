package optimizer;

import static optimizer.Visuals.*;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Assessment mode keeps the earlier CSV project intact and uses the PDF benchmark separately. */
public final class AssessmentDashboard extends JPanel {
    final JComboBox<SearchMethod> method =
            new JComboBox<>(
                    new SearchMethod[] {
                        SearchMethod.HYBRID,
                        SearchMethod.EXACT,
                        SearchMethod.RANDOM,
                        SearchMethod.TARGET
                    });
    final JSpinner seed = Dashboard.spinner(2026L, Long.MIN_VALUE, Long.MAX_VALUE, 1L),
            budget = Dashboard.spinner(20000, 1, 1_000_000, 1000),
            batch = Dashboard.spinner(10000, 1, 1000000, 1000),
            patience = Dashboard.spinner(1, 0, 100, 1),
            greed = Dashboard.spinner(.35, 0., 1., .05),
            target = Dashboard.spinner(.8, 0., 10., .01),
            pace = Dashboard.spinner(70, 0, 500, 10);
    final JButton start = Dashboard.button("Start", BLUE),
            pause = Dashboard.button("Pause", INK),
            step = Dashboard.button("Step", INK),
            stop = Dashboard.button("Stop", INK),
            reset = Dashboard.button("Reset run", INK),
            reference = Dashboard.button("Reference check", TEAL),
            defaults = Dashboard.button("Restore PDF data", INK),
            export = Dashboard.button("Export run", INK),
            experiments = Dashboard.button("Run 20-seed assessment study", BLUE),
            compareTen = Dashboard.button("Compare 10 algorithms", TEAL);
    final JLabel
            status =
                    new JLabel(
                            "Assessment benchmark / fixed J weights and scales / one iteration ="
                                    + " one complete evaluation"),
            proof = new JLabel("Global optimum found? NO / Exact reference required"),
            iteration = new JLabel("0 evaluations / 0 ms search time"),
            comparison = new JLabel("No feasible result yet");
    final JLabel[] metrics = new JLabel[6];
    final JTabbedPane tabs = new JTabbedPane(), states = new JTabbedPane();
    final ReplayCanvas currentCanvas = new ReplayCanvas(), bestCanvas = new ReplayCanvas();
    final ReplayPanel replay = new ReplayPanel();
    final AssessmentPlots progress = new AssessmentPlots(false),
            timeline = new AssessmentPlots(true);
    final DefaultTableModel
            serverModel =
                    Dashboard.model(
                            true,
                            "Server",
                            "vCPU",
                            "RAM GB",
                            "Disk GB",
                            "GPU slots",
                            "Max services",
                            "Speed GI/s",
                            "Idle W",
                            "Peak W",
                            "USD / busy s",
                            "RTT ms",
                            "Bandwidth Mb/s"),
            serviceModel =
                    Dashboard.model(
                            true,
                            "Service ID",
                            "vCPU",
                            "RAM GB",
                            "Disk GB",
                            "Work GI",
                            "GPU slots",
                            "Payload MB",
                            "Max network ms"),
            networkModel =
                    Dashboard.model(
                            false,
                            "Service",
                            "Host",
                            "RTT ms",
                            "Mb/s",
                            "Payload MB",
                            "Transfer ms",
                            "Network ms",
                            "SLA ms",
                            "Margin ms",
                            "SLA status",
                            "Compute s"),
            assignmentModel =
                    Dashboard.model(
                            false,
                            "Service",
                            "Host",
                            "vCPU",
                            "RAM GB",
                            "Disk GB",
                            "GPU",
                            "Work GI",
                            "Network ms",
                            "SLA status");
    final JTable serverTable = Dashboard.wideTable(serverModel),
            serviceTable = Dashboard.wideTable(serviceModel);
    final JTextArea log = new JTextArea(),
            record = new JTextArea(),
            experimentLog = new JTextArea();
    final javax.swing.Timer motion;
    ProblemData data = ProblemData.assessmentSupplied();
    Evaluator evaluator;
    SearchControl control;
    SearchResult result, exact;
    Evaluator exactModel;
    boolean busy, recordingFrames;
    List<SearchEvent> events = new ArrayList<>();
    List<AssessmentPlots.Point> points = new ArrayList<>();
    SearchEvent currentEvent;
    long frameStart;
    Path saved;
    String errorMessage;
    ExperimentRunner.Study study;
    final List<StudyCharts> studyCharts = new ArrayList<>();
    final DefaultTableModel
            studyTable =
                    Dashboard.model(
                            false,
                            "Method",
                            "Best J",
                            "Mean J",
                            "Median J",
                            "Worst J",
                            "SD J",
                            "Target hit %",
                            "Exact hits %",
                            "Evaluations",
                            "Unique %",
                            "Mean ms",
                            "Confidence"),
            fairTable =
                    Dashboard.model(
                            false,
                            "Protocol",
                            "Method",
                            "Mean J",
                            "Worst J",
                            "Mean evaluations",
                            "Mean ms");
    final JTabbedPane studyViews = new JTabbedPane();
    final PortfolioPanel portfolio = new PortfolioPanel(this::compareTen);
    JPanel controlRows, searchSettings, metricBoxes;

    public AssessmentDashboard() {
        super(new BorderLayout(0, 8));
        setBackground(BACK);
        setBorder(new EmptyBorder(10, 16, 10, 16));
        JPanel header = Dashboard.panel(new BorderLayout(12, 0));
        header.setBorder(null);
        JLabel title = new JLabel("Service Placement Lab / Assessment benchmark");
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        title.setForeground(INK);
        header.add(title, BorderLayout.CENTER);
        JButton original = Dashboard.button("Original CSV project", INK);
        original.addActionListener(e -> openOriginal());
        header.add(original, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        JPanel body = Dashboard.panel(new BorderLayout(0, 7));
        body.setBorder(null);
        body.add(controls(), BorderLayout.NORTH);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Live search", live());
        tabs.addTab("Configuration", configuration());
        tabs.addTab("Performance", progress);
        tabs.addTab("Network / SLAs", Dashboard.scrollTable(slaTable(networkModel, 9)));
        tabs.addTab("Compute timeline", timeline);
        tabs.addTab("Assignments", Dashboard.scrollTable(slaTable(assignmentModel, 8)));
        tabs.addTab("Run record / log", records());
        tabs.addTab("Experiments", experiments());
        tabs.addTab("Lecture / assessment guide", guide());
        tabs.addTab("Recorded replay", replay);
        tabs.addChangeListener(
                event -> {
                    boolean experimentsVisible = tabs.getSelectedIndex() == 7;
                    searchSettings.setVisible(!experimentsVisible);
                    metricBoxes.setVisible(!experimentsVisible);
                    body.revalidate();
                });
        body.add(tabs, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        JPanel footer = Dashboard.panel(new GridLayout(2, 1));
        footer.setBorder(null);
        status.setFont(new Font("SansSerif", Font.PLAIN, 11));
        proof.setFont(new Font("SansSerif", Font.BOLD, 12));
        proof.setForeground(TEAL);
        footer.add(proof);
        footer.add(status);
        add(footer, BorderLayout.SOUTH);
        for (JTextArea area : new JTextArea[] {log, record, experimentLog}) {
            area.setEditable(false);
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setBorder(new EmptyBorder(12, 14, 12, 14));
        }
        currentCanvas.setLive(true);
        bestCanvas.setLive(true);
        load(data);
        setBusy(false);
        export.setEnabled(false);
        motion = new javax.swing.Timer(25, e -> animate());
        motion.setCoalesce(true);
        start.addActionListener(e -> start(false));
        pause.addActionListener(
                e -> {
                    if (control != null) {
                        if (control.paused()) {
                            control.resume();
                            pause.setText("Pause");
                        } else {
                            control.pause();
                            pause.setText("Resume");
                            status.setText(
                                    "Paused before the next real evaluation. Step performs one"
                                            + " complete evaluation.");
                        }
                    }
                });
        step.addActionListener(
                e -> {
                    if (!busy) start(true);
                    if (control != null) {
                        control.step();
                        pause.setText("Resume");
                    }
                });
        stop.addActionListener(
                e -> {
                    if (control != null) control.stop();
                });
        reset.addActionListener(e -> clear());
        reference.addActionListener(e -> showReference());
        defaults.addActionListener(
                e -> {
                    load(ProblemData.assessmentDefaults());
                    status.setText("PDF defaults restored. Run to apply them.");
                });
        export.addActionListener(e -> export());
        experiments.addActionListener(e -> experiment());
        compareTen.addActionListener(e -> compareTen());
    }

    private JTable slaTable(DefaultTableModel model, int statusColumn) {
        JTable table = Dashboard.table(model);
        table.setDefaultRenderer(
                Object.class,
                new javax.swing.table.DefaultTableCellRenderer() {
                    public Component getTableCellRendererComponent(
                            JTable table,
                            Object value,
                            boolean selected,
                            boolean focus,
                            int row,
                            int column) {
                        Component component =
                                super.getTableCellRendererComponent(
                                        table, value, selected, focus, row, column);
                        if (!selected) {
                            boolean pass =
                                    "PASS"
                                            .equals(
                                                    model.getValueAt(
                                                            table.convertRowIndexToModel(row),
                                                            statusColumn));
                            component.setBackground(
                                    pass ? new Color(239, 252, 246) : new Color(255, 235, 239));
                            component.setForeground(pass ? INK : new Color(173, 34, 61));
                        }
                        return component;
                    }
                });
        return table;
    }

    private JPanel controls() {
        JPanel panel = Dashboard.panel(new BorderLayout(0, 5));
        panel.setBorder(null);
        JPanel rows = Dashboard.panel(new BorderLayout(0, 5));
        controlRows = rows;
        rows.setBorder(null);
        JPanel first = Dashboard.flow();
        first.add(new JLabel("Method"));
        first.add(method);
        for (JButton button : new JButton[] {start, pause, step, stop, reset, reference, export})
            first.add(button);
        rows.add(first, BorderLayout.NORTH);
        JPanel second = Dashboard.flow();
        searchSettings = second;
        Dashboard.addField(second, "Seed", seed);
        Dashboard.addField(second, "Eval cap", budget);
        Dashboard.addField(second, "Batch", batch);
        Dashboard.addField(second, "Patience", patience);
        Dashboard.addField(second, "Greediness", greed);
        Dashboard.addField(second, "Target J", target);
        Dashboard.addField(second, "View ms", pace);
        rows.add(second, BorderLayout.CENTER);
        panel.add(rows, BorderLayout.NORTH);
        JPanel boxes = new JPanel(new GridLayout(1, 6, 8, 0));
        metricBoxes = boxes;
        boxes.setOpaque(false);
        String[] names = {
            "BEST J / dimensionless",
            "MAKESPAN M / seconds",
            "COST C / USD",
            "ENERGY E / Wh",
            "MEAN L / ms",
            "WORST Lmax / ms"
        };
        for (int i = 0; i < 6; i++) {
            metrics[i] = new JLabel("--");
            boxes.add(Dashboard.metric(names[i], metrics[i]));
        }
        panel.add(boxes, BorderLayout.CENTER);
        return panel;
    }

    private JPanel live() {
        JPanel panel = Dashboard.panel(new BorderLayout(0, 5));
        panel.setBorder(null);
        JPanel labels = Dashboard.panel(new GridLayout(2, 1));
        labels.setBorder(new EmptyBorder(5, 12, 5, 12));
        labels.add(iteration);
        labels.add(comparison);
        panel.add(labels, BorderLayout.NORTH);
        states.addTab("Current trial / proposals may be rejected", currentCanvas);
        states.addTab("Best feasible so far", bestCanvas);
        panel.add(states, BorderLayout.CENTER);
        return panel;
    }

    private JPanel configuration() {
        JPanel panel = Dashboard.panel(new BorderLayout(0, 6));
        JPanel actions = Dashboard.flow();
        actions.add(defaults);
        actions.add(
                new JLabel(
                        "Edit all benchmark fields / GPU and service slots are integers / network"
                                + " latency is separate from compute time"));
        panel.add(actions, BorderLayout.NORTH);
        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        Dashboard.section(
                                "Servers: capacities, throughput, power, busy-time price and"
                                        + " network",
                                Dashboard.scrollTable(serverTable)),
                        Dashboard.section(
                                "Services: reserved resources, work, payload and hard network SLA",
                                Dashboard.scrollTable(serviceTable)));
        split.setResizeWeight(.37);
        split.setDividerLocation(160);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent records() {
        JSplitPane split =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(record), new JScrollPane(log));
        split.setResizeWeight(.48);
        return split;
    }

    private JComponent experiments() {
        JPanel panel = Dashboard.panel(new BorderLayout(8, 8));
        JPanel buttons = Dashboard.flow();
        buttons.add(experiments);
        buttons.add(compareTen);
        buttons.add(new JLabel("Same assessment data / paired seeds / measured results"));
        panel.add(buttons, BorderLayout.NORTH);
        studyViews.addTab("Progress / results", new JScrollPane(experimentLog));
        studyViews.addTab(
                "Statistics / confidence", Dashboard.scrollTable(Dashboard.wideTable(studyTable)));
        JTabbedPane charts = new JTabbedPane();
        for (StudyCharts.Kind kind : StudyCharts.Kind.values()) {
            StudyCharts chart = new StudyCharts(kind);
            studyCharts.add(chart);
            charts.addTab(kind.toString(), chart);
        }
        studyViews.addTab("Charts", charts);
        studyViews.addTab("Fair budgets", Dashboard.scrollTable(Dashboard.table(fairTable)));
        studyViews.addTab("Compare 10 algorithms", portfolio);
        panel.add(studyViews, BorderLayout.CENTER);
        return panel;
    }

    private JComponent guide() {
        JEditorPane guide = new JEditorPane("text/html", AssessmentGuide.html());
        guide.setEditable(false);
        return new JScrollPane(guide);
    }

    void load(ProblemData data) {
        this.data = data;
        serverModel.setRowCount(0);
        serviceModel.setRowCount(0);
        for (ProblemData.Server s : data.servers())
            serverModel.addRow(
                    new Object[] {
                        s.name(),
                        s.cpu(),
                        s.ram(),
                        s.storage(),
                        s.gpu(),
                        s.maxServices(),
                        s.speed(),
                        s.idlePower(),
                        s.peakPower(),
                        s.busyPrice(),
                        s.latencyMs(),
                        s.bandwidth()
                    });
        for (ProblemData.Service s : data.services())
            serviceModel.addRow(
                    new Object[] {
                        s.name(),
                        s.cpu(),
                        s.ram(),
                        s.storage(),
                        s.work(),
                        s.gpu(),
                        s.payload(),
                        s.maxLatency()
                    });
        currentCanvas.setData(data);
        bestCanvas.setData(data);
    }

    ProblemData read() {
        if (serverTable.isEditing()) serverTable.getCellEditor().stopCellEditing();
        if (serviceTable.isEditing()) serviceTable.getCellEditor().stopCellEditing();
        List<ProblemData.Server> servers = new ArrayList<>();
        List<ProblemData.Service> services = new ArrayList<>();
        for (int r = 0; r < 4; r++)
            servers.add(
                    new ProblemData.Server(
                            serverModel.getValueAt(r, 0).toString(),
                            number(serverModel, r, 1),
                            number(serverModel, r, 2),
                            number(serverModel, r, 3),
                            number(serverModel, r, 10),
                            0,
                            integer(serverModel, r, 4),
                            integer(serverModel, r, 5),
                            number(serverModel, r, 6),
                            number(serverModel, r, 7),
                            number(serverModel, r, 8),
                            number(serverModel, r, 9),
                            number(serverModel, r, 11)));
        for (int r = 0; r < 12; r++)
            services.add(
                    new ProblemData.Service(
                            serviceModel.getValueAt(r, 0).toString(),
                            number(serviceModel, r, 1),
                            number(serviceModel, r, 2),
                            number(serviceModel, r, 3),
                            0,
                            number(serviceModel, r, 4),
                            integer(serviceModel, r, 5),
                            number(serviceModel, r, 6),
                            number(serviceModel, r, 7)));
        return new ProblemData("assessment-edited", servers, services, true);
    }

    private static double number(DefaultTableModel table, int row, int column) {
        return Double.parseDouble(table.getValueAt(row, column).toString());
    }

    private static int integer(DefaultTableModel table, int row, int column) {
        double value = number(table, row, column);
        if (!Double.isFinite(value)
                || value < 0
                || value > Integer.MAX_VALUE
                || value != Math.rint(value))
            throw new IllegalArgumentException("GPU and service-slot counts must be integers");
        return (int) value;
    }

    private SearchConfig config() {
        int cap = ((Number) budget.getValue()).intValue();
        SearchMethod selected = (SearchMethod) method.getSelectedItem();
        if (selected == SearchMethod.RANDOM) cap = 100000;
        if (selected == SearchMethod.TARGET) cap = 500000;
        return new SearchConfig(
                ((Number) seed.getValue()).longValue(),
                cap,
                ((Number) batch.getValue()).intValue(),
                ((Number) patience.getValue()).intValue(),
                ((Number) greed.getValue()).doubleValue(),
                ((Number) target.getValue()).doubleValue());
    }

    void start(boolean paused) {
        try {
            data = read();
            evaluator = new Evaluator(data, 0, Evaluator.Objective.ASSESSMENT, .75);
            SearchConfig config = config();
            SearchMethod selected = (SearchMethod) method.getSelectedItem();
            events = new ArrayList<>();
            points = new ArrayList<>();
            log.setText("");
            record.setText("");
            currentEvent = null;
            errorMessage = null;
            control = new SearchControl(((Number) pace.getValue()).intValue());
            if (paused) control.pause();
            currentCanvas.setData(data);
            bestCanvas.setData(data);
            currentCanvas.setMinimumServices(0);
            bestCanvas.setMinimumServices(0);
            proof.setText("Global optimum found? NO / searching");
            setBusy(true);
            pause.setText(paused ? "Resume" : "Pause");
            tabs.setSelectedIndex(0);
            recordingFrames = true;
            motion.start();
            new SwingWorker<SearchResult, SearchEvent>() {
                protected SearchResult doInBackground() throws Exception {
                    List<SearchEvent> recording = new ArrayList<>();
                    result =
                            new Optimizer()
                                    .run(
                                            evaluator,
                                            selected,
                                            config,
                                            null,
                                            control::stopped,
                                            event -> {
                                                recording.add(event);
                                                publish(event);
                                                if (event.type() != SearchEvent.Type.FINISH)
                                                    control.pace();
                                            },
                                            control);
                    events = recording;
                    saved =
                            Path.of(
                                    "results",
                                    "assessment-runs",
                                    "run-" + System.currentTimeMillis());
                    save(saved, result, recording);
                    return result;
                }

                protected void process(List<SearchEvent> updates) {
                    for (SearchEvent event : updates) remember(event);
                    if (!updates.isEmpty()) showEvent(updates.get(updates.size() - 1));
                }

                protected void done() {
                    try {
                        result = get();
                        if (result.completeEnumeration(data.searchSpace())) {
                            exact = result;
                            exactModel = evaluator;
                        }
                        finish();
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        recordingFrames = false;
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
            setBusy(false);
        }
    }

    private void remember(SearchEvent event) {
        if (event.bestEvaluation() != null) {
            AssessmentPlots.Point point =
                    new AssessmentPlots.Point(event.evaluations(), event.bestEvaluation());
            if (!points.isEmpty()
                    && points.get(points.size() - 1).iteration() == event.evaluations())
                points.set(points.size() - 1, point);
            else points.add(point);
        }
        String line =
                "Eval "
                        + event.evaluations()
                        + " / "
                        + event.type()
                        + " / "
                        + (event.evaluation() == null
                                ? "no result"
                                : event.evaluation().feasible()
                                        ? "FEASIBLE J=" + f(event.evaluation().score())
                                        : "REJECTED: "
                                                + String.join(
                                                        "; ", event.evaluation().violations()))
                        + "\n";
        log.append(line);
        log.setCaretPosition(log.getDocument().getLength());
    }

    void showEvent(SearchEvent event) {
        currentEvent = event;
        frameStart = System.nanoTime();
        currentCanvas.setEvent(event, 0);
        currentCanvas.setComparison(
                ResultComparison.step(evaluator, event, event.beforeEvaluation()),
                history(),
                "ACTUAL BEST J HISTORY");
        SearchEvent best =
                new SearchEvent(
                        event.sequence(),
                        event.type(),
                        event.evaluations(),
                        event.feasible(),
                        event.unique(),
                        event.restarts(),
                        event.milliseconds(),
                        event.best(),
                        event.bestEvaluation(),
                        event.best(),
                        event.bestEvaluation(),
                        event.best(),
                        event.bestEvaluation(),
                        event.newBest(),
                        event.detail());
        bestCanvas.setEvent(best, 1);
        bestCanvas.setComparison(
                ResultComparison.between(
                        evaluator,
                        event.beforeEvaluation(),
                        event.bestEvaluation(),
                        "PREVIOUS STATE",
                        "BEST FEASIBLE",
                        "Best state from this same evaluation"),
                history(),
                "ACTUAL BEST J HISTORY");
        updateMetrics(event.bestEvaluation());
        network(event.candidate(), event.evaluation());
        timeline.set(data, event.best(), event.bestEvaluation());
        progress.set(points);
        iteration.setText(
                "Evaluation / iteration "
                        + String.format(Locale.ROOT, "%,d", event.evaluations())
                        + " / "
                        + event.milliseconds()
                        + " ms active search / "
                        + String.format(
                                Locale.ROOT, "%.4f%%", 100. * event.unique() / data.searchSpace())
                        + " unique coverage / "
                        + event.type());
        status.setText(
                event.evaluation() != null && !event.evaluation().feasible()
                        ? "Rejected trial: " + String.join("; ", event.evaluation().violations())
                        : "Live algorithm state / presentation waits and pauses excluded from"
                                + " active search time");
        updateProof(event.bestEvaluation());
    }

    private List<Double> history() {
        List<Double> history = new ArrayList<>();
        for (AssessmentPlots.Point point : points) history.add(point.value().score());
        return history;
    }

    private void animate() {
        if (currentEvent == null) return;
        double fraction = Math.min(1, (System.nanoTime() - frameStart) / 250_000_000.);
        currentCanvas.setEvent(currentEvent, fraction);
        currentCanvas.advanceComparison(25);
        bestCanvas.advanceComparison(25);
        if (!recordingFrames
                && fraction >= 1
                && !currentCanvas.isComparisonPulsing()
                && !bestCanvas.isComparisonPulsing()) motion.stop();
    }

    private void updateMetrics(Evaluation value) {
        if (value == null) {
            for (JLabel metric : metrics) metric.setText("--");
            comparison.setText("No feasible result yet");
            return;
        }
        double[] values = {
            value.score(),
            value.makespanSeconds(),
            value.cost(),
            value.energyWh(),
            value.delayMs(),
            value.worstLatencyMs()
        };
        for (int i = 0; i < 6; i++) metrics[i].setText(f(values[i]));
        Evaluation baseline = evaluator.evaluate(ProblemData.referenceAssignment());
        comparison.setText(
                "BEST FEASIBLE / SLA pass "
                        + (12 - value.slaViolations())
                        + "/12 / "
                        + (baseline.feasible()
                                ? String.format(
                                        Locale.ROOT,
                                        "%.3f%% improvement versus evaluated reference",
                                        100 * (baseline.score() - value.score()) / baseline.score())
                                : "reference is infeasible after edits; no improvement claim"));
    }

    private void updateProof(Evaluation value) {
        boolean known =
                exact != null
                        && exact.provenOptimal(data.searchSpace())
                        && ResultComparison.sameModel(evaluator, exactModel);
        boolean matches =
                known
                        && value != null
                        && Math.abs(value.score() - exact.evaluation().score()) < 1e-9;
        proof.setText(
                "Global optimum found? "
                        + (matches
                                ? "YES / matches completed Exact"
                                : known
                                        ? "NO / best found; Exact gap "
                                                + f(
                                                        ExperimentRunner.gap(
                                                                value == null
                                                                        ? Double.NaN
                                                                        : value.score(),
                                                                exact,
                                                                data.searchSpace()))
                                                + "%"
                                        : "NO / best found; no completed matching Exact"
                                                + " reference"));
    }

    private void network(Candidate candidate, Evaluation value) {
        networkModel.setRowCount(0);
        assignmentModel.setRowCount(0);
        if (candidate == null || value == null) return;
        for (int i = 0; i < 12; i++) {
            ProblemData.Service service = data.services().get(i);
            int s = candidate.server(i);
            ProblemData.Server server = data.servers().get(s);
            double latency = evaluator.networkLatency(i, s),
                    margin = service.maxLatency() - latency;
            String pass = margin >= -1e-9 ? "PASS" : "FAIL";
            networkModel.addRow(
                    new Object[] {
                        service.name(),
                        server.name(),
                        server.latencyMs(),
                        server.bandwidth(),
                        service.payload(),
                        f(latency - server.latencyMs()),
                        f(latency),
                        service.maxLatency(),
                        f(margin),
                        pass,
                        f(service.work() / server.speed())
                    });
            assignmentModel.addRow(
                    new Object[] {
                        service.name(),
                        server.name(),
                        service.cpu(),
                        service.ram(),
                        service.storage(),
                        service.gpu(),
                        service.work(),
                        f(latency),
                        pass
                    });
        }
    }

    private void save(Path folder, SearchResult result, List<SearchEvent> recording)
            throws Exception {
        CsvFiles.export(folder, evaluator, result);
        CsvFiles.writeEvents(folder, recording);
        List<AssessmentPlots.Point> evidence = new ArrayList<>();
        for (SearchEvent event : recording)
            if (event.bestEvaluation() != null)
                evidence.add(
                        new AssessmentPlots.Point(event.evaluations(), event.bestEvaluation()));
        AssessmentPlots.export(folder, data, result, evidence);
    }

    private void finish() {
        record.setText(
                "Autosaved: "
                        + saved.toAbsolutePath()
                        + "\n\n"
                        + String.join("\n", CsvFiles.metadata(evaluator, result)));
        replay.load(evaluator, result, events, false);
        updateMetrics(result.evaluation());
        network(result.best(), result.evaluation());
        timeline.set(data, result.best(), result.evaluation());
        updateProof(result.evaluation());
        status.setText(
                result.stopReason()
                        + " / active search "
                        + result.milliseconds()
                        + " ms / saved "
                        + saved);
        status.setToolTipText(status.getText());
    }

    private void showReference() {
        try {
            result = null;
            export.setEnabled(false);
            record.setText(
                    "PDF reference check: this is an evaluated mapping, not an optimization run.");
            data = read();
            evaluator = new Evaluator(data, 0, Evaluator.Objective.ASSESSMENT, .75);
            Evaluation value = evaluator.evaluate(ProblemData.referenceAssignment());
            SearchEvent event =
                    new SearchEvent(
                            1,
                            SearchEvent.Type.FINISH,
                            0,
                            value.feasible() ? 1 : 0,
                            0,
                            0,
                            0,
                            null,
                            null,
                            ProblemData.referenceAssignment(),
                            value,
                            value.feasible() ? ProblemData.referenceAssignment() : null,
                            value.feasible() ? value : null,
                            false,
                            "PDF reference, valid but not claimed optimal");
            points = new ArrayList<>();
            if (value.feasible()) points.add(new AssessmentPlots.Point(0, value));
            currentCanvas.setData(data);
            bestCanvas.setData(data);
            showEvent(event);
            motion.start();
            status.setText(
                    value.feasible()
                            ? "PDF reference loaded / J="
                                    + f(value.score())
                                    + " / all 12 SLAs pass / not an optimum claim"
                            : "Reference rejected: " + String.join("; ", value.violations()));
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        for (JComponent component :
                new JComponent[] {
                    start,
                    reset,
                    reference,
                    defaults,
                    method,
                    seed,
                    budget,
                    batch,
                    patience,
                    greed,
                    target,
                    pace,
                    serverTable,
                    serviceTable,
                    experiments,
                    compareTen
                }) component.setEnabled(!value);
        portfolio.setBusy(value);
        pause.setEnabled(value);
        stop.setEnabled(value);
        step.setEnabled(true);
        export.setEnabled(!value && result != null);
    }

    private void clear() {
        motion.stop();
        recordingFrames = false;
        proof.setText("Global optimum found? NO / no run displayed");
        result = null;
        currentEvent = null;
        events = new ArrayList<>();
        points = new ArrayList<>();
        currentCanvas.setData(data);
        bestCanvas.setData(data);
        progress.set(points);
        timeline.set(data, null, null);
        networkModel.setRowCount(0);
        assignmentModel.setRowCount(0);
        updateMetrics(null);
        log.setText("");
        record.setText("");
        export.setEnabled(false);
        status.setText("Run cleared. Input data preserved.");
    }

    private void export() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path folder =
                    chooser.getSelectedFile()
                            .toPath()
                            .resolve("assessment-" + System.currentTimeMillis());
            save(folder, result, events);
            status.setText("Exported " + folder.toAbsolutePath());
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void experiment() {
        try {
            data = read();
            Evaluator model = new Evaluator(data, 0, Evaluator.Objective.ASSESSMENT, .75);
            clear();
            evaluator = model;
            SearchConfig settings = config();
            setBusy(true);
            pause.setEnabled(false);
            step.setEnabled(false);
            control = new SearchControl(0);
            tabs.setSelectedIndex(7);
            experimentLog.setText("Running assessment experiments...\n");
            new SwingWorker<ExperimentRunner.Study, String>() {
                Path folder =
                        Path.of(
                                "results",
                                "assessment-studies",
                                "study-" + System.currentTimeMillis());

                protected ExperimentRunner.Study doInBackground() throws Exception {
                    ExperimentRunner.Study measured =
                            ExperimentRunner.study(
                                    model,
                                    new SearchConfig(
                                            settings.seed(),
                                            20000,
                                            10000,
                                            1,
                                            settings.greediness(),
                                            settings.target()),
                                    20,
                                    100000,
                                    message -> publish(message),
                                    control::stopped);
                    ExperimentRunner.write(folder, measured);
                    AssessmentExperiments.additional(
                            folder,
                            model,
                            settings.seed(),
                            20,
                            message -> publish(message),
                            control::stopped);
                    return measured;
                }

                protected void process(List<String> messages) {
                    for (String message : messages) experimentLog.append(message + "\n");
                    experimentLog.setCaretPosition(experimentLog.getDocument().getLength());
                    status.setText(messages.get(messages.size() - 1));
                }

                protected void done() {
                    try {
                        study = get();
                        exact = study.exact();
                        exactModel = model;
                        showStudy(study);
                        experimentLog.append(
                                AssessmentExperiments.summary(study)
                                        + "\nSaved "
                                        + folder.toAbsolutePath());
                        status.setText("Assessment study saved: " + folder.toAbsolutePath());
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
            setBusy(false);
        }
    }

    private record PortfolioUpdate(String message, ExperimentRunner.Study study) {}

    void compareTen() {
        if (busy) return;
        try {
            data = read();
            Evaluator model = new Evaluator(data, 0, Evaluator.Objective.ASSESSMENT, .75);
            SearchConfig settings =
                    new SearchConfig(
                            ((Number) portfolio.firstSeed.getValue()).longValue(),
                            ((Number) portfolio.evaluations.getValue()).intValue(),
                            10000,
                            0,
                            ((Number) greed.getValue()).doubleValue(),
                            ((Number) target.getValue()).doubleValue());
            int seeds = ((Number) portfolio.seeds.getValue()).intValue();
            setBusy(true);
            pause.setEnabled(false);
            step.setEnabled(false);
            control = new SearchControl(0);
            tabs.setSelectedIndex(7);
            studyViews.setSelectedIndex(4);
            portfolio.begin();
            status.setText("Comparing ten algorithms / exact reference first...");
            proof.setText(
                    "Comparison / Exact supplies the reference; all ten searches keep their own"
                            + " best result.");
            new SwingWorker<ExperimentRunner.Study, PortfolioUpdate>() {
                final Path folder =
                        Path.of(
                                "results",
                                "ten-algorithm-comparisons",
                                "comparison-" + System.currentTimeMillis());

                protected ExperimentRunner.Study doInBackground() throws Exception {
                    var measured =
                            AlgorithmComparison.run(
                                    model,
                                    settings,
                                    seeds,
                                    message -> publish(new PortfolioUpdate(message, null)),
                                    partial -> publish(new PortfolioUpdate(null, partial)),
                                    control::stopped);
                    AlgorithmComparison.write(folder, measured);
                    return measured;
                }

                protected void process(List<PortfolioUpdate> updates) {
                    for (var update : updates) {
                        if (update.message() != null) {
                            portfolio.message(update.message());
                            status.setText(update.message());
                        }
                        if (update.study() != null) portfolio.show(update.study(), null);
                    }
                }

                protected void done() {
                    try {
                        var measured = get();
                        showComparison(measured, folder);
                        status.setText(
                                (AlgorithmComparison.complete(measured)
                                                ? "Ten-algorithm comparison complete: "
                                                : "Partial comparison saved: ")
                                        + folder.toAbsolutePath());
                    } catch (Exception ex) {
                        error(ex);
                    } finally {
                        setBusy(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(ex);
            setBusy(false);
        }
    }

    void showComparison(ExperimentRunner.Study measured, Path folder) {
        portfolio.show(measured, folder);
        exact = measured.exact();
        exactModel = measured.evaluator();
        proof.setText(
                exact.provenOptimal(exactModel.data.searchSpace())
                        ? "Comparison / Exact optimum proven / see each algorithm's gap and"
                                + " optimum-hit rate."
                        : "Comparison / Exact reference incomplete or no feasible solution / see"
                                + " recorded status.");
    }

    void showStudy(ExperimentRunner.Study study) {
        this.study = study;
        studyTable.setRowCount(0);
        fairTable.setRowCount(0);
        for (StudyCharts chart : studyCharts) chart.set(study);
        for (SearchMethod method : ExperimentRunner.MAIN_METHODS) {
            ExperimentRunner.Summary value = ExperimentRunner.summarize(study, method, "main");
            studyTable.addRow(
                    new Object[] {
                        method,
                        f(value.best()),
                        f(value.mean()),
                        f(value.median()),
                        f(value.worst()),
                        f(value.sd()),
                        value.successRate(),
                        value.optimumRate(),
                        value.evaluations(),
                        100 * value.unique() / study.evaluator().data.searchSpace(),
                        value.runtime(),
                        ExperimentRunner.confidence(study, value)
                    });
        }
        for (String protocol : new String[] {"equal_budget", "srs_stop_budget"})
            for (SearchMethod method :
                    new SearchMethod[] {SearchMethod.RANDOM, SearchMethod.HYBRID}) {
                ExperimentRunner.Summary value =
                        ExperimentRunner.summarize(
                                study,
                                method,
                                protocol.equals("srs_stop_budget") && method == SearchMethod.HYBRID
                                        ? "main"
                                        : protocol);
                if (value.runs() > 0)
                    fairTable.addRow(
                            new Object[] {
                                protocol,
                                method,
                                f(value.mean()),
                                f(value.worst()),
                                value.evaluations(),
                                value.runtime()
                            });
            }
    }

    private void error(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        errorMessage = cause.getMessage();
        status.setText("Error: " + errorMessage);
        if (isShowing())
            JOptionPane.showMessageDialog(
                    this,
                    errorMessage,
                    "Invalid configuration or run error",
                    JOptionPane.ERROR_MESSAGE);
    }

    private void openOriginal() {
        JFrame frame = new JFrame("Original CSV project / weighted latency");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(new Dashboard());
        frame.setSize(1440, 850);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    static String f(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "N/A";
    }
}
