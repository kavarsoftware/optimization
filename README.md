# Service Placement

A Java 17 desktop application for `requirements.pdf`: 12 services, 4 servers, one fixed objective J.

## Run

```sh
./build.sh
./run.sh
./run.sh --self-test
```

Windows: `build.bat`, then `run.bat`. No dependencies, API keys or cloud accounts.
In IntelliJ, select JDK 17+, mark `src` as Sources Root and run `optimizer.Main`.

## The algorithm in five sentences

1. Construct a placement, choosing randomly among promising hosts with enough resources and a passing network SLA.
2. Try moving one service or swapping two services.
3. Accept a feasible change that lowers J; keep the best feasible result seen anywhere.
4. When no neighbor improves, construct a new start.
5. Stop at the evaluation cap, after the configured number of stagnant batches, or when Stop is pressed.

This is restart local search with randomized greedy construction (a GRASP-style method). Greediness controls how broadly construction selects hosts; batch and patience control early stopping. One iteration is one full placement evaluation. A failed partial construction is discarded and separately counted. Search failure does not prove infeasibility.

Random is the baseline. Exact checks every one of the 4^12 assignments and supplies an optional proof reference. Neither passes its result into local search.

## What to demonstrate

Configuration -> Reference check -> Start/Pause/Step/Resume -> Performance -> Network/SLAs -> Compute timeline.
The Experiments tab runs 10 paired seeds, the same evaluation budget for local search and Random, budget sensitivity (20/50/100/200), and network sensitivity (RTT +25%, bandwidth -30%). Patience is disabled in experiments for fair budgets. Seed and budget are taken from the controls; Exact always enumerates the complete space.

```sh
./run.sh --study 10 --out results/assessment-study
./run.sh --cli --method LOCAL_SEARCH --seed 2026 --budget 20000 --out results/local
./run.sh --cli --method RANDOM --budget 20000 --out results/random
./run.sh --cli --method EXACT --out results/exact
./run.sh --help
```

Runs export settings, inputs, assignment, network SLA margins, timeline and charts. The current seeded evidence is in `results/assessment-study/`. Runtime varies by machine and excludes deliberate pause/display waits. UI events are samples of actual evaluations; every improvement and periodic checkpoints are retained. Step permits exactly one evaluation.

## Source reading order

- `ProblemData.java`: benchmark inputs and validation.
- `Candidate.java`: 12 server indices.
- `Evaluator.java`: all hard constraints and the professor's formulas.
- `Optimizer.java`: construction, moves/swaps, restarts, Random and Exact.
- `SearchControl.java`: pause, resume, step and stop.
- `AssessmentDashboard.java`: UI and background worker.
- `AssessmentTest.java` / `SelfTest.java`: T1-T12 and regression checks.

The remaining files draw charts or save CSV evidence. They are support code, not additional optimization algorithms.

Read `docs/REPORT.md` for the report and `docs/DEMO.md` for a four-minute explanation.
The previous code, data and documents are preserved in `archive/previous-project.zip` and are not built or used by this application. Old numerical reports describe different protocols and should not be submitted as current evidence.

AI assistance: the previous project and this simplification used coding assistants, including OpenAI Codex. The student must understand and explain the source and disclose assistance according to institutional rules. Java Swing/Java2D and standard-library CSV output are used; no external runtime libraries.
