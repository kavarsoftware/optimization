package optimizer;

import static optimizer.ReplayCanvas.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Presentation-speed replay. Search timing and replay timing remain separate. */
public final class ReplayPanel extends JPanel {
    final ReplayCanvas canvas = new ReplayCanvas();
    final JButton play = control("Play"),
            back = control("Back"),
            next = control("Step"),
            restart = control("Replay");
    final JComboBox<String> speed = new JComboBox<>(new String[] {"0.5x", "1x", "2x", "4x"});
    final JComboBox<String> comparisonMode =
            new JComboBox<>(new String[] {"Step change", "Previous run"});
    final JSlider timeline = new JSlider(0, 0, 0);
    final JLabel title = new JLabel("Watch the search, one decision at a time"),
            detail =
                    new JLabel(
                            "Run optimizer to record and replay real placements, moves and swaps."),
            counter = new JLabel("No recording"),
            timing = new JLabel("Recorded simulation / no live server traffic");
    private final javax.swing.Timer timer;
    private List<SearchEvent> events = List.of();
    private Evaluator evaluator;
    private SearchResult result;
    private Evaluator previousModel;
    private SearchResult previousRun;
    private Consumer<SearchEvent> listener;
    private int index;
    private double frameMs = 800;
    private boolean playing, stepping, adjusting;
    private long lastTick;

    public ReplayPanel() {
        super(new BorderLayout(0, 0));
        setBackground(BG);
        JPanel heading = dark(new BorderLayout(12, 3));
        heading.setBorder(new EmptyBorder(10, 16, 7, 16));
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT);
        detail.setFont(new Font("SansSerif", Font.PLAIN, 12));
        detail.setForeground(MUTED);
        heading.add(title, BorderLayout.NORTH);
        heading.add(detail, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        JPanel footer = dark(new BorderLayout(12, 5));
        footer.setBorder(new EmptyBorder(7, 14, 9, 14));
        JPanel controls = dark(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(play);
        controls.add(back);
        controls.add(next);
        controls.add(restart);
        JLabel speedLabel = new JLabel("Speed");
        speedLabel.setForeground(MUTED);
        controls.add(speedLabel);
        controls.add(speed);
        JLabel compareLabel = new JLabel("Compare");
        compareLabel.setForeground(MUTED);
        controls.add(compareLabel);
        controls.add(comparisonMode);
        counter.setForeground(TEXT);
        counter.setFont(new Font("Monospaced", Font.BOLD, 12));
        controls.add(counter);
        footer.add(controls, BorderLayout.NORTH);
        timeline.setOpaque(false);
        timeline.setFocusable(true);
        timeline.setToolTipText("Seek to a recorded search decision");
        footer.add(timeline, BorderLayout.CENTER);
        timing.setForeground(MUTED);
        timing.setFont(new Font("SansSerif", Font.PLAIN, 10));
        footer.add(timing, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        speed.setSelectedIndex(1);
        timer = new javax.swing.Timer(25, e -> tick());
        timer.setCoalesce(true);
        play.addActionListener(
                e -> {
                    if (playing) pause();
                    else play();
                });
        back.addActionListener(e -> seek(index - 1));
        next.addActionListener(e -> step());
        restart.addActionListener(
                e -> {
                    seek(0);
                    play();
                });
        timeline.addChangeListener(
                e -> {
                    if (!adjusting) seek(timeline.getValue());
                });
        comparisonMode.setToolTipText(
                "Compare this proposal with its starting placement, or compare two completed runs"
                        + " in this session");
        comparisonMode.addActionListener(
                e -> {
                    if (!events.isEmpty()) showComparison();
                });
        updateButtons();
    }

    public void setListener(Consumer<SearchEvent> listener) {
        this.listener = listener;
    }

    public void setPreviousRun(Evaluator model, SearchResult run) {
        previousModel = model;
        previousRun = run;
    }

    public List<SearchEvent> events() {
        return events;
    }

    public int frameIndex() {
        return index;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void clear(ProblemData data) {
        pause();
        events = List.of();
        index = 0;
        canvas.setData(data);
        title.setText("Recording the real search...");
        detail.setText(
                "The calculation runs at full speed. Playback begins when the recording is ready.");
        adjusting = true;
        timeline.setMaximum(0);
        timeline.setValue(0);
        adjusting = false;
        counter.setText("Recording");
        updateButtons();
    }

    public void load(
            Evaluator evaluator, SearchResult result, List<SearchEvent> events, boolean autoPlay) {
        pause();
        this.evaluator = evaluator;
        this.result = result;
        this.events = List.copyOf(events);
        canvas.setData(evaluator.data);
        canvas.setMinimumServices(evaluator.minServices);
        index = 0;
        adjusting = true;
        timeline.setMaximum(Math.max(0, events.size() - 1));
        timeline.setValue(0);
        adjusting = false;
        timing.setText(
                "Search calculation: "
                        + result.milliseconds()
                        + " ms / "
                        + events.size()
                        + " recorded steps / trials are sampled; all evaluations count / playback"
                        + " speed is independent");
        if (events.isEmpty()) {
            title.setText("No recorded decisions");
            detail.setText(result.stopReason());
            updateButtons();
            return;
        }
        frameMs = autoPlay ? 0 : 800;
        showFrame();
        if (autoPlay) play();
        else updateButtons();
    }

    public void seek(int value) {
        if (events.isEmpty()) return;
        pause();
        index = Math.max(0, Math.min(events.size() - 1, value));
        frameMs = 800;
        showFrame();
        updateButtons();
    }

    public void play() {
        if (events.isEmpty()) return;
        if (index >= events.size() - 1) {
            index = 0;
            frameMs = 0;
            showFrame();
        }
        playing = true;
        stepping = false;
        play.setText("Pause");
        lastTick = System.nanoTime();
        timer.start();
        updateButtons();
    }

    public void pause() {
        playing = false;
        stepping = false;
        timer.stop();
        play.setText("Play");
        updateButtons();
    }

    public void step() {
        if (events.isEmpty()) return;
        pause();
        if (index < events.size() - 1) index++;
        frameMs = 0;
        showFrame();
        stepping = true;
        lastTick = System.nanoTime();
        timer.start();
        updateButtons();
    }

    private void tick() {
        long now = System.nanoTime();
        double delta = Math.min(100, (now - lastTick) / 1_000_000.);
        lastTick = now;
        double multiplier =
                switch (speed.getSelectedIndex()) {
                    case 0 -> .5;
                    case 2 -> 2.;
                    case 3 -> 4.;
                    default -> 1.;
                };
        if (playing || stepping) {
            frameMs += delta * multiplier;
            if (frameMs >= 800) {
                if (playing && index < events.size() - 1) {
                    index++;
                    frameMs = 0;
                    showFrame();
                } else {
                    frameMs = 800;
                    playing = false;
                    stepping = false;
                    play.setText("Play");
                    updateButtons();
                }
            }
            if (!events.isEmpty()) canvas.setEvent(events.get(index), frameMs / 800.);
        }
        canvas.advanceComparison(delta * multiplier);
        if (!playing && !stepping && !canvas.isComparisonPulsing()) timer.stop();
    }

    private void showFrame() {
        if (events.isEmpty()) return;
        SearchEvent e = events.get(index);
        Color verdict = TEXT;
        title.setText(describe(e));
        String explanation = e.detail();
        if (e.type() == SearchEvent.Type.MOVE || e.type() == SearchEvent.Type.SWAP) {
            if (!e.evaluation().feasible()) {
                verdict = RED;
                explanation =
                        "REJECTED: "
                                + String.join(
                                        "; ",
                                        e.evaluation()
                                                .violations()
                                                .subList(
                                                        0,
                                                        Math.min(
                                                                2,
                                                                e.evaluation()
                                                                        .violations()
                                                                        .size())))
                                + ". The trial returns to its starting placement.";
            } else if (e.beforeEvaluation() != null
                    && e.evaluation().score() < e.beforeEvaluation().score() - 1e-10) {
                verdict = GREEN;
                explanation =
                        "Feasible improvement: score "
                                + number(e.beforeEvaluation().score())
                                + " to "
                                + number(e.evaluation().score())
                                + ". Descent accepts its best improving neighbor after the pass.";
            } else {
                verdict = AMBER;
                explanation =
                        "No strict improvement. This feasible trial returns to its starting"
                                + " placement.";
            }
        } else if (e.type() == SearchEvent.Type.CONSTRUCT) {
            verdict = CYAN;
            explanation =
                    "Capacity-aware construction produced a complete feasible start. Restart "
                            + e.restarts()
                            + ".";
        } else if (e.type() == SearchEvent.Type.ACCEPT) {
            verdict = GREEN;
            explanation =
                    "Accepted improvement: score "
                            + number(e.beforeEvaluation().score())
                            + " to "
                            + number(e.evaluation().score())
                            + ". Continue from the new placement.";
        } else if (e.type() == SearchEvent.Type.RANDOM_SAMPLE) {
            verdict = e.evaluation().feasible() ? GREEN : RED;
            explanation =
                    e.evaluation().feasible()
                            ? "A feasible uniform random sample."
                            : "Rejected random sample: " + e.evaluation().violations().get(0);
        } else if (e.type() == SearchEvent.Type.FINISH) {
            verdict = e.best() == null ? AMBER : GREEN;
            explanation =
                    e.detail()
                            + (e.best() == null
                                    ? ". No feasible placement was recorded."
                                    : ". Showing the final best-found placement.");
        }
        if (e.newBest()) explanation = "NEW BEST FOUND. " + explanation;
        title.setForeground(verdict);
        detail.setForeground(MUTED);
        detail.setText(explanation);
        detail.setToolTipText(explanation);
        counter.setText(
                String.format(
                        Locale.ROOT,
                        "Step %d/%d  /  eval %,d",
                        index + 1,
                        events.size(),
                        e.evaluations()));
        adjusting = true;
        timeline.setValue(index);
        adjusting = false;
        canvas.setEvent(e, frameMs / 800.);
        showComparison();
        if (listener != null) listener.accept(e);
    }

    private void showComparison() {
        if (events.isEmpty() || evaluator == null || result == null) return;
        ResultComparison value;
        List<Double> history;
        String label;
        if (comparisonMode.getSelectedIndex() == 0) {
            SearchEvent event = events.get(index);
            Evaluation fallback = index == 0 ? null : events.get(index - 1).bestEvaluation();
            value = ResultComparison.step(evaluator, event, fallback);
            history = new ArrayList<>();
            for (SearchResult.Point point : result.trace())
                if (point.evaluations() <= event.evaluations()) history.add(point.score());
            label = "BEST SCORE / RECORDED CHECKPOINTS";
        } else {
            value = ResultComparison.runs(previousModel, previousRun, evaluator, result);
            history = new ArrayList<>();
            if (value.outcome() != ResultComparison.Outcome.INCOMPARABLE) {
                if (value.previous().valid()) history.add(value.previous().value());
                if (value.current().valid()) history.add(value.current().value());
            }
            label = "PREVIOUS RUN / CURRENT RUN";
        }
        canvas.setComparison(value, history, label);
        if (isShowing() && canvas.isComparisonPulsing() && !timer.isRunning()) {
            lastTick = System.nanoTime();
            timer.start();
        }
    }

    private String describe(SearchEvent e) {
        if (e.type() == SearchEvent.Type.MOVE
                || e.type() == SearchEvent.Type.SWAP
                || e.type() == SearchEvent.Type.ACCEPT) {
            List<Integer> changed = new ArrayList<>();
            if (e.before() != null && e.candidate() != null)
                for (int i = 0; i < e.candidate().size(); i++)
                    if (e.before().server(i) != e.candidate().server(i)) changed.add(i);
            if (changed.size() == 1) {
                int i = changed.get(0);
                return (e.type() == SearchEvent.Type.ACCEPT ? "Accept move: " : "Test move: ")
                        + evaluator.data.services().get(i).name()
                        + "  "
                        + host(e.before().server(i))
                        + " to "
                        + host(e.candidate().server(i));
            }
            if (changed.size() == 2)
                return (e.type() == SearchEvent.Type.ACCEPT ? "Accept swap: " : "Test swap: ")
                        + evaluator.data.services().get(changed.get(0)).name()
                        + " and "
                        + evaluator.data.services().get(changed.get(1)).name();
        }
        return switch (e.type()) {
            case EXACT_SCAN -> "Exact enumeration / best placement so far";
            case CONSTRUCT -> "Build a starting placement / restart " + e.restarts();
            case RANDOM_SAMPLE -> "Evaluate a random complete assignment";
            case CHECKPOINT ->
                    "Batch checkpoint / "
                            + String.format(Locale.ROOT, "%,d", e.evaluations())
                            + " evaluations";
            case LOCAL_OPTIMUM -> "Local neighborhood exhausted / restart next";
            case FINISH -> "Search finished / best-found placement";
            default -> "Recorded search decision";
        };
    }

    private String host(int s) {
        return evaluator.data.servers().get(s).name();
    }

    private static String number(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    private void updateButtons() {
        boolean any = !events.isEmpty();
        play.setEnabled(any);
        restart.setEnabled(any);
        back.setEnabled(any && index > 0);
        next.setEnabled(any && index < events.size() - 1);
        timeline.setEnabled(any);
    }

    private static JPanel dark(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BG);
        return panel;
    }

    private static JButton control(String text) {
        JButton b = Dashboard.button(text, new Color(30, 56, 82));
        b.setBorder(new EmptyBorder(6, 13, 6, 13));
        return b;
    }

    @Override
    public void removeNotify() {
        pause();
        super.removeNotify();
    }
}
