# Lecture activities and evidence

## Week 0 slide 19: choose a laptop

This is a fictional classroom dataset, not shopping advice. Candidates A, B and C have (price in USD, RAM in GB, battery life in hours): A (600, 16, 8), B (800, 32, 10), C (500, 8, 6). The decision is which single laptop to buy. Minimize price in USD, subject to RAM at least 16 GB and battery life at least 8 hours. Prices and specifications are problem data. Evaluate each complete candidate by checking both constraints, then compare prices only among feasible candidates. A and B are feasible; A is the cheapest feasible candidate in this dataset. C is cheaper but infeasible.

## Week 1 slide 25: campus timetable

Decisions assign each teaching session a room and time slot. One objective is to minimize the total number of idle student periods per week. Constraints include no lecturer or student group attending overlapping sessions, and room capacity at least the class enrollment. Data include sessions, enrollments, lecturer availability, room capacities, equipment and available time slots. The search space is discrete because room and time-slot choices come from finite sets.

## Week 1 slide 24: common mistakes → project safeguards

| Common mistake | How this project avoids it |
| --- | --- |
| Vague objective | Explicit average delay in ms; other objectives have separate labels and records. |
| Hidden constraint | One shared evaluator checks assignment validity, minimum counts and CPU, RAM and storage capacities. |
| Wrong units | CPU in cores, RAM/storage in GB, delay/runtime in ms; illustrative prices in cost units per core per observation period. Balanced scores are dimensionless. |
| Model–code mismatch | Hand-calculated examples, capacity boundary tests and a tiny Exact enumeration verify the evaluator. |
| One lucky run | Twenty paired seeds per stochastic protocol; summarize best, mean, worst and feasible-run success rate. |
| No baseline | Random at the common evaluation cap and at each hybrid stopping budget; Exact provides a proof when enumeration completes. |

## Reading the experiment tables

`results/lecture-comparison/summary.csv` is the measured final summary. The dashboard's **Quality / Time / Confidence** table displays the same columns. Scores within a comparison use one fixed instance, objective, alpha and constraint setting. Never combine scores across objectives or across alpha values in the sweep.

Success rate means the percentage of runs returning a feasible candidate, not the percentage proven optimal. `% of space` is the mean number of distinct complete assignments evaluated divided by the full assignment space. Evaluations include repeats and infeasible candidates. Runtime is measured wall-clock time in ms and depends on hardware and JVM warmup. Best, mean and worst summarize feasible results only; missing feasible results have no score.

Exact enumerates all complete assignments using the same evaluator, has no seed and is outside stochastic evaluation budgets. A completed enumeration proves its feasible optimum, or infeasibility if none exists. Cancellation never yields a proof. Gap is 100 × (best − Exact optimum) / |Exact optimum|. An unavailable optimum yields NA; a zero optimum gives 0% only for an equal score, otherwise NA because the percentage is undefined.

The equal-budget protocol gives HYBRID, CONSTRUCTIVE and RANDOM the same 20 seeds and evaluation cap with patience disabled. A second protocol runs HYBRID with its own three-stagnant-batch stopping rule (subject to the same cap), then runs RANDOM with the same seed and exactly the number of evaluations used by that hybrid run. This separates fixed-budget quality from performance at natural stopping.

Every optimizer invocation, including sweep and cancelled runs, appends a record to `results/run-records.csv`. Run exports include `experiment_record.csv`; comparison exports include every record in `trials.csv`, tagged by protocol. Records contain the instance, version, objective and score unit, all search parameters, stopping condition, seed (NA for Exact), score, feasibility, evaluations, distinct candidates, runtime, Java version and OS. Input snapshots accompany explicit exports.

The optimistic lower bound ignores capacity and minimum-count constraints and appears as a dashed line in the convergence chart. It is not the Exact optimum. The lecture guide shows the five-step workflow and the log-scale chart of 4^n for 1 through 12 services. The cost/delay chart marks nondominated sampled solutions; illustrative prices are not measured tariffs and the sampled frontier is not a global Pareto certificate.
