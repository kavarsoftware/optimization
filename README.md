# Server Placement Lab — Java 17

The app now opens in **Assessment benchmark** mode for `docs/Assessment_Latency.pdf`. The original weighted-latency project, unchanged original CSVs, animations and cost/delay sweep remain available through **Original CSV project** or `--original`. The two datasets and objectives are kept separate.

No external Java libraries, Maven, Gradle, network account or API key are required. The source, JAR and scripts are included.

**New: Experiments → Compare 10 algorithms.** This is a separate comparison on the same assessment model, with 20 paired seeds and 20,000 evaluations per algorithm per seed. It adds a quality ranking, every-seed spread, runtime bars, ten convergence curves, statistics, and a pulsing **Paired vs SRS** view. Pick an algorithm and seed to see its real score difference from SRS. The original four-method assessment study remains available.

## Run / build

```sh
java -jar ServerPlacementLab.jar
./build.sh
./run.sh --self-test
```

Linux/macOS: `./run.sh`. Windows: `run.bat` / `build.bat`. In IntelliJ: open this folder, select JDK 17+, mark `src` as Sources Root, run `optimizer.Main`. Linux high-DPI scaling is automatic; `--ui-scale 1` or `--ui-scale 2` overrides it. The provided scripts set a 512 MiB heap limit; ordinary `java -jar` also works.

## Assessment model

Twelve indivisible services choose one of A–D: **4^12 = 16,777,216** possible vectors. Hard rules cover CPU, RAM, disk, GPU slots, maximum service count and each service's network-latency SLA. There is **no minimum-two rule in assessment mode**; the PDF reference legitimately puts only S12 on B.

- Busy time `B = sum(Work GI / Speed GI/s)`; makespan `M = max B` in seconds.
- Active power `P = Idle + (Peak-Idle)*CPU utilization`; an unused server is powered off.
- Energy `E = sum(P*B/3600)` Wh; cost `C = sum(busy-time price*B)` USD.
- Network latency `RTT + 1000*8*payloadMB/bandwidthMbPerSecond` ms. Its mean is L, maximum Lmax. This excludes CPU processing/wait time.
- Minimize dimensionless **J = 0.35*M/30 + 0.20*C/0.50 + 0.15*E/5 + 0.30*L/70**. The weights/scales are fixed, not hard limits.

Assessment defaults are in `data/assessment/`. Original `data/servers.csv` and `data/services.csv` remain byte-for-byte unchanged. All assessment fields are editable/resettable in the app. Models are validated before search.

## What to show

1. **Reference check:** verifies the PDF's feasible mapping: J=0.856845, M=28.3333 s, C=$0.449583, E=3.927416 Wh, L=53.347863 ms, Lmax=76.666667 ms; all 12 SLAs pass. This mapping is not claimed optimal.
2. **Start / Pause / Resume / Step / Stop / Reset:** control actual evaluations. One iteration is one complete candidate evaluation; Step permits one. `View ms` only paces delivered real events. Deliberate pauses/display waits are excluded from active search time. Recorded replay has its own tab.
3. **Current trial / Best feasible:** linked real algorithm states, animated service links, exact CPU/RAM/disk/GPU/service-slot figures, per-service network labels and pulsing score changes. Rejected trials are marked explicitly; they never replace the feasible incumbent.
4. **Performance:** best J and its associated L/M/E/C/Lmax versus evaluation iteration. Only J must improve monotonically; its other measures can trade off.
5. **Network / SLAs:** RTT, bandwidth, payload, transfer time, latency, SLA margin and PASS/FAIL per service. **Compute timeline:** ascending service-ID order, Work/Speed durations, busy times and makespan.
6. **Experiments:** twenty paired seeds, all four main methods, Exact reference, full statistics, convergence/effort/range/dot/space/growth charts, fair budgets, 20/50/100/200-evaluation study, and +25% RTT / -30% bandwidth sensitivity. CSV/PNG evidence saves automatically.
7. **Compare 10 algorithms:** adjustable first seed, number of seeds and equal evaluation budget. Each complete invalid or repeated trial counts. Stop preserves partial results, which are labeled provisional. Exact runs once as a separate reporting reference; its answer is never passed to an approximate method.

## Recommended comparison

The ten algorithms are Random, randomized greedy construction, restart hill climbing, SRS/GRASP, simulated annealing, tabu search, genetic algorithm, iterated local search, variable neighborhood search and beam search. These are explicitly documented teaching variants. Target Search stays in the four-method protocol: with threshold stopping disabled it would duplicate Random.

On the default PDF data, **Tabu is the strongest observed choice at 20,000 evaluations**: it matched Exact's J=0.715154359580 in **20/20** runs. VNS matched it in 13/20, annealing in 11/20, and SRS in 5/20. All 200 runs returned feasible placements. This is evidence for this instance and these parameters; it does not establish a universal winner. Rank primarily by feasibility and mean score, then consider consistency and measured runtime.

See **[docs/TEN_ALGORITHMS.md](docs/TEN_ALGORITHMS.md)** for all ten results, parameters, limitations and primary references. Raw trials, per-run assignments/inputs and five PNG charts are in **[results/ten-algorithms/](results/ten-algorithms/)**. The six-page PDF report covers the earlier four-method protocol; the new Markdown report supplements it.

## Methods

| Method | Main protocol |
| --- | --- |
| **Smart Restart Search (SRS)** | Capacity/GPU/SLA-aware randomized greedy starts, best improving moves/swaps, restarts, batch stop |
| Exact | Enumerate every assignment once; completion can certify an optimum |
| Random | Exactly 100,000 uniform assignments |
| Target | Until feasible J <=0.8, or 500,000 random attempts |

SRS defaults: seed 2026, cap 20,000 evaluations, batch 10,000, patience 1, greediness 0.35. Batch/patience/greediness/cap are adjustable. Patience zero disables batch stopping. All invalid and repeated complete evaluations count. Failed partial constructions are recorded separately. SRS is a GRASP-style metaheuristic; Exact is also an explicitly accepted method in the brief.

## Reproduce

```sh
./run.sh --study 20 --out results/my-assessment-study
./run.sh --compare-ten 20 --seed 2026 --budget 20000 --out results/my-ten-algorithms
./run.sh --cli --method EXACT --out results/my-exact
./run.sh --cli --method HYBRID --seed 2026 --out results/my-srs
./run.sh --original
./run.sh --original --study 20 --out results/my-original-study
./run.sh --help
```

`--input data/assessment` loads assessment CSVs. `--original --input data` loads the original model. Saved assessment evidence is in `results/assessment-study/`, `assessment-default/`, and `assessment-reference/`; the original evidence stays in `results/weighted-study/` and `weighted-tradeoffs/`. Every run saves inputs, version, settings, stop reason, seed (Exact=N/A), feasibility, score/units, J/M/C/E/L/Lmax, SLA violations, counts, Java/OS and runtime. Study exports include six chart pairs, budget/network plots and all raw trials.

The assessment Exact minimum is **J=0.715154359580**, with **92,643 feasible placements**. SRS's main mean is **0.716056817799**; it matched Exact in 5/20 runs using 20,000 evaluations, and 18/20 at 100,000. See `docs/ASSESSMENT_RESULTS.md`. These are measured instance-specific results, not a global guarantee for SRS.

## Submission files

- `docs/Assessment_Report.pdf`: six-page assessment report with screenshots, tests and measured evidence.
- `docs/TEN_ALGORITHMS.md`: supplementary equal-budget comparison of ten algorithms and their fixed parameters.
- `docs/PRESENTATION.md`: five-minute live-demo script and 15 teacher questions.
- `docs/ASSESSMENT_CHECKLIST.md`: requirements and T1–T12 evidence.
- `docs/LECTURE_MAP.md`, `CODE_WALKTHROUGH.md`: lecture coverage and source reading order.
- `docs/ORIGINAL_PROJECT.md`, `ORIGINAL_REPORT.md`, `ORIGINAL_PRESENTATION.md`, `RESULTS.md`: preserved original-mode documentation.
- `docs/evidence/`: test logs and independent development checks.

The simulation does not model congestion, real cloud traffic, failures or replicas. Verification establishes the stated formulas, not physical deployment validity. This project was developed with **OpenAI Codex assistance** and reused the student's original Java idea/data and the previously developed app. No external Java runtime libraries are included. The student should review/explain the code and disclose assistance under the institution's rules. Windows/macOS, a real projector and an IntelliJ GUI session were not directly tested; Linux Java 17 compilation, native launch, automated tests and 200% rendering were checked.
