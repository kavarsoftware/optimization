# Server Placement Lab: model, method and evidence

Version `srs-study-2.0` extends the existing Java 17 project. It preserves the supplied CSV values, class split, Swing resource cards, animated replay, before/after pulses and cost/delay view. It adds the requested weighted default, Exact/Target methods, full experiment records, fair-budget protocols and study charts. Earlier additive outputs are labeled as historical.

## Model and units

For service `i`, `x[i]` is a server index in `{0,1,2,3}`. Twelve independent host choices produce `4^12 = 16,777,216` complete assignments before constraints.

All main methods minimize `D(x) = sum(serviceLatency[i] * serverLatency[x[i]]) / sum(serviceLatency[i])`.

The latest specification explicitly requests this formula. Service latency values are unchanged and act as normalized weights. The numerator has ms²; dividing by ms leaves ms. This is a weighted host-latency score, not a physical additive processing-time prediction. The previous additive formula remains optional and separate. CPU cost uses illustrative units per observation period; the compromise objective normalizes delay/cost before combining them.

Feasibility requires a complete valid assignment, CPU cores / RAM GB / storage GB within every host's capacities, and at least two services on each host. Equality at capacity is allowed, with a small tolerance for floating-point sums. Infeasible candidates are rejected and never selected for their raw latency. Two services per host is a distribution rule, not a reliability certificate.

`data/servers.csv` and `data/services.csv` are unchanged and loaded directly. No real price tariffs, energy measurements, dependency graphs or failure probabilities are invented.

## Shared evaluator and architecture

`ProblemData` holds inputs; `Candidate` wraps the `int[]` decisions; `Evaluation` holds quality/feasibility/usage/reasons. All methods use `Evaluator.measure()` for resource totals, constraints and the selected formula.

Exact reuses small `Work` arrays instead of creating millions of diagnostic lists. `evaluate()` calls the same routine and creates an explanatory immutable snapshot. Exact snapshots only an improving feasible incumbent. Both paths are tested against each other, so allocation savings do not introduce different rules.

`SearchMethod`, `SearchConfig` and `SearchResult` identify algorithms/settings/outcomes. `ExperimentRunner` runs protocols and statistics; `CsvFiles` preserves inputs and records. The existing replay classes present actual sampled decisions, while `StudyCharts` draws the requested evidence. `CODE_WALKTHROUGH.md` gives the reading order. The implementation uses ordinary loops; small lambdas remain for Swing callbacks.

## Four main methods

**Exact Search:** increment the assignment like a base-four odometer and evaluate every complete array once. A completed scan proves its minimum for this finite model and counts the feasible subset. Cancellation cannot provide a proof. Exact has no seed and ignores heuristic caps/patience. The supplied integer instance has minimum weighted numerator 1050, divided by 63.

**Random Search:** exactly 100,000 attempts; each index is chosen uniformly using the seeded generator. Repeats and infeasible candidates consume attempts. The best feasible strict improvement is retained.

**Target Search:** uniform samples until the first feasible score <=18 ms, or 500,000 attempts. Reaching the threshold does not imply optimality. It stops immediately on success. Patience does not stop Random or Target; cancellation is reported explicitly.

**Smart Restart Search (SRS):** our name for the existing GRASP-style method:

1. Rank services by host eligibility and resource pressure, with seeded random variation, placing restricted services early.
2. Build a start using only hosts with sufficient remaining capacity. Reserve enough unassigned services to cover every host's count deficit.
3. Form a restricted candidate list with contribution at most `minimum + greediness * range`, then randomly select an eligible host. Every fifth construction allows all eligible hosts to increase variety.
4. Abandon a blocked partial construction and restart; count it separately because it is not a complete candidate evaluation.
5. Evaluate the complete start with the shared evaluator.
6. Test moves of one service and swaps of two. Accept the best strict feasible improvement and inspect the new neighborhood.
7. Restart when no improving move or swap remains.
8. Compare the global best at every complete 10,000-evaluation batch. Improvement resets stagnation; a stagnant batch increments it. Stop at patience (default one), the evaluation cap, cancellation or the construction safeguard.

A neighborhood has at most 36 moves and 66 swaps. A local optimum refers to those neighbors, not all assignments. Restarts broaden exploration but give no general global guarantee. Batch/patience are adjustable engineering choices, not theorems. Patience 0 disables batch stopping. Every repeated and infeasible complete evaluation counts; abandoned partial constructions are recorded separately. Construction-only remains an extra ablation outside the required four-method study.

## Experiment design

Seeds 2026–2045 pair Random, Target and SRS. Exact runs once; repeating it with twenty seed labels would not provide independent evidence. Every method uses the same data, objective and constraints.

- **Main:** Exact full scan; Random 100,000; Target <=18 ms / 500,000; SRS cap 1,000,000, batch 10,000, patience 1.
- **Equal budget:** Random and SRS each use 100,000 evaluations; SRS patience is disabled.
- **SRS stop budget:** for each seed, Random receives the actual count used by stopped main SRS. Compare against that paired SRS result.

The main stochastic execution order rotates by seed to reduce a fixed JVM warmup advantage. Runtime includes construction/bookkeeping but excludes export/playback. Equal evaluations do not guarantee equal time. Measurements are descriptive, machine-specific evidence, not formal confidence intervals or a universal ranking.

Each of the 121 study records includes instance/input snapshot, version, method, objective/unit, parameters, stop condition, seed or deterministic N/A, feasibility, best score, evaluation/distinct counts, coverage, runtime, Java version and OS/architecture. The current single-run metadata appears in **Run record**; every study run has a separate CSV and input snapshot. Stopped runs preserve partial evidence too.

Best/mean/median/worst, sample SD, target success, optimum hit rate, mean work, distinct coverage and runtime are reported. Failed runs remain in the success denominator; score statistics use feasible returned results. Exact has one run, so its sample SD is N/A. See `RESULTS.md` and the saved CSVs for all measured values.

## Measured conclusion

Exact counted **18,281 feasible placements**, 0.108963% of the space, and proved a minimum of **16.666667 ms**. SRS matched it in all twenty main runs using **20,000 evaluations each**. Random averaged **18.452381 ms**; Target averaged **17.630952 ms**. Success at <=18 ms was 30%, 80% and 100% for Random, Target and SRS. Optimum hit rates were 0%, 5% and 100% respectively.

SRS's returned scores can be certified by equality to the completed Exact reference. Its algorithm remains approximate. Exact is practical for this small input; we do not claim brute force is impossible here. The growth chart explains why reducing work becomes useful as services increase.

An independent Python integer recursive enumeration with capacity/count pruning confirmed 18,281 feasible assignments and numerator 1050/63. Python was a development check, not a delivered runtime dependency. The optimistic lower bound is 5 ms, ignoring capacities/counts; it is not the Exact optimum or a certificate by itself.

## Visuals and exports

The existing replay records a bounded sample of real trials, acceptances, restarts and checkpoints, then animates them after the full-speed calculation. Counters include unshown evaluations. Play/Pause/Step/timeline affect playback only. The before/current display reports absolute/percentage changes and pulses green for lower, amber for higher and red for infeasible. Missing/zero baselines have no invented percentage. Previous-run mode requires compatible inputs/rules/objective.

Resource cards label CPU cores, RAM/storage GB and placed services. The assignment table lists resources, service/host latency and the selected-objective contribution. The proof badge requires a completed matching Exact reference and an equal score; replay updates the badge for its current recorded incumbent.

Study visuals are:

- Quality/time/confidence with Exact gap, runtime and evaluations.
- Best/mean/median/worst/SD, target and optimum rates, and seed dots.
- Four-method convergence on a log evaluation axis, with SRS highlighted and the optimistic bound dashed. This is explicitly a first-seed representative trace; distributions cover all seeds. Random and Target overlap while their seeded sample sequences match.
- Evaluation bars and mean-score bars with min/max ranges.
- Nonstacked total/feasible/distinct-visited counts with labeled log transforms; these sets overlap and are not additive.
- Log-scale `4^n`, n=1..12, highlighting n=12.

Studies export six PNG charts and corresponding CSV evidence. Single runs export placement/convergence PNGs plus assignment/usage/trace/metadata/input CSVs. Sweeps export each priority's run record, nondominance CSV and PNG. Everything uses native Java2D and CSV, with no external Java library.

## Illustrative trade-offs

The optional sweep samples alpha=0..1 in steps of 0.1 and minimizes `alpha*weightedDelay/delayScale + (1-alpha)*cost/costScale`. Normalization prevents adding incompatible raw units. Prices are illustrative CPU cost units per observation period.

A sampled solution is dominated if another is no worse in delay and cost and strictly better in at least one. Green means nondominated within the sample. Approximate weighted sums need not reveal every nondominated discrete solution; no complete Pareto-frontier claim is made. Single-objective ties need not optimize secondary cost.

## Three hand-checked candidates

Two hosts have delay 5/20 ms and CPU prices 0.2/0.1, with enough capacity. Services have CPU `[2,4,1]` cores and latency `[1,2,3]` ms. Each host needs at least one service in this small teaching case.

| Candidate | Weighted latency | Result |
| --- | --- | --- |
| A=[S1,S2,S1] | `(1*5 + 2*20 + 3*5)/6 = 10 ms` | Feasible |
| B=[S2,S1,S2] | `(1*20 + 2*5 + 3*20)/6 = 15 ms` | Feasible, worse than A |
| C=[S1,S1,S1] | Raw 5 ms | Rejected: S2 empty |

All eight arrays have a hand-known optimum of 7.5 ms: put only the smallest weight on S2. Exact confirms it. Additive A=12/B=17 ms is tested separately and never pooled with weighted results.

Self-tests also cover invalid encodings, each resource rule, equality at capacity, candidate immutability, seeds, budgets, target success/failure, batch stopping, cancellation, bounds, CSV round trips, fast/detailed evaluation agreement, chart exports, workers, study tables, proof references and truthful replay/pulses.

**Verification** asks whether Java implements the equations. **Validation** asks whether the equations/inputs represent the real need. Queueing, topology, request frequency, replicas and reliability need richer measurements and stakeholder review. Correct code and an optimum do not validate omitted physical assumptions.

## Lecture features and concept checks

`LECTURE_MAP.md` maps all 22 Week 0 slides and 29 Week 1 slides. The in-app guide links its workflow (W1 19), experiment record (23), deterministic/stochastic behavior (13), quality/time/confidence (16), growth (15), evaluator lab (20–21), trade-offs (W0 14 / W1 12), and common mistakes (24).

**Week 0 slide 19 laptop activity:** illustrative candidates A=8 GB RAM/256 GB storage/600 price units, B=16/512/800, C=32/1024/1100. Choose one laptop, minimize price, require >=16 GB RAM and >=512 GB storage, budget <=1000. A fails specs, C exceeds budget, B is feasible. Specs/prices are data; the choice is the decision. These are classroom numbers, not purchase advice.

**Week 1 slide 25 timetable:** decide course-room-time assignments; for example minimize student idle time. Constraints prevent room/instructor clashes and respect capacity/availability. Courses, enrollment, rooms, instructors and slots are known data. Fixed slots make the decision space discrete.

| Week 1 slide 24 mistake | How the project avoids it |
| --- | --- |
| Vague objective | Explicit formula, minimize direction and unit |
| Hidden constraints | Visible capacities/count rule; one evaluator |
| Wrong units | Cores/GB/ms labels; normalized compromise; labeled cost |
| Model/code mismatch | Hand checks and shared fast/detailed logic |
| One lucky run | Twenty paired seeds and distributions |
| No baseline | Random, Exact and fair-budget protocols |

The five-step workflow is understand, formulate, choose a method, implement and verify, then experiment and explain. Activities and future topics in the decks are teaching context; they do not override the student's project request. Genetic/annealing/swarm/continuous methods are discussed for suitability, not falsely claimed as implementations.
