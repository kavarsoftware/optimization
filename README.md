# Service Placement Lab

A Java project for Dr. Godar's Optimization Methods course. Place 12 services on 4 servers while respecting CPU, RAM and storage capacities. A GRASP metaheuristic combines randomized greedy construction, move/swap local descent, and repeated restarts.

The service values are **latencies in milliseconds**, as confirmed by the student. The default objective minimizes average service latency plus server latency. The optional legacy mode reproduces the weight interpretation in the original code.

## Open the application

Requires **Java 17 or newer**, with a graphical desktop. The compiled JAR is included. No Maven, Gradle, external libraries, internet connection, or database is required.

- Windows: double-click `run.bat`.
- Linux/macOS: run `./run.sh` from this folder.
- Any platform: `java -Xmx512m -jar ServerPlacementLab.jar`.

If Java is missing, install a Java 17+ JDK/JRE first or point `JAVA_HOME` at an existing installation. A JDK is required to rebuild the source. The Java runtime itself is not bundled in this portable project.

The dashboard opens maximized and automatically uses 200% scaling on high-DPI Linux displays. Search history and service assignments have separate result tabs so both stay readable. To choose the display scale explicitly, use `./run.sh --ui-scale 2` for 200% or `./run.sh --ui-scale 1` for 100%. Windows/macOS retain their native Java scaling unless you supply an override.

## What to demonstrate

1. Open **Model and data** to explain known inputs and units. Cells are editable.
2. Keep the default service latency objective, seed 2026, and minimum two services per server.
3. Press **Run optimizer**. Inspect placement, capacity bars, convergence, feasibility, coverage and stopping evidence.
4. Open **Experiments**, then **Compare methods**. All methods share an evaluator and full evaluation budgets. Try ten paired seeds at 50,000 evaluations per method.
5. Run the optional **cost / delay sweep** to show stakeholder priorities. Its prices are explicitly illustrative.
6. Use **Export run** or **Export experiments** to preserve reproducible evidence.

The default reference run found **27.75 ms**, with all constraints satisfied, after 40,000 evaluation calls. It visited 28,696 distinct candidates, about **0.171%** of the 16,777,216 possible assignments. The first occurrence of this score was evaluation 114. This is a best-found result without a global optimality certificate.

## Build and verify

From this folder, run:

```sh
./build.sh
./run.sh --self-test
```

On Windows, use `build.bat`, then `run.bat --self-test`.

In IntelliJ IDEA, Eclipse, or NetBeans, open this folder as a Java project, mark `src` as the source root, choose JDK 17+, and run `optimizer.Main`. `ProblemData`, `Candidate`, `Evaluation`, `Evaluator`, `SearchMethod`, and `ExperimentRunner` correspond to the lecture's architecture.

## Command-line use

```sh
java -Xmx512m -jar ServerPlacementLab.jar --cli --out results/my-run
java -Xmx512m -jar ServerPlacementLab.jar --cli --method RANDOM --seed 2026 --budget 50000 --patience 0 --out results/random-run
java -Xmx512m -jar ServerPlacementLab.jar --compare 20 --seed 2026 --budget 50000 --out results/my-comparison
java -Xmx512m -jar ServerPlacementLab.jar --cli --input data --objective BALANCED --alpha 0.75 --out results/compromise
java -Xmx512m -jar ServerPlacementLab.jar --help
```

`--budget` counts every complete candidate evaluation, including infeasible and repeated candidates. It cannot exceed 1,000,000. `--patience 0` disables early stopping. Ordinary searches check stagnation at complete batches of 10,000 evaluations and stop after three stagnant batches. Comparisons always disable patience.

Export folders contain the input CSV snapshot, assignment, resource usage, convergence and run metadata. Do not compare scores from different objective modes as though they measure the same quantity. CPU cost uses illustrative units per observation period.

## Project contents

- `ServerPlacementLab.jar`: compiled application.
- `src/optimizer/`: commented Java source, including automated checks.
- `data/`: supplied inputs and separately labeled example cost rates.
- `docs/REPORT.md`: model, algorithm, analysis, measured results, limitations and original-code review.
- `docs/LECTURE_MAP.md`: all 22 Week 0 slides and all 29 Week 1 slides mapped to this project.
- `docs/PRESENTATION.md`: a short speaking script and teacher questions with answers.
- `docs/Presentation.html`: browser presentation for class, with keyboard navigation.
- `docs/Class_Presentation.pptx`: editable classroom slides with speaker notes.
- `results/default-run/`: a measured reference run.
- `results/comparison-latency/`: 20 paired seeds × 3 methods × 50,000 evaluations.
- `results/comparison-legacy-weighted/`: a separate experiment using the old interpretation, when present.
- `results/tradeoffs/`: a saved 11-priority cost/delay sweep with illustrative prices.

Both lecture decks are source material. Their classroom activities and future-week instructions describe their teaching context. This project implements the student's requested optimizer and explains their relationship to that context.
