# Five-minute assessment demonstration

This script uses the **Assessment benchmark**, not the original weighted score. Keep the app open at 200% on a high-DPI display. `View ms` can be 70–150 so real intermediate states are easy to discuss. The report and CSVs contain the exact measurements.

## 0:00–0:45 — Model and fixed benchmark

“We place exactly twelve independent services on four heterogeneous servers, A through D. Our decision is a twelve-entry vector. Every service has exactly one host. CPU, RAM, disk, GPU slots, maximum service count and each service's network SLA are hard constraints.”

Show **Configuration**. “These are the fixed assessment defaults and can be edited/reset. Assessment mode does not require two services on every host; the separate original project keeps that rule.”

“J combines normalized makespan, busy-time cost, energy and mean network latency with the specified weights 0.35, 0.20, 0.15 and 0.30. Lower J is better.”

## 0:45–1:30 — Verify the numeric reference

Click **Reference check**. “The supplied mapping is feasible but not claimed optimal. We reproduce J=0.856845, M=28.3333 seconds, C=0.449583 dollars, E=3.927416 Wh, L=53.347863 ms and Lmax=76.666667 ms. All twelve SLAs pass.”

Show **Network / SLAs**. “Latency is RTT plus payload transfer time: MB to Mb multiplies by eight, then seconds to ms multiplies by 1000. Compute/wait time is excluded from this communication SLA.”

Show **Compute timeline**. “Services process sequentially in ascending ID order. Work/Speed gives seconds. Algorithm run time, makespan and network milliseconds are three different measures.”

## 1:30–2:35 — Explain and show SRS

“Our main method is Smart Restart Search, a GRASP-style metaheuristic. It constructs starts using resource, GPU and SLA eligibility, tests moves and swaps, accepts strict feasible improvement, and restarts at local optima. Greediness, batch size, patience and the cap are configurable.”

Click **Start**, then **Pause**, **Step**, **Resume**. “These controls affect real evaluations. One iteration means one complete candidate evaluation. We record the actual candidate and incumbent; invalid trials are rejected and may appear red, but never replace the best feasible solution.”

Show **Current trial** and **Best feasible so far**. Point to the pulse and CPU/GPU/slot bars. “One batch is 10,000 evaluations. With patience one, a completed batch without global improvement stops the search. A batch rule is a practical choice, not an optimality proof.”

## 2:35–3:45 — Evidence and confidence

Show the saved study's summary/charts or run the study in advance. “We used seeds 2026 through 2045. Exact checked every one of 16,777,216 assignments once and counted 92,643 feasible placements. It proved J=0.71515436 for this model.”

“SRS averaged 0.71605682 at 20,000 evaluations and matched Exact in five of twenty runs. At the same 100,000-evaluation budget as Random, SRS matched Exact in eighteen of twenty runs. Random's main mean was 0.72407629. Target stops as soon as J is at most 0.8, so its fast threshold success does not mean it finds the minimum.”

“Runtime is measured on this machine, excludes deliberate display waits and exports, and can vary. The record preserves all inputs, settings, seeds, units and platform information.”

## 3:45–4:25 — Budgets, sensitivity and tests

“The 20/50/100/200-evaluation experiment shows how more search improves mean J. Increasing RTT by 25% or reducing bandwidth by 30%, using the same seeds, raises communication latency and changes the placement trade-offs. Every reported solution still satisfies all SLAs.”

Point to T1–T12 evidence. “We deliberately test S06 on non-GPU A, CPU overload at 21/16 vCPU, insufficient total GPUs, exact capacity equality, invalid speed/network inputs, and S03 on C at 70 ms against a 60 ms SLA. A search failure alone is not an infeasibility proof.”

## 4:25–5:00 — Lecture connection and conclusion

“We followed understand, formulate, choose, implement and verify, then experiment and explain. Verification asks whether the code matches the model. Validation asks whether the model captures reality. This is the assessment's simplified simulation, with no congestion or real cloud deployment.”

“SRS returns best found. Only a completed Exact procedure can establish global optimality for a returned score on this instance. The original weighted project remains available separately; its 16.666667 ms score is not comparable with J. I used and disclose Codex assistance, and can trace the evaluator and search code.”

## Fifteen likely teacher questions

1. **What is a seed?** The pseudorandom generator's initial state. Same inputs, settings, version and seed reproduce scores/assignments/counts; runtime varies.
2. **Why not only use Exact?** Exact is practical here and supplies a certificate. Work grows as 4^n; SRS demonstrates good results with fewer complete evaluations.
3. **Why 10,000 per batch?** It is an adjustable observation interval, not a theorem. It balances search effort with the risk of stopping before another useful restart.
4. **What is patience?** The number of complete consecutive stagnant batches; default one. Zero disables this stop rule.
5. **What is a local optimum?** A placement with no improving move or swap in the inspected neighborhood. A distant placement may still improve J.
6. **Why Random as a comparator?** It is transparent and shares the evaluator. Equal-budget and SRS-stop-budget comparisons isolate the value of structured search.
7. **What is feasible?** Valid unique placement plus all CPU/RAM/disk/GPU/slot/SLA limits. Low J never compensates for a violated SLA.
8. **Why normalize J?** M, C, E and L use incompatible units. Dividing by fixed reference scales makes dimensionless weighted terms; those scales are not capacity limits.
9. **How is network latency calculated?** RTT ms + 1000 × (8 × payload MB / bandwidth Mb/s). It excludes compute and waiting time under the mandatory model.
10. **What are the three different times?** Active algorithm time in ms, simulated compute makespan in seconds, per-service network latency in ms. Pause/display waits are tracked separately.
11. **Why does the timeline order matter?** The brief specifies ascending service ID. Each rectangle lasts Work/Speed seconds; their sum equals server busy time, and the largest total is M.
12. **Can SRS claim a global optimum?** Its own stop rule cannot. A result matching the completed Exact score is certified for that same model; otherwise report best found and its gap.
13. **Why might energy, cost or latency rise while J improves?** J trades four normalized terms. Only the combined best J is monotone; its component measures can move in opposite directions.
14. **How do you prove infeasibility?** Some necessary conditions suffice, such as total GPU demand 3 > available 1. General search failure is inconclusive; complete enumeration can prove no feasible vector exists.
15. **Verification versus validation, and who wrote the code?** Hand/reference checks, T1–T12 and independent enumeration verify implementation. Physical validation needs realistic measurements. The student supplied the project/data; Codex assisted implementation and documentation, which must be reviewed and credited under institutional rules.

## Optional one-minute comparison demonstration

Open **Experiments → Compare 10 algorithms**. Use first seed 2026, 20 seeds, budget 20,000 per run. Click **Compare 10 algorithms**. This collects 200 runs with the same input, hard constraints and fixed J. Exact provides a separate reference. Both target stopping and batch patience are disabled for this protocol.

“Tabu matched the exact optimum in 20 of 20 runs; VNS in 13, annealing in 11, and SRS in 5. The ranking uses mean feasible score and feasible-run coverage, not the luckiest best. All 200 outputs satisfy the hard rules. I would choose Tabu at this budget on this benchmark; other inputs and parameter choices may change the winner.”

Show **Seed spread**, **Speed**, then **Paired vs SRS**. Select Genetic and seed 2032: Genetic J=0.716529359580, SRS J=0.716357637206; delta is approximately +0.000172, so SRS is better on that seed. The pulse marks an actual selected record. Explain that the x-axis lists independent seeds; it is not a single evolving run.

If asked why ten methods: they illustrate different search strategies and escaping local minima. They extend the lecture's general search framework; not every added algorithm was necessarily taught in Weeks 0/1. These are readable teaching variants with all parameters and background references disclosed in `TEN_ALGORITHMS.md`.
