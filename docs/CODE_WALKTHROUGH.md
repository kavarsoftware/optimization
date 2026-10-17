# Reading the source as a student

The existing class split and animation are preserved. The search and experiment additions use Java 17, ordinary loops, arrays, lists and a seeded `Random`; there are no external runtime libraries. Swing event listeners use small lambdas where callbacks are required. Java records hold named values without repetitive getters.

Read these in order:

1. **ProblemData**: server/service records, unit validation, CSV loading. These are known inputs, not decisions.
2. **Candidate**: an `int[]` assignment. Defensive copying prevents a later trial from changing the saved best. `code()` encodes the array in base four for distinct counting.
3. **Evaluation**: feasibility, score, resource totals and explanatory violations.
4. **Evaluator**: `measure()` is the single source of resource rules and formulas. `evaluate()` adds an explanatory immutable result. Exact uses reusable `Work` arrays so millions of rejected assignments do not allocate millions of detailed lists; it calls the same `measure()` method. `contribution()` ranks choices during construction, while final acceptance uses the complete evaluator.
5. **SearchMethod / SearchConfig / SearchResult**: names, parameters, and recorded outcomes. `completeEnumeration()` checks whether Exact visited the whole space; `provenOptimal()` also requires a feasible answer.
6. **Optimizer**: `run()` selects Random/Target or construction-based search. `exact()` increments a base-four array like an odometer. `construct()` orders restricted services, filters hosts by remaining capacity, reserves enough unassigned services to satisfy count deficits, and chooses randomly from good eligible hosts. `descend()` tests moves/swaps and accepts the best strict improvement. `Context.check()` counts every complete evaluation, tracks distinct codes and applies stopping rules. Partial construction failures are counted separately.
7. **ExperimentRunner**: runs Exact once, rotates stochastic execution order, pairs seeds, separates main/equal-budget/matched-stop protocols, and calculates statistics. Success means meeting the threshold; optimum hits compare with Exact. Failed runs stay in success-rate denominators.
8. **CsvFiles**: quoted UTF-8 CSV parsing/writing and full reproducibility metadata. Each study run gets its own record, input snapshot, assignment and trace.
9. **SelfTest**: hand calculations and behavioral checks. Follow the three-candidate example first.

The remaining classes present those results:

- **Dashboard** owns the controls. SwingWorkers perform search and file export away from the event thread; only their callbacks change the visible components.
- **StudyCharts** draws the six study charts with Java2D and exports PNG/CSV evidence. Convergence uses the first seed as a clearly labeled representative run; dots and summaries cover all seeds.
- **Visuals** draws resource cards, one-run convergence and illustrative cost/delay trade-offs.
- **SearchEvent**, **ReplayPanel**, **ReplayCanvas** preserve a bounded sample of actual decisions, then replay movement at presentation speed. Animation does not change the optimizer's random choices or evaluation counts.
- **ResultComparison**, **ComparisonPulse** calculate safe before/after differences and draw pulses/sparklines. Changed objectives/inputs cannot support an improvement claim.
- **LectureGuide** contains the slide-linked teaching text. **DisplaySettings** handles scaling. **Main** is the GUI/CLI entry point.

`Context` inside Optimizer and `Work` inside Evaluator keep internal bookkeeping close to the code that uses it. Construction-only remains an extra method for comparing the effect of local improvement; the required study reports the four requested methods.

For a class demonstration, trace one feasible candidate through `Evaluator`, one base-four increment in `exact()`, one move in `descend()`, and one batch check in `Context.check()`. Then explain why the same evaluator makes the comparisons meaningful.

## Assessment-mode additions

The same ProblemData/Candidate/Evaluation/Evaluator/SearchMethod/ExperimentRunner split is retained. ProblemData now carries the assessment resource, work, power, price and network fields as well as the preserved old constructors. Evaluator.measure handles the fixed assessment J and hard GPU/slot/SLA rules. The original weighted data require their separate evaluator modes.

- **AssessmentDashboard:** assessment configuration, live worker controls, six metrics, network table, current/best views and study tabs.
- **SearchControl:** gates real evaluations for pause/step/resume; measures deliberate waiting separately from active search time.
- **AssessmentPlots:** six performance panels and ascending-ID compute timeline, with CSV/PNG export.
- **AssessmentExperiments:** fixed-budget and network sensitivity protocols, raw per-run metrics and chart exports.
- **AssessmentTest:** the named PDF tests T1–T12, reference arithmetic, evaluator agreement and live controls.
- **AssessmentGuide:** the PDF model and its connections to Week 0/1.

Default Main opens this assessment view. Original CSV project opens the earlier Dashboard, also available with --original. No external Java runtime libraries are used. The mathematical reference and data models are small; the extra classes retain readable UI/experiment responsibilities instead of hiding them in the evaluator.

## Ten-algorithm extension, version 4.0

- **AdvancedSearch:** seven extra implementations, each using `Optimizer.Context.check` for the shared evaluator, budget, uniqueness, incumbent, cancellation and trace. `State` stores an already evaluated candidate; comparing stored scores never consumes hidden evaluations.
- **AlgorithmComparison:** ten approximate methods, Exact once outside the comparison, identical budgets with early stopping disabled, paired seed identifiers, rotating execution order, warmup, coverage/mean ranking and raw exports. It never passes the exact mapping or score into an algorithm.
- **PortfolioCharts / PortfolioPanel:** ranking/range, every-seed spread, speed, mean incumbent curves and paired selection with a pulse over real recorded data. The experiment tab hides unrelated live metrics to keep the charts readable.
- **PortfolioTest:** seeded reproducibility, budget-one initialization, all hard rules, monotone incumbent traces, cancellation, exports and the real SwingWorker/controls.

`--compare-ten 20 --budget 20000` invokes the same coordinator used by the UI. The existing four-method study and original dataset are retained.
